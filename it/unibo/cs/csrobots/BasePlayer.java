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

import java.util.Scanner;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;

import java.net.ProtocolException;
import java.io.IOException;
import java.text.ParseException;

public abstract class BasePlayer implements UpdateHandler {
    private static final String localhost = "127.0.0.1";

    private Stub serverStub = null;

    // Our character
    private int playerId = -1;
    private int money = -1;
    private int strength = -1;
    private boolean alive = true;
    private Position position = null;

    public BasePlayer(int port, String host)
        throws StubException
    {
        ServerConnection sc = new ServerConnection(port, host);
        serverStub = sc.getStub();
    }

    public BasePlayer(int port)
        throws StubException
    {
        this(port, localhost);
    }

    private void sendLine(String line) {
        serverStub.getWriter().print(line + "\n");
        serverStub.getWriter().flush();
    }

    private String recvLine() throws IOException {
        String str;
        if ((str = serverStub.getReader().readLine()) == null)
            throw new IOException();
        return str;
    }

    private void recvPlayfield() throws IOException, ProtocolException {
        String sizeSpec = recvLine();

        Scanner scanner = new Scanner(sizeSpec);
        int width = scanner.nextInt();
        int height = scanner.nextInt();

        LinkedList<String> field = new LinkedList<String>();
        for (int row = 0; row < height; row++)
            field.add(recvLine());

        try {
            parseField(field, width, height);
        } catch (InvalidBoardException e) {
            throw new ProtocolException("invalid board: " + e.getMessage());
        }
    }

    private void recvPlayerConf() throws IOException, ProtocolException {
        String playerConf = recvLine();
        if (!playerConf.matches("^\\d+ \\d+ \\d+$"))
            throw new ProtocolException("invalid player conf \"" + playerConf + "\"");

        Scanner scanner = new Scanner(playerConf);
        playerId = scanner.nextInt();
        strength = scanner.nextInt();
        money = scanner.nextInt();
    }

    private void recvUpdates() throws IOException, ProtocolException {
        try {
            String bundle = recvLine();
            if (!bundle.matches("^\\[.*\\]$"))
                throw new ParseException(bundle, 0);

            String[] updates = (bundle.substring(1, bundle.length() - 1)).split(";");

            for (String update : updates)
                if (update.length() > 0)
                    Update.invokeHandler(update, this);
        } catch (ParseException e) {
            System.err.printf("error parsing: %s", e.getMessage());
            throw new ProtocolException("ivalid update format");
        }
    }

    private void recvPacketList() throws IOException, ProtocolException {
        String str = recvLine();
				if (!str.matches("^\\[.*\\]$"))
					throw new ProtocolException("bad package list");
					
        String[] pkgs = str.substring(1, str.length() - 1).split(";");
        LinkedList<Packet> pkgList =  new LinkedList<Packet>();
        for (String s : pkgs) {
            if (!s.equals(""))
                pkgList.add(Packet.parseString(s));
        }
        handlePacketsNotify(pkgList);
    }

    private void sendCommand() {
        Command cmd = issueCommand();
        System.out.printf("sending command: %s\n", cmd.toString());
        sendLine(cmd.toString());
    }

    public void play() {
        try {

            sendLine("player");

            recvPlayfield();
            recvPlayerConf();
            recvUpdates();

            while (isAlive()) {
                recvPacketList();
                sendCommand();
                recvUpdates();
            }
        } catch (ProtocolException e) {
            System.err.printf("error: protocol error [reason: %s]\n" + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println("error: IO error: is the server running?\n" + e.getMessage());
            System.exit(1);
        }
    }

    //// public methods

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    protected int getId() {
        return playerId;
    }

    protected int getMoney() {
        return money;
    }

    protected void setMoney(int money) {
        this.money = money;
    }

    protected int detractMoney(int amount) {
        this.money -= amount;
        return amount;
    }

    protected int getStrength() {
        return strength;
    }

    protected Position getPosition() {
        return position;
    }

    protected void setPosition(Position p) {
        position = p;
    }

    protected abstract Command issueCommand();
    protected abstract void parseField(List<String> rows, int width, int height)
        throws InvalidBoardException;
    protected abstract void handlePacketsNotify(LinkedList<Packet> packets);
}
