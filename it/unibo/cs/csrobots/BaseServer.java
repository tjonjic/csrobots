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

import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Comparator;

import java.util.regex.*;
import java.util.Scanner;

import java.io.IOException;

/**
 * This class attempts to provide a protocol layer abstraction and is
 * subclassed to implement actual game logic.
 */
public abstract class BaseServer
{
    /**
     * Types of messages that can appear in a log
     */
    protected enum LogMessageLevel {
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }

    protected class Client {
        private Robot robot = null;
        private int score = 0;
        private Stub stub;
        private boolean alive = true;

        public Client(Robot r) {
            robot = r;
        }

        public int getId() {
            return robot.getId();
        }

        public Robot getRobot() {
            return robot;
        }

        public Stub getStub() {
            return stub;
        }

        public void setStub(Stub stub) {
            this.stub = stub;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
        }

        public boolean isAlive() {
            return alive;
        }

        public void addScore(int numPoints) {
            score += numPoints;
        }

        public int getScore() { return score; }
    }

    /** Players and debuggers */
    protected LinkedHashMap<Integer, Client> clients;
    private LinkedList<Stub> debuggers = new LinkedList<Stub>();

    /** Number of turns since the beginning of time */
    private int turnCount = 0;

    private static void sendLine(Stub stub, String line) {
        stub.getWriter().print(line + "\n");
        stub.getWriter().flush();
    }

    private static String recvLine(Stub stub) throws IOException {
        String str;
        if ((str = stub.getReader().readLine()) == null)
            throw new IOException();
        return str;
    }

    // Command parsing
    private static final Pattern movePattern = Pattern.compile("^(-?\\d+) move ([nwse])");
    private static final Pattern pickPattern = Pattern.compile("^(-?\\d+) pick (\\d+)(( \\d+)*)$");
    private static final Pattern dropPattern = Pattern.compile("^(-?\\d+) drop (\\d+)(( \\d+)*)$");

    /**
     * Tries to parse the command sent to us by the player and, if successful,
     * it returns its corresponding higher-level representation.
     */
    private Command parseCommand(int id, String command) {
        Matcher matcher;

        matcher = movePattern.matcher(command);
        if (matcher.matches()) {
            return new MoveCommand(id, Integer.parseInt(matcher.group(1)),
                                   Enum.valueOf(Direction.n.getDeclaringClass(),
                                                matcher.group(2)));
        }

        matcher = pickPattern.matcher(command);
        if (matcher.matches()) {
            LinkedList<Integer> list = new LinkedList<Integer>();
            list.add(Integer.parseInt(matcher.group(2)));
            if (matcher.group(3) != null) {
                Scanner s = new Scanner(matcher.group(3));
                while (s.hasNextInt()) 
                    list.add(s.nextInt());
            }
            return new PickCommand(id, Integer.parseInt(matcher.group(1)), list);
        }

        matcher = dropPattern.matcher(command);
        if (matcher.matches()) {
            LinkedList<Integer> list = new LinkedList<Integer>();
            list.add(Integer.parseInt(matcher.group(2)));
            if (matcher.group(3) != null) {
                Scanner s = new Scanner(matcher.group(3));
                while (s.hasNextInt()) 
                    list.add(s.nextInt());
            }
            return new DropCommand(id, Integer.parseInt(matcher.group(1)), list);
        }

        return null;
    }

    /**
     * Waits a command from each player and returns them as a list.
     */
    private LinkedList<Command> recvCommands() throws IOException {
        LinkedList<Command> commands = new LinkedList<Command>();

        for (Client p : clients.values()) {
            if (!p.isAlive())
                continue;

            // Try to read a command from the player; we don't have a sensible
            // policy for dealing with invalid commands -- we just keep trying :-)
            Command command;
            while (true) {
                logMessage(LogMessageLevel.INFO,
                           "awaiting command from player %d", p.getId());

                String str;
                try {
                    if ((str = p.getStub().getReader().readLine()) == null)
                        throw new IOException();
                } catch (IOException e) {
                    logMessage(LogMessageLevel.ERROR,
                               "error getting command from player %d; " +
                               "has the player disconnected?", p.getId());
                    handleFatalError();
                    throw e;
                }

                if ((command = parseCommand(p.getId(), str)) != null)
                    break;

                logMessage(LogMessageLevel.WARNING,
                           "could not make sense of \"%s\"; " +
                           "the player should probably recheck its sanity!",
                           str);
            }
            commands.add(command);
        }

        return commands;
    }

    /**
     * Notify each player with the list of updates for the turn.
     */
    private void sendUpdates(LinkedList<Update> updates) {
        logMessage(LogMessageLevel.INFO,
                   "sending updates to all players");

        String updateStr = "[";
        if (updates != null) {
            boolean first = true;
            for (Update update : updates) {
                updateStr += (first ? "" : ";") + update.toString();
                first = false;
            }
        }
        updateStr += "]";

        for (Client c : clients.values())
            sendLine(c.getStub(), updateStr);

        for (Stub debugger : debuggers)
            sendLine(debugger, updateStr);
    }

