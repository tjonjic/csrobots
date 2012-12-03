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
 * An update signalling a player has moved in a specified direction.
 */
public class MoveUpdate extends Update {
    private Direction dir;

    public Direction getDirection() { return dir; }
    
    public MoveUpdate(int playerId, Direction dir) {
        super(playerId);
        this.dir = dir;
    }

    public String toString() {
        return String.format("#%d,%s", getPlayerId(), dir.name());
    }
}
