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

import java.util.Observable;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;

import java.io.PrintWriter;

/**
 * The playfield. Offers a convenient way for representing the game map.
 */
public class Field extends Observable {

    /**
     * All of the terrain types
     */
    public enum CellType {
        OPEN_SPACE,
        BASE,
        WATER,
        WALL
    }

    private class Cell {
        public CellType type;
        public CellData data = null;

        public Cell(CellType type) {
            this.type = type;
            if (isHolder())
                data = new CellData();
        }

        public boolean isHolder() {
            return (type == CellType.OPEN_SPACE ||
                    type == CellType.BASE);
        }
    }

    private class CellData {
        public HashMap<Integer, Packet> packets = null;
        public Robot visitor = null;

        public CellData() {
            packets = new HashMap<Integer, Packet>();
        }
    }

    private Cell[][] field = null;

    // table driven field repr <-> char repr conversion -- real crack
    private static HashMap<Character, CellType> symbolsToCellType;
    private static EnumMap<CellType, Character> cellTypeToSymbols;
    static {
        Object[][] tmp = {
            { CellType.OPEN_SPACE, "." },
            { CellType.BASE,       "@" },
            { CellType.WATER,      "~" },
            { CellType.WALL,       "#" }
        };

        symbolsToCellType = new HashMap<Character, CellType>();

        Class<CellType> enumClass = CellType.OPEN_SPACE.getDeclaringClass();
        cellTypeToSymbols = new EnumMap<CellType, Character>(enumClass);

        for (int i = 0; i < tmp.length; ++i) {
            symbolsToCellType.put(((String)tmp[i][1]).charAt(0), (CellType) tmp[i][0]);
            cellTypeToSymbols.put((CellType) tmp[i][0], ((String)tmp[i][1]).charAt(0));
        }
    }

    /**
     * Constructs a new <code>Field</code> object from a list of string
     * representation of it's rows. Throws <code>InvalidBoardException</code>
     * if the representation cannot be parsed properly.
     */
    public Field(List<String> list) throws InvalidBoardException {
        if (list == null)
            throw new IllegalArgumentException();

        if (list.size() == 0)
            throw new InvalidBoardException("empty board");

        int expectedWidth = list.get(0).length();
        
        field = new Cell[list.size()][expectedWidth];
		
        int y = 0;
        for (String l : list) {
            int x = 0;
            for (int i = 0; i < l.length(); i++) {
                if (symbolsToCellType.containsKey(l.charAt(i))) {
                    CellType type = symbolsToCellType.get(l.charAt(i));
                    field[y][x] = new Cell(type);
                } else {
                    String msg = String.format("invalid cell at (%d,%d)", y, x);
                    throw new InvalidBoardException(msg);
                }
                x++;
            }
            y++;
        }
    }

    /**
     * Prints the field's character representation to the supplied print
     * writer.
     * <p>
     * This exists for efficency reasons only.
     */
    public void print(PrintWriter w) {
        for (Cell[] row : field) {
            String repr = "";

            for (Cell sq : row)
                repr += cellTypeToSymbols.get(sq.type);
            repr += "\n";

            w.print(repr);
            // ...and flush after yourself!
            w.flush();
        }
    }

    public String toString() {
        String repr = "";
        for (Cell[] row : field) {
            for (Cell sq : row)
                repr += cellTypeToSymbols.get(sq.type);
            repr += "\n";
        }
        return repr;
    }

    public int getHeight() {
        return field.length;
    }

    public int getWidth() {
        return field[0].length;
    }

    private Cell get(Position p) {
        return field[p.getY()][p.getX()];
    }

    private void validatePosition(Position p) {
        if (p == null)
            throw new IllegalArgumentException();

        if (p.getX() < 0 ||
            p.getX() >= getWidth() ||
            p.getY() < 0 ||
            p.getY() >= getHeight())
            throw new IndexOutOfBoundsException();
    }

    private void notifyAbout(Position pos) {
        setChanged();
        notifyObservers(pos);
    }

    public CellType getType(Position p)
        throws IndexOutOfBoundsException
    {
        validatePosition(p);

        return get(p).type;
    }

    public CellType getType(int y, int x) {
        return field[y][x].type;
    }

    public boolean containsPacket(Position pos, int packetId)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        Cell sq = get(pos);
        return (sq.isHolder() &&
                sq.data.packets.containsKey(packetId));
    }

    public Packet getPacket(Position pos, int packetId)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        if (containsPacket(pos, packetId))
            return get(pos).data.packets.get(packetId);
        else
            return null;
    }

    public Packet[] getPackets(Position pos)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        Cell sq = get(pos);
        if (sq.isHolder())
            return sq.data.packets.values().toArray(new Packet[0]);
        else
            return null;
    }

    public boolean hasPackets(Position pos)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        return (get(pos).isHolder() &&
                !get(pos).data.packets.isEmpty());
    }
            

    public Packet removePacket(Position pos, int packetId)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        if (containsPacket(pos, packetId))
            return get(pos).data.packets.remove(packetId);
        else
            return null;
    }

    public void addPacket(Position pos, Packet packet)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        Cell sq = get(pos);

        if (sq.isHolder())
            if (!containsPacket(pos, packet.getId()))
                sq.data.packets.put(packet.getId(), packet);
    }

    public void setRobot(Position pos, Robot robot)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        if (get(pos).isHolder())
            get(pos).data.visitor = robot;

        notifyAbout(pos);
    }

    public Robot getRobot(Position pos)
        throws IndexOutOfBoundsException
    {
        validatePosition(pos);

        if (get(pos).isHolder())
            return get(pos).data.visitor;
        else
            return null;
    }
}
