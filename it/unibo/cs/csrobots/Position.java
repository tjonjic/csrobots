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

/**
 * Describes an (immutable) position.
 */
public class Position {
    /** The row & col */
    private int y, x;

    public Position(int y, int x) {
        this.y = y;
        this.x = x;
    }

    public Position() {
        this(0, 0);
    }

    public int getRow() {
        return getY();
    }

    public int getY() {
        return y;
    }

    public int getColumn() {
        return getX();
    }

    public int getX() {
        return x;
    }

    public Position move(Direction d) {
        switch (d) {
        case n:
            return new Position(y - 1, x);
        case s:
            return new Position(y + 1, x);
        case e:
            return new Position(y, x + 1);
        case w:
            return new Position(y, x - 1);
        default:
            assert false;
        }

        return null;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        Position other = (Position) obj;
        return y == other.y && x == other.x;
    }

    public int hashCode() {
        return y * x + x;
    }

    public String toString() {
        return String.format("%s [row=%d,column=%d]", getClass().getName(), y, x);
    }
}
