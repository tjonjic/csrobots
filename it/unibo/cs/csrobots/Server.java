/*
marvin -- a CSRobots client and server
Copyright (C) 2006 Carlo Cuoghi, Tomislav Jonjic

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package it.unibo.cs.csrobots;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import java.awt.Dimension;

import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Collections;

import java.util.regex.*;

public class Server extends BaseServer implements CommandHandler {

    // What we mean by program exit status
    private static final int EXIT_STATUS_SUCCESS  = 0;
    private static final int EXIT_STATUS_FAILURE  = 1;
    private static final int EXIT_STATUS_BAD_ARGS = 2;

    // Default server params
    private static final int DEFAULT_LISTEN_PORT = 7919;
    private static final String defaultListenAddr = "127.0.0.1";

    private int serverPort;
    private String serverAddress;
    private Field field;
    private GameConfig config;

    public Server(int port, String host, String boardPath)
        throws IOException, InvalidBoardException, FileNotFoundException
    {
        clients = new LinkedHashMap<Integer, Client>();
        serverPort = port;
        serverAddress = host;
        config = new GameConfig(boardPath);
        field = config.getField();
    }

    
    //// The CommandHandler interface

    public LinkedList<Update> handleCommand(MoveCommand cmd) {
        LinkedList<Update> updates = new LinkedList<Update>();

        Client client = clients.get(cmd.getPlayerId());
        if (client.getRobot().isIdle()) {
            client.getRobot().setIdle(false);
            return updates;
        }

        tryMove(client.getRobot(), cmd.getDirection(), updates);

        return updates;
    }

    /**
     * Try moving a robot, possibly by moving others recursively first (e.g.
     * push others if necessary).
     */
    private void tryMove(Robot robot, Direction dir, LinkedList<Update> result) {
        Position dest = robot.getPosition().move(dir);

        if (dest.getY() < 0 || field.getHeight() <= dest.getY())
            return;
        if (dest.getX() < 0 || field.getWidth() <= dest.getX())
            return;
        
        switch (field.getType(dest)) {
        case OPEN_SPACE:
        case BASE:
            if (field.getRobot(dest) != null) {
                Robot victim = field.getRobot(dest);
                Packet p = victim.dropLast();
                if (p != null)
                    field.addPacket(dest, p);
                
                tryMove(victim, dir, result);
                victim.setIdle(true);
            }

            if (field.getRobot(dest) == null) {
                field.setRobot(robot.getPosition(), null);
                field.setRobot(dest, robot);
                robot.setPosition(dest);
                result.add(new MoveUpdate(robot.getId(), dir));
            }
            break;

        case WATER:
            removeRobot(robot.getId());
            result.add(new MoveUpdate(robot.getId(), dir));
            result.add(new KillUpdate(robot.getId()));
            break;
            
        case WALL:
            break;

        default:
            assert false;
        }
    }

    public LinkedList<Update> handleCommand(PickCommand cmd) {
        LinkedList<Update> updates = null;
        Client client = clients.get(cmd.getPlayerId());
        if (client.getRobot().isIdle()) {
            client.getRobot().setIdle(false);
            return updates;
        }

        Robot robot = client.getRobot();
        Position position = robot.getPosition();

        // First, check if the little robot overdid it.
        LinkedList<Integer> packetList = cmd.getPacketIdList();
        int k = 0;
        for (int id : packetList) {
            if (field.containsPacket(position, id))
                k += field.getPacket(position, id).getWeight();
        }

        if ((k + client.getRobot().getCurrentLoad()) > client.getRobot().getStrength()) {
            removeRobot(client.getId());
            if (updates != null)
                updates.add(new KillUpdate(cmd.getPlayerId()));
            else {
                updates = new LinkedList<Update>();
                updates.add(new KillUpdate(cmd.getPlayerId()));
            }
        } else {
            for (int id : packetList) {
                if (field.containsPacket(position, id)) {
                    robot.pick(field.removePacket(position, id));
                    if (updates != null)
                        updates.add(new PickUpdate(cmd.getPlayerId(), id));
                    else {
                        updates = new LinkedList<Update>();
                        updates.add(new PickUpdate(cmd.getPlayerId(), id));
                    }
                }
            }
        }
        return updates;
    }

    public LinkedList<Update> handleCommand(DropCommand cmd) {
        LinkedList<Update> updates = new LinkedList<Update>();
        Client client = clients.get(cmd.getPlayerId());

        if (client.getRobot().isIdle()) {
            client.getRobot().setIdle(false);
            return updates;
        }

        Robot robot = client.getRobot();
        for (int packetId : cmd.getPacketIdList()) {
            Packet p;
            if ((p = robot.drop(packetId)) != null) {
                updates.add(new DropUpdate(cmd.getPlayerId(), packetId));
                if (p.getDestination().equals(robot.getPosition()))
                    client.addScore(p.getWeight());
                else {
                    field.addPacket(robot.getPosition(), p);
                }
            }
        }
        return updates;
    }
    
    protected void logMessage(LogMessageLevel level,
                              String messageFormat, Object ... args)
    {
        switch (level) {
        case ERROR:
            System.out.print("[ERROR]"); break;
        case WARNING:
            System.out.print("[WARNING]"); break;
        case INFO:
        case DEBUG:
        default:
            System.out.print("[INFO]"); break;
        }
        System.out.format(" " + messageFormat + "\n", args);
    }

    protected void handleFatalError() {
        logMessage(LogMessageLevel.INFO, "quitting due to irrecoverable error");
        System.exit(EXIT_STATUS_FAILURE);
    }

    protected LinkedList<Update> handleCommands(LinkedList<Command> commands) {
        // Sort the received commands relative to the bid and player age.
        Collections.sort(commands,
                         new Comparator<Command>() 
        {
            public int compare(Command a, Command b) {
                return b.getBid() - a.getBid();
            }
        });

        LinkedList<Update> updates = null;
        for (Command cmd : commands) {
            clients.get(cmd.getPlayerId()).getRobot().detractMoney(Math.abs(cmd.getBid()));
            if (clients.get(cmd.getPlayerId()).getRobot().getMoney() < 0 || cmd.getBid() == 0) {
                if (updates != null)
                    updates.add(new KillUpdate(cmd.getPlayerId()));
                else {
                    (updates = new LinkedList<Update>()).add(new KillUpdate(cmd.getPlayerId()));
                }

                removeRobot(cmd.getPlayerId());
            } else {
                logMessage(LogMessageLevel.INFO,
                           "executing command from player %d [%s]", cmd.getPlayerId(), cmd);

                // Invoke the right handler for the commnad
                LinkedList<Update> up = cmd.invokeHandler(this);

                if (up != null) {
                    if (updates != null)
                        updates.addAll(up);
                    else
                        updates = up;
                }
            }
        }

        return updates;
    }

    protected Field getField() {
        return field;
    }

    protected Packet[] getPacketListForPlayer(Client client) {
        Position playerPos = client.getRobot().getPosition();
        return field.getPackets(playerPos);
    }

    protected void handleTurnStart() {
        for (Client c : clients.values()) {
            c.getRobot().setIdle(false);
        }
    }

    public void start() {
        for (Robot r : config.getRobots())
            clients.put(r.getId(), new Client(r));

        try {
            acceptConnections(serverPort, serverAddress);
        } catch (StubException e) {
            logMessage(LogMessageLevel.ERROR, "could not start server [reason: %s]",
                       e.getMessage());
            System.exit(EXIT_STATUS_FAILURE);
        }

        run();
    }

    private void removeRobot(int id) {
        clients.get(id).setAlive(false);
        field.setRobot(clients.get(id).getRobot().getPosition(), null);
    }

    private static void printUsageAndQuit(int exitStatus) {
        PrintStream out;
        if (exitStatus == EXIT_STATUS_SUCCESS)
            out = System.out;
        else
            out = System.err;

        out.println("usage: java it.unibo.cs.csrobots.Server [OPTION]...");
        out.println("\t-board\t\tname of the game map file [required]");
        out.println("\t-address\tthe address this server should listen at [default=localhost]");
        out.println("\t-port\t\tlisten port [default=7919]");
        out.println("\t-help\t\tprint this help message");

        System.exit(exitStatus);
    }

    public static void main(String[] args) {
        String board = null;
        String listenAddr = defaultListenAddr;
        int listenPort = DEFAULT_LISTEN_PORT;

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-board")) {
                if (i + 1 == args.length)
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

                board = args[++i];
            } else if (args[i].equals("-address")) {
                if (i + 1 == args.length)
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

                listenAddr = args[++i];
            } else if (args[i].equals("-port")) {
                if (i + 1 == args.length)
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

                try {
                    listenPort = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);
                }
            } else if (args[i].equals("-help")) {
                printUsageAndQuit(EXIT_STATUS_SUCCESS);
            } else {
                printUsageAndQuit(EXIT_STATUS_BAD_ARGS);
            }
        }

        if (board == null)
            printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

        Server server = null;

        try {
            server = new Server(listenPort, listenAddr, board);
        } catch(FileNotFoundException e) {
            System.err.printf("error: board file not found: `%s'\n", board);
            System.exit(EXIT_STATUS_FAILURE);
        } catch(InvalidBoardException e) {
            System.err.printf("error: invalid board file [cause: `%s']\n", e.getMessage());
            System.exit(EXIT_STATUS_FAILURE);
        } catch (IOException e) {
            System.err.printf("error: unexpecetd I/O error [cause: %s]\n", e.getMessage());
            System.exit(EXIT_STATUS_FAILURE);
        }

        try {
            server.start();
        } catch (Exception e) {
            // We should have caught this elsewhere
            System.err.printf("error: unexpecetd exception:\n");
            e.printStackTrace();
            System.exit(EXIT_STATUS_FAILURE);
        }

        System.exit(EXIT_STATUS_SUCCESS);
    }
}
