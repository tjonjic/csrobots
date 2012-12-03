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
import java.util.HashMap;
import java.util.Scanner;
import java.util.Observable;
import java.util.Observer;

import java.io.PrintStream;
import java.text.ParseException;
import java.io.IOException;
import java.net.ProtocolException;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class DebuggerFrame extends JFrame implements Observer {
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;
    private static final String defaultTitle = "CSRobots";

    private Debugger debugger = null;
    private FieldView fieldView = null;

    private JMenuItem stepMenuItem;
    private JTextField statusField;

    public DebuggerFrame(Debugger debugger) {
        super(defaultTitle);

        this.debugger = debugger;
        debugger.addObserver(this);

        JPanel panel = new JPanel(new BorderLayout());

        fieldView = new FieldView();

        JScrollPane scroller = new JScrollPane();
        scroller.getViewport().add(fieldView);

        panel.add(scroller, BorderLayout.CENTER);
        add(panel);

        statusField = new JTextField("");
        statusField.setEditable(false);
        add(statusField, BorderLayout.SOUTH);

        createMenuBar();

        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    private void nextTurn() {
        debugger.step();
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu runMenu = new JMenu("Run");
        runMenu.setMnemonic('r');
        menuBar.add(runMenu);

        stepMenuItem = runMenu.add(new JMenuItem("Step"));
        stepMenuItem.setMnemonic('s');
        stepMenuItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        stepMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    nextTurn();
                }
            });

        setJMenuBar(menuBar);
        updateSensitivity();
    }

    public void updateSensitivity() {
        if (debugger.getSessionState() == Debugger.SessionState.RUNNING) {
            if (debugger.getState() == Debugger.DebuggerState.BUSY) {
                stepMenuItem.setEnabled(false);
            } else {
                stepMenuItem.setEnabled(true);
            }
        } else {
            stepMenuItem.setEnabled(false);
        }
    }

    public void update(Observable o, Object arg) {
        if (debugger.getField() != null)
            fieldView.setField(debugger.getField());

        SwingUtilities.invokeLater(new Runnable()
            {
                public void run() {
                    updateSensitivity();

                    switch (debugger.getSessionState()) {
                    case RUNNING:
                        if (debugger.getState() == Debugger.DebuggerState.BUSY)
                            statusField.setText("sending/receiving...");
                        else 
                            statusField.setText("ready");
                        break;

                    case STARTING:
                        statusField.setText("starting...");
                        break;

                    default:
                        statusField.setText("");
                    }
                }
            });
    }

    private class FieldView extends JComponent implements Observer {
        private static final int SQUARE_WIDTH = 20;
        private static final int BORDER_WIDTH = 5;
        private Field field;
        private Icon robotIcon = null;

        public FieldView() {
            this.field = field;

            robotIcon = new ImageIcon("icons/robot.png");
            setToolTipText("csrobots");

            revalidate();
            updateUI();
        }

        public void setField(final Field field) {
            if (this.field != null)
                return;

            this.field = field;
            field.addObserver(this);

            SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() {
                        int prefWidth = field.getWidth() * SQUARE_WIDTH + BORDER_WIDTH * 2;
                        int prefHeight = field.getHeight() * SQUARE_WIDTH + BORDER_WIDTH * 2;
                        Dimension dimension = new Dimension(prefWidth, prefHeight);
                        setMinimumSize(dimension);
                        setPreferredSize(dimension);
                        revalidate();
                        updateUI();
                        repaint(getX(), getY(), getWidth(), getHeight());
                    }
                });
        }

        public void update(Observable o, Object arg) {
            repaint(getX(), getY(), getWidth(), getHeight());
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (field == null)
                return;

            Graphics2D g2d = (Graphics2D) g.create();

            int origX = getX() + (getWidth() - field.getWidth() * SQUARE_WIDTH) / 2;
            int origY = getY() + (getHeight() - field.getHeight() * SQUARE_WIDTH) / 2;

            for (int row = 0; row < field.getHeight(); row++) {
                for (int col = 0; col < field.getWidth(); col++) {
                    Position pos = new Position(row, col);
                    switch (field.getType(pos)) {
                    case OPEN_SPACE:
                        g2d.setColor(Color.green);
                        break;
                    case BASE:
                        g2d.setColor(Color.yellow);
                        break;
                    case WALL:
                        g2d.setColor(Color.gray);
                        break;
                    case WATER:
                        g2d.setColor(Color.blue);
                        break;
                    }
                    g2d.fill3DRect(origX + col * SQUARE_WIDTH,
                                   origY + row * SQUARE_WIDTH,
                                   SQUARE_WIDTH, SQUARE_WIDTH, false);
                    if (field.getRobot(pos) != null) {
                        robotIcon.paintIcon(this, g2d,
                                            origX + col * SQUARE_WIDTH,
                                            origY + row * SQUARE_WIDTH);
                    }
                }
            }
        }

        private Position pickSquare(int x, int y) {
            int origX = getX() + (getWidth() - field.getWidth() * SQUARE_WIDTH) / 2;
            int origY = getY() + (getHeight() - field.getHeight() * SQUARE_WIDTH) / 2;

            int fieldW = field.getWidth() * SQUARE_WIDTH;
            int fieldH = field.getHeight() * SQUARE_WIDTH;

            if (x < origX || x >= origX + fieldW ||
                y < origY || y >= origY + fieldH)
                return null;
            else
                return new Position((y - origY) / SQUARE_WIDTH,
                                    (x - origX) / SQUARE_WIDTH);
        }

        public String getToolTipText(MouseEvent event) {
            if (field == null)
                return "n/a";

            Position p = pickSquare(event.getX(), event.getY());

            if (p == null)
                return "n/a";
            else if (field.getRobot(p) != null)
                return String.format("Robot %d at [row=%d, column=%d]",
                                     field.getRobot(p).getId(),
                                     p.getY(), p.getX());
            else
                return String.format("row=%d, column=%d [%s]",
                                     p.getY(), p.getX(),
                                     field.getType(p).name());
        }
    }
}