    /**
     * Inform the player about the packages he has stumbled upon.
     */
    private void sendPacketList() {
        for (Client c : clients.values()) {
            if (!c.isAlive()) continue;
            String packetStr = "[";
            Packet[] packets = getPacketListForPlayer(c);
            if (packets != null) {
                boolean first = true;
                for (Packet p : packets) {
                    if (!first)
                        packetStr += ";";
                    packetStr += String.format("%d,%d,%d,%d",
                                               p.getId(),
                                               p.getDestination().getY(),
                                               p.getDestination().getX(),
                                               p.getWeight());
                    first = false;
                }
            }
            packetStr += "]";
            sendLine(c.getStub(), packetStr);
        }
    }

    private LinkedList<Update> createFirstUpdate() {
        LinkedList<Update> returnObj = new LinkedList<Update>();
        for (Client c : clients.values())
            returnObj.add(new AppearUpdate(c.getRobot().getId(),c.getRobot().getPosition()));
        return returnObj;
    }

    private void sendRobotConfig() {
        String bundle = "[";
        boolean first = true;
        for (Client c : clients.values()) {
            Robot r = c.getRobot();
            String config = String.format("%d %d %d",
                                          r.getId(),
                                          r.getStrength(),
                                          r.getMoney());
            sendLine(c.getStub(), config);
            if (!first)
                bundle += ";";
            first = false;
            bundle += config;
        }
        bundle += "]";

        for (Stub debugger : debuggers)
            sendLine(debugger, bundle);
    }

    private void sendFieldConfig() {
        Field field = getField();

        String dimen = String.format("%d %d", field.getWidth(), field.getHeight());

        for (Client client : clients.values()) {
            sendLine(client.getStub(), dimen);
            field.print(client.getStub().getWriter());
        }

        for (Stub debugger : debuggers) {
            sendLine(debugger, dimen);
            field.print(debugger.getWriter());
        }
    }
    
    private boolean isThereLife() {
        for (Client cl : clients.values())
            if (cl.isAlive())
                return true;

        return false;
    }

    protected void run() {
        try {
            sendFieldConfig();
            sendRobotConfig();
            sendUpdates(createFirstUpdate());
            while (isThereLife())
                doTurn();
            printRankings();
        }  catch (Exception e) { 
            logMessage(LogMessageLevel.ERROR,
                       "unexpected error [caused by: `%s']", e.getMessage());
            handleFatalError();
        }
    }

    /** 
     * If there is anyone just watching the game, wait for confirm to continue.
     */
    private void waitStep() {
        LinkedList<Stub> disc = new LinkedList<Stub>();

        for (Stub debugger : debuggers) {
            while (true) {
                String l = null;
                try {
                    l = recvLine(debugger);
                } catch (Exception e) {
                    disc.add(debugger);
                    break;
                }
                if (l.equals("step"))
                    break;
            }
        }

        for (Stub s : disc)
            debuggers.remove(s);
    }

    private void doTurn() throws Exception {
        logMessage(LogMessageLevel.INFO,
                   "starting turn %d", turnCount++);
        handleTurnStart();
        waitStep();
        sendPacketList();
        LinkedList<Command> commandList = recvCommands();
        LinkedList<Update> updates = handleCommands(commandList);
        sendUpdates(updates);
    }

    /**
     * Waits for incoming connections from as many clients as needed. Rejects
     * immediately clients that are not compliant.
     */
    protected void acceptConnections(int port, String address)
        throws StubException
    {
        PlayersGreeter greeter = new PlayersGreeter(port, address);

        logMessage(LogMessageLevel.INFO,
                   "server started; listening for connections on %s:%d",
                   address, port);

        logMessage(LogMessageLevel.INFO,
                   "waiting for %d player(s)", clients.size());

        for (Client c : clients.values()) {
            Stub stub;

            logMessage(LogMessageLevel.INFO,
                       "waiting for player %d ...", c.getId());

            while (true) {
                stub = greeter.getStub();

                String str = null;
                try {
                    str = recvLine(stub);
                } catch (Exception e) {
                    logMessage(LogMessageLevel.DEBUG,
                               "the client already disconnected?");
                    continue;
                }

                if (str.equals("player")) {
                    c.setStub(stub);
                    break;
                } else if (str.equals("debugger")) {
                    debuggers.add(stub);
                } else {
                    logMessage(LogMessageLevel.WARNING,
                               "acceptConnections(): protocol mismatch; " +
                               "rejecting client (i got `%s')", str);
                    try {
                        stub.getSocket().close();
                    } catch (Exception e) { /* missing something here? */ }
                }
            }
        }
    }

    private void printRankings() {
        Client[] sorted = new Client[clients.size()];
        clients.values().toArray(sorted);
			
        Arrays.sort(sorted, 
                    new Comparator<Client>() {
            public int compare(Client a, Client b) {
                return b.getScore() - a.getScore();
            }
        });

        System.out.println("******** Rankings ********");
        System.out.println("\tRobot\tScore");

        for (Client c : sorted)
            System.out.printf("\t#%d\t%d\n", c.getId(), c.getScore());
    }

    //// Abstract methods

    /**
     * @return a list of updates triggered by executing <tt>commands</tt>.
     */
    protected abstract LinkedList<Update> handleCommands(LinkedList<Command> commands);

    /**
     * A hook method invoked at the beginning of each turn.
     */
    protected abstract void handleTurnStart();

    /**
     * @return the list of packets the player should be notified about.
     */
    protected abstract Packet[] getPacketListForPlayer(Client client);

    /**
     * Log a message somewhere.
     */
    protected abstract void logMessage(LogMessageLevel level, 
                                       String messageFormat, Object ... args);

    protected abstract Field getField();

    protected abstract void handleFatalError();
}
