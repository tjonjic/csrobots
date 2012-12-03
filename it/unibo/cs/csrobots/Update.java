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

import java.util.regex.*;
import java.text.ParseException;

/**
 * Base class for all player updates.
 * <p>
 * Un <i>update</i> represents a completed board event relative to a
 * specified robot.
 */
public abstract class Update {
    /** The player this update is about */
    private final int playerId;

    public Update(int playerId) {
        this.playerId = playerId;
    }

    public int getPlayerId() {
        return playerId;
    }

    private static final Pattern moveUpdatePattern   = Pattern.compile("^#(\\d+),([nsew])$");
    private static final Pattern pickUpdatePattern   = Pattern.compile("^#(\\d+),p (\\d+)$");
    private static final Pattern dropUpdatePattern   = Pattern.compile("^#(\\d+),d (\\d+)$");
    private static final Pattern appearUpdatePattern = Pattern.compile("^#(\\d+),r (\\d+) c (\\d+)$");
    private static final Pattern killUpdatePattern   = Pattern.compile("^#(\\d+),k$");

    public static void invokeHandler(String str, UpdateHandler handler)
        throws ParseException
    {
        Matcher matcher;

        if ((matcher = moveUpdatePattern.matcher(str)).matches()) {
            int robotId = Integer.parseInt(matcher.group(1));
            Direction dir = Enum.valueOf(Direction.n.getDeclaringClass(), matcher.group(2));
            handler.handleUpdate(new MoveUpdate(robotId, dir));
        } else if ((matcher = pickUpdatePattern.matcher(str)).matches()) {
            int robotId = Integer.parseInt(matcher.group(1));
            int packetId = Integer.parseInt(matcher.group(2));
            handler.handleUpdate(new PickUpdate(robotId, packetId));
        } else if ((matcher = dropUpdatePattern.matcher(str)).matches()) {
            int robotId = Integer.parseInt(matcher.group(1));
            int packetId = Integer.parseInt(matcher.group(2));
            handler.handleUpdate(new DropUpdate(robotId, packetId));
        } else if ((matcher = appearUpdatePattern.matcher(str)).matches()) {
            int robotId = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int x = Integer.parseInt(matcher.group(3));
            handler.handleUpdate(new AppearUpdate(robotId, new Position(y, x)));
        } else if ((matcher = killUpdatePattern.matcher(str)).matches()) {
            int robotId = Integer.parseInt(matcher.group(1));
            handler.handleUpdate(new KillUpdate(robotId));
        } else {
            throw new ParseException("Unknown update: \"" + str + "\"", 0);
        }
    }
}