public class Debugger extends Observable implements UpdateHandler {
    public enum DebuggerState {
        BUSY,
        IDLE,
    };

    public enum SessionState {
        NONE,
        STARTING,
        RUNNING,
        TERMINATED
    }

    private static final int EXIT_STATUS_SUCCESS  = 0;
    private static final int EXIT_STATUS_FAILURE  = 1;
    private static final int EXIT_STATUS_BAD_ARGS = 2;

    private Field field = null;
    private HashMap<Integer, Robot> robots;
    private Stub server = null;
    private SessionState sessionState = null;
    private DebuggerState state;

    public Debugger(int port, String address)
        throws StubException
    {
        ServerConnection sc = new ServerConnection(port, address);
        server = sc.getStub();
        sendLine("debugger");

        robots = new HashMap<Integer, Robot>();
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public DebuggerState getState() {
        return state;
    }

    public Field getField() {
        return field;
    }

    private void sendLine(String line) {
        server.getWriter().print(line + "\n");
        server.getWriter().flush();
    }

    private String recvLine() throws IOException {
        String str;
        if ((str = server.getReader().readLine()) == null)
            throw new IOException();
        return str;
    }

    public void handleUpdate(MoveUpdate update) {
        Robot r = robots.get(update.getPlayerId());
        assert field.getRobot(r.getPosition()) != null;
        field.setRobot(r.getPosition(), null);
        r.setPosition(r.getPosition().move(update.getDirection()));
        field.setRobot(r.getPosition(), r);
    }

    public void handleUpdate(PickUpdate update) {}
    public void handleUpdate(DropUpdate update) {}

    public void handleUpdate(AppearUpdate update) {
        Robot r = robots.get(update.getPlayerId());
        assert r != null;
        r.setPosition(update.getPosition());
        field.setRobot(update.getPosition(), r);
    }

    public void handleUpdate(KillUpdate update) {
        Robot r = robots.get(update.getPlayerId());
        field.setRobot(r.getPosition(), null);
        robots.remove(update.getPlayerId());

        if (robots.isEmpty()) {
            setSessionState(SessionState.TERMINATED);
        }
    }

    private void recvPlayfield() throws IOException, ProtocolException {
        String sizeSpec = recvLine();

        Scanner scanner = new Scanner(sizeSpec);
        int width = scanner.nextInt();
        int height = scanner.nextInt();

        LinkedList<String> rows = new LinkedList<String>();
        for (int row = 0; row < height; row++)
            rows.add(recvLine());

        try {
            field = new Field(rows);
        } catch (InvalidBoardException e) {
            throw new ProtocolException("invalid board: " + e.getMessage());
        }
    }

    private void recvPlayerList() throws IOException, ProtocolException {
        String str = recvLine();

        if (!str.matches("^\\[(\\d+ \\d+ \\d+;)*\\d+ \\d+ \\d+\\]$"))
            throw new ProtocolException();

        str = str.substring(1, str.length() - 1);
        String[] configs = str.split(";");

        for (String conf : configs)
            if (conf.length() > 0) {
                Scanner scanner = new Scanner(conf);
                int playerId = scanner.nextInt();
                int strength = scanner.nextInt();
                int money = scanner.nextInt();
                robots.put(playerId, new Robot(playerId, strength, money));
            }
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
        } catch (IOException e) {
            System.err.println("error receiving updates; is the server still alive?");
            //e.printStackTrace();
        }
    }

