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
import java.util.HashMap;
import java.util.Map;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.regex.*;

/**
 * A utility class for the task of loading all of the game parameters.
 */
class GameConfig {
    /** Regular expressions for parsing the associated `.items' file. */
    private static final String robotSpecRegex =
        "^robot (\\d+) (\\d+) (\\d+) @ \\((\\d+),(\\d+)\\).*";
    private static final String packetSpecRegex =
        "^package (\\d+) (\\d+) \\((\\d+),(\\d+)\\) @ \\((\\d+),(\\d+)\\).*";
    private static final String commentRegex =
        "^\\s*#.*";
    private static final Pattern robotSpecPattern = Pattern.compile(robotSpecRegex);
    private static final Pattern packetSpecPattern = Pattern.compile(packetSpecRegex);
    private static final Pattern commentPattern = Pattern.compile(commentRegex);

    /// The game elements
    private Field field;
    private LinkedList<Robot> robots;
    private HashMap<Packet, Position> packetMap;

    public GameConfig(String boardPath)
        throws IOException, InvalidBoardException, FileNotFoundException
    {
        BufferedReader reader;
        String line;

        // The playfield ...
        LinkedList<String> fieldRows = new LinkedList<String>();
        reader = new BufferedReader(new FileReader(boardPath));
        while ((line = reader.readLine()) != null)
            fieldRows.addLast(line);
        this.field = new Field(fieldRows);
        reader.close();

        // ... and it's inhabitants:
        reader = new BufferedReader(new FileReader(boardPath + ".items"));
        int numRobots = 0, numPackets = 0;
        robots = new LinkedList<Robot>();
        packetMap = new HashMap<Packet, Position>();

        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;

            Matcher matcher;

            // Skip comments.
            matcher = commentPattern.matcher(line);
            if (matcher.matches())
                continue;

            // Is it a robot spec?
            matcher = robotSpecPattern.matcher(line);
            if (matcher.matches()) {
                int id = Integer.parseInt(matcher.group(1));
                int strength = Integer.parseInt(matcher.group(2));
                int money = Integer.parseInt(matcher.group(3));
                Position position = new Position(Integer.parseInt(matcher.group(4)),
                                                 Integer.parseInt(matcher.group(5)));
                Robot r = new Robot(id, strength, money, position);
                robots.add(r);
                if (position.getY() >= field.getHeight() ||
                    position.getY() < 0 ||
                    position.getX() >= field.getWidth() ||
                    position.getX() < 0 ||
                    field.getType(position) == Field.CellType.WALL || 
                    field.getType(position) == Field.CellType.WATER) {
                    String message = String.format("%s.items (line %d): invalid robot position",
                                                   boardPath, lineNum);
                    throw new InvalidBoardException(message);
                }
                field.setRobot(position, r);
                continue;
            }

            // Maybe a packet spec?
            matcher = packetSpecPattern.matcher(line);
            if (matcher.matches()) {
                int id = Integer.parseInt(matcher.group(1));
                int weight = Integer.parseInt(matcher.group(2));
                Position destination = new Position(Integer.parseInt(matcher.group(3)),
                                                    Integer.parseInt(matcher.group(4)));
                Position position = new Position(Integer.parseInt(matcher.group(5)),
                                                 Integer.parseInt(matcher.group(6)));

                Packet packet = new Packet(id, weight, destination);
                packetMap.put(packet, position);
                field.addPacket(position, packet);

                continue;
            }

            // It's something we cannot make sense of!
            throw new InvalidBoardException(String.format("%s.items (line %d): syntax error",
                                                          boardPath, lineNum));
        }
    }

    public Field getField() {
        return field;
    }

    public Map<Packet, Position> getPacketMap() {
        return packetMap;
    }

    public LinkedList<Robot> getRobots() {
        return robots;
    }
}
