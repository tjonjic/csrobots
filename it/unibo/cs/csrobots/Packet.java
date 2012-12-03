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

/**
 * An instance of this class represents a single "packet" (or "package").
 * It is entirely immutable.
 */
public class Packet extends GameObject {
    private final int weight;
    private final Position destination;

    public Packet(int id, int weight, Position destination)
        throws IllegalArgumentException
    {
        super(id);

        if (weight < 0)
            throw new IllegalArgumentException("invalid argument `weight'");
        this.weight = weight;

        if (destination == null)
            throw new IllegalArgumentException();
        this.destination = destination;
    }

    public static Packet parseString(String str) {
        if (!str.matches("\\d+,\\d+,\\d+,\\d+"))
            throw new IllegalArgumentException();

        Scanner s = new Scanner(str).useDelimiter(",");
        int id = s.nextInt();
        Position destination = new Position(s.nextInt(), s.nextInt());
        int weight = s.nextInt();

        return new Packet(id, weight, destination);
    }

    public int getWeight() {
        return weight;
    }

    public Position getDestination() {
        return destination;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        Packet other = (Packet) obj;
        return getId() == other.getId();
    }

    public String toString() {
        return String.format("%s [id=%d,weight=%d,destination=(%d,%d)]",
                             getClass().getName(),
                             getId(), weight,
                             destination.getY(), destination.getX());
    }
}