    private void start() {
        setSessionState(SessionState.STARTING);
        Runnable r = new Runnable()
            {
                public void run() {
                    try {
                        recvPlayfield();
                        recvPlayerList();
                        recvUpdates();
                        sessionState = SessionState.RUNNING;
                        setChanged();
                        notifyObservers();
                    } catch (Exception e) {
                        System.err.printf("error: %s\n", e.getMessage());
                        e.printStackTrace();
                        System.exit(EXIT_STATUS_FAILURE);
                    }
                }
            };
        new Thread(r).start();
    }

    private void setState(DebuggerState newState) {
        if (newState != state) {
            state = newState;
            setChanged();
            notifyObservers();
        }
    }

    private void setSessionState(SessionState newState) {
        if (newState != sessionState) {
            sessionState = newState;
            setChanged();
            notifyObservers();
        }
    }

    public void step() {
        if (sessionState != SessionState.RUNNING)
            return;

        setState(DebuggerState.BUSY);

        Runnable r = new Runnable()
            {
                public void run() {
                    try {
                        sendLine("step");
                        recvUpdates();
                    } catch (Exception e) {
                        System.err.printf("error: %s\n", e.getMessage());
                        e.printStackTrace();
                        System.exit(EXIT_STATUS_FAILURE);
                    }
                    setState(DebuggerState.IDLE);
                }
            };
        new Thread(r).start();
    }

    private static void printUsageAndQuit(int exitStatus) {
        PrintStream out;
        if (exitStatus == EXIT_STATUS_SUCCESS)
            out = System.out;
        else
            out = System.err;

        out.println("usage: java it.unibo.cs.csrobots.Debugger [OPTION]...");
        out.println("\t-address\tthe server address [default=localhost]");
        out.println("\t-port\t\tserver port [default=7919]");

        System.exit(exitStatus);
    }

    public static void main(String[] args) {
        String serverAddr = "127.0.0.1";
        int serverPort = 7919;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-address")) {
                if (i + 1 == args.length)
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

                serverAddr = args[++i];
            } else if (args[i].equals("-port")) {
                if (i + 1 == args.length)
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);

                try {
                    serverPort = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    printUsageAndQuit(EXIT_STATUS_BAD_ARGS);
                }
            } else {
                printUsageAndQuit(EXIT_STATUS_BAD_ARGS);
            }
        }

        Debugger debugger = null;
        try {
            debugger = new Debugger(serverPort, serverAddr);
        } catch (Exception e) {
            //e.printStackTrace();
            System.err.printf("error: cannot conect to `%s:%d' [reason:%s]\n",
                              serverAddr, serverPort, e.getMessage());
            System.exit(1);
        }

        if (debugger != null) {
            try {
                final Debugger deb = debugger;
                SwingUtilities.invokeAndWait(new Runnable()
                    {
                        public void run() {
                            DebuggerFrame frame = new DebuggerFrame(deb);
                            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                            frame.setVisible(true);
                        }
                    });
            } catch (Exception e) {
                System.err.println("error loading UI");
                e.printStackTrace();
                System.exit(EXIT_STATUS_FAILURE);
            }
            debugger.start();
        }
    }
}
