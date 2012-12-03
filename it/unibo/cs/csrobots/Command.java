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

/**
 * <code>Command</code> serves as the base class for all classes representing
 * actual player commands.
 * <p>
 * This serves as the abstraction of the command notion in our textual
 * protocol level.
 */
public abstract class Command {
    /** The player this command is associated with */
    private final int playerId;

    /** The player's bid (i.e. the execution priority for this command) */
    private final int bid;

    public Command(int playerId, int bid) {
        this.playerId = playerId;
        this.bid = bid;
    }

    public int getPlayerId() { return playerId; }

    public int getBid() { return bid; }

    /**
     * Invoke the appropriate handler for this command.
     */
    public abstract LinkedList<Update> invokeHandler(CommandHandler handler);
}
