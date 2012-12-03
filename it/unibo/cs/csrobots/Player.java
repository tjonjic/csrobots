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

import java.io.PrintStream;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Queue;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;

public class Player extends BasePlayer {
    private static final int DEFAULT_SERVER_PORT = 7919;
    private static final String defaultServerAddr = "127.0.0.1";
    private static final int EXIT_STATUS_SUCCESS  = 0;
    private static final int EXIT_STATUS_FAILURE  = 1;
    private static final int EXIT_STATUS_BAD_ARGS = 2;

    private Field field;

    /** All the packets we're currently carrying */
    private LinkedList<Packet> packetLoad;

    /** Our total load, in the SI unit of your choice */
    private int load;

    /** The list of positions that may have packets on them */
    private LinkedList<Position> sourceList;

    /**
     * Squares with packets that are currently unavailable to us (e.g. due to
     * our limited carrying capacity). This can of course change each time we
     * we drop something.
     */
    private LinkedList<Position> suspendedSources;

    /** 
     * All the packets we know about. Makes for easy id to Packet translation.
     */
    private HashMap<Integer, Packet> packetDict;

    /** The foes */
    private HashMap<Integer, Robot> robots = null;

    /** The current target, if we happen to have one */
    private Position target = null;

    /** 
     * Meta quantity; in the various computations it stands for `unreachable'.
     */
    private int infinity;

    /** 
     * Distances from our current target. We try to be nice to the VM so this
     * is allocated only once and reused from there on.
     */
    private int[][] targetDists;

    /**
     * As above, but used temporarily for calculating the less expensive next
     * target. So this may, or may not, at a given point represent the dists
     * to all other reachable locations.
     */
    private int[][] sourceDists;

    /** Have we checked if each entry in our source list is reachable? */
    private boolean sourceListFiltered = false;

    /** A flag that tells if the above has been already initialized. */
    private boolean distanceComputed = false;

    /** The direction we're currently "scanning" */
    private Direction scanningDir = Direction.e;

    /** Our previous command, an often useful piece of information */
    private Command prevCommand = null;

    /** We flag each square we visit at least once as visited. */
    private boolean[][] visitedMap;

    public Player(int port, String host) throws StubException {
        super(port, host);
        packetLoad = new LinkedList<Packet>();
        sourceList = new LinkedList<Position>();
        packetDict = new HashMap<Integer, Packet>();
        target = null;
        robots = new HashMap<Integer, Robot>();
        suspendedSources = new LinkedList<Position>();
    }
		
    // Debug only
    private void dumpPlayerInfo() {
        String str = "[";
        for (Packet p : packetLoad)
            str += p.toString() + ";";
        str += "]";

        System.out.printf("id:%d, srength:%d, load:%d, money:%d, position:(%d,%d)\n" +
                          "\tpackets: %s\n", getId(), getStrength(), load, getMoney(),
                          getPosition().getY(), getPosition().getX(),
                          str);
    }

    /**
     * Let the supplied packet off our back.
     */
    private void releasePacket(Packet p) {
        if (packetLoad.contains(p)) {
            packetLoad.remove(p);
            load -= p.getWeight();
        }
    }

    /**
     * Release the packet we picked _last_.
     */
    private void releasePacket() {
        if (!packetLoad.isEmpty()) {
            Packet p = packetLoad.removeLast();
            load -= p.getWeight();
        }
    }

    //// UpdateHandler interface
    
    public void handleUpdate(MoveUpdate update) {
        if (update.getPlayerId() == getId()) {
            setPosition(getPosition().move(update.getDirection()));

            // If my previous command was something other than a move in this
            // direction than I must have been pushed! Shame on you robot! So
            // forget whatever we picked up last.
            if (prevCommand != null &&
                (!(prevCommand instanceof MoveCommand) ||
                 ((MoveCommand) prevCommand).getDirection() != update.getDirection())) {
                releasePacket();
            }
        }

        // A robot has moved, so update our world accordingly.
        assert robots.containsKey(update.getPlayerId());

        if (robots.containsKey(update.getPlayerId())) {
            Robot r = robots.get(update.getPlayerId());

            assert field.getRobot(r.getPosition()) != null;

            field.setRobot(r.getPosition(), null);
            r.setPosition(r.getPosition().move(update.getDirection()));
            field.setRobot(r.getPosition(), r);
        }
    }

    public void handleUpdate(PickUpdate update) {
        if (update.getPlayerId() == getId()) {
            // We should already know about this packet!
            assert packetDict.containsKey(update.getPacketId());

            // We're carrying another packet now...
            packetLoad.add(packetDict.get(update.getPacketId()));
            load += packetDict.get(update.getPacketId()).getWeight();
        } else if (packetDict.containsKey(update.getPacketId())) {
            Packet packet = packetDict.get(update.getPacketId());
            if (packetLoad.contains(packet)) {
                // HA! A player (and it's not us) has just picked up a packet
                // that we thought WE had. Now we know better.
                releasePacket(packet);
            }
        }

        if (robots.containsKey(update.getPlayerId())) {
            Robot robot = robots.get(update.getPlayerId());
            Position pos = robot.getPosition();

            // If we're certain (well, almost) that this square doesn't contain
            // other packets besides this, then remove it from our list of
            // plausible packet sources.
            field.removePacket(pos, update.getPacketId());
            if (visitedMap[pos.getY()][pos.getX()] &&
                !field.hasPackets(pos))
                sourceList.remove(pos);
        }
    }

    public void handleUpdate(DropUpdate update) {
        if (update.getPlayerId() == getId()) {
            packetLoad.remove(packetDict.get(update.getPacketId()));
            load -= packetDict.get(update.getPacketId()).getWeight();

            // We won't be needing a reference to this packet again, so we can
            // safely remove it.
            packetDict.remove(update.getPacketId());
            
            // Each time we drop something, we regain some force. Hence some
            // of the suspended sources might become interesting to us again.
            for (Position p : suspendedSources) {
                for (Packet pkg : field.getPackets(p)) {
                    if (pkg.getWeight() <= (getStrength() - load)) {
                        sourceList.add(p);
                        break;
                    }
                }
            }
            for (Position p : sourceList)
                suspendedSources.remove(p);
        } else {
            Packet packet = packetDict.get(update.getPacketId());
            if (packetLoad.contains(packet)) {
                // HA! A player (and it's not us) has just dropped up a packet
                // that we thought WE had. Now we know better.
                releasePacket(packet);
            }
        }
    }

    public void handleUpdate(AppearUpdate update) {
        if (update.getPlayerId() == getId())
            setPosition(update.getPosition());

        Robot robot = new Robot(update.getPlayerId(), update.getPosition());
        robots.put(update.getPlayerId(), robot);
        field.setRobot(robot.getPosition(), robot);
    }

    public void handleUpdate(KillUpdate update) {
        if (update.getPlayerId() == getId())
            setAlive(false);
        else {
            // A robot is dead (and luckily, it's not us), so remove it from
            // the map and the dictionary.
            if (robots.containsKey(update.getPlayerId())) {
                Robot killed = robots.get(update.getPlayerId());
                field.setRobot(killed.getPosition(), null);
                robots.remove(killed.getId());
            }
        }
    }

    /**
     * Called whenever the server notifies us about the packets on our current
     * location.
     */
    protected void handlePacketsNotify(LinkedList<Packet> packets) {
        for (Packet p : field.getPackets(getPosition()))
            field.removePacket(getPosition(), p.getId());

        for (Packet p : packets) {
            // We don't bother with updating our field with packages whose dest
            // is not reachable.
            if (positionReachable(p.getDestination())) {
                if (!packetDict.containsKey(p.getId()))
                    packetDict.put(p.getId(), p);
							
                field.addPacket(getPosition(), p);

                // A subtlety: if we happen to think we're carrying a packet we
                // were just told we're standing on, something's wrong (i.e. we
                // were pushed by someone and dropped the packet involontarily).
                // So forget all about it.
                releasePacket(p);
            }
        }

        int py = getPosition().getY(), px = getPosition().getX();
        visitedMap[py][px] = true;
    }

    /**
     * Get an appriximate distance of point p from the origin. Fast and
     * innacurate. Used for special occasions only.
     */
    private int fuzzyDistance(Position p) {
        return (Math.abs(getPosition().getY() - p.getY()) +
                Math.abs(getPosition().getX() - p.getX()));
    }

    /**
     * Pick a selection of packets worth picking. We chose the ones with a
     * closer destination, but we always pick as many of them as possible.
     */
    private LinkedList<Integer> computePickList(Packet[] packets) {
        LinkedList<Integer> packetSel = new LinkedList<Integer>();

        if (packets.length == 0)
            return packetSel;

        Arrays.sort(packets,
                    new Comparator<Packet>()
        {
            public int compare(Packet a, Packet b) {
                return (fuzzyDistance(a.getDestination()) -
                        fuzzyDistance(b.getDestination()));

            }
        });

        int extraLoad = load;
        for (Packet p : packets) {
            if (p.getWeight() <= getStrength() - extraLoad) {
                packetSel.add(p.getId());
                extraLoad += p.getWeight();
            }
        }

        return packetSel;
    }

    /**
     * Return the packages that finally arrived at their dests and should
     * be dropped.
     */
    private LinkedList<Integer> computeDropList() {
        LinkedList<Integer> lst = new LinkedList<Integer>();
        for (Packet p : packetLoad) {
            if (p.getDestination().equals(getPosition()))
                lst.add(p.getId());
        }
        return lst;
    }

    private void tryVisit(int y, int x, int cost,
                          int[][] distances, Queue<Position> q) {
        if (distances[y][x] == infinity &&
            field.getType(y, x) != Field.CellType.WATER &&
            field.getType(y, x) != Field.CellType.WALL) {
            q.offer(new Position(y, x));
            distances[y][x] = cost + 1;
        }
    }

    /**
     * Compute the minimum distance form each point to <code>pos</code> using
     * an iterative "gradient fill" algorithm.
     *
     * @param distances array where the relative distances are stored
     * @param pos the source
     */
    private void computeDists(int[][] distances, Position pos) {
        // First, fill the array with inifinity
        for (int i = 0; i < field.getHeight(); i++)
            Arrays.fill(distances[i], infinity);

        // Squares we must still visit
        Queue<Position> toVisit = new LinkedList<Position>();

        // The origin
        toVisit.offer(pos);
        distances[pos.getY()][pos.getX()] = 0;

        // While there are squares to visit...
        while (toVisit.peek() != null) {
            Position p = toVisit.poll();
            int x = p.getX();
            int y = p.getY();
            int cost = distances[y][x];

            if (y + 1 < field.getHeight())
                tryVisit(y + 1, x, cost, distances, toVisit);
            if (y - 1 >= 0)
                tryVisit(y - 1, x, cost, distances, toVisit);
            if (x + 1 < field.getWidth())
                tryVisit(y, x + 1, cost, distances, toVisit);
            if (x - 1 >= 0)
                tryVisit(y, x - 1, cost, distances, toVisit);
        }
    }

    private void ensureDistsComputed() {
        if (!distanceComputed)
            computeDists(sourceDists, getPosition());
        distanceComputed = true;
    }

    private boolean positionReachable(Position p) {
        ensureDistsComputed();
        return sourceDists[p.getY()][p.getX()] != infinity;
    }

    /**
     * Filter out all the bases that are unreacheable to us.
     */
    private void filterSourceList() {
        if (!sourceListFiltered) {
            for (int i = 0; i < sourceList.size(); ++i) {
                Position p = sourceList.get(i);
                if (sourceDists[p.getY()][p.getX()] == infinity) {
                    sourceList.remove(i);
                    --i;
                }
            }
        }
        sourceListFiltered = true;
    }

    /**
     * Choose our next target using several different heuristics (e.g. our
     * current capacity, drop target distance, base distance).
     */
    private void computeNextTarget() {
        computeDists(sourceDists, getPosition());
        distanceComputed = true;

        filterSourceList();

        Position closestSrc = null;
        int srcCost = 0;
        for (Position p : sourceList) {
            int cost = sourceDists[p.getY()][p.getX()];
            if (closestSrc == null || srcCost > cost) {
                srcCost = cost;
                closestSrc = p;
            }
        }

        Position closestDest = null;
        int destCost = 0;
        for (Packet p : packetLoad) {
            Position pos = p.getDestination();
            int cost = sourceDists[pos.getY()][pos.getX()];
            if (closestDest == null || destCost > cost) {
                destCost = cost;
                closestDest = pos;
            }
        }

        // Now we must chose our next target. Here's our (very naive) decision
        // policy:
        //
        //  * if we're over 75% full go ahead and reach the closest dest
        //
        //  * else base our decision on the relative distances AND the load
        //    factor as follows:
        //
        //    if [0.5 + (strength - load) / strength] * dest/source is > 1
        //       go to the closest source, else go to the closest dest
        //
        // This *seems* to work well on average but is nevertheless just our
        // naive estimate!

        if (closestSrc != null && closestDest != null) {
            double d = (double) destCost;
            double s = (double) srcCost;

            double loadFact = (double) getStrength() - load;
            loadFact /= getStrength();
            if (loadFact <= .25)
                target = closestDest;
            else {
                loadFact += .5;
                double crit = (d/s) * loadFact;
                target = (crit > 1) ? closestSrc : closestDest;
            }
            computeDists(targetDists, target);
        } else if (closestDest != null) {
            target = closestDest;
            computeDists(targetDists, target);
        } else if (closestSrc != null ) {
            target = closestSrc;
            computeDists(targetDists, target);
        } else {
            target = findNextScanTarget(getPosition());
            computeDists(targetDists, target);
        }
    }

    /**
     * Compute the "lethality" of a path, that is, try to come with an
     * approximation of it by counting the number of enemies along it.
     */
    private double computePathCost(Position p, int sourceDistance) {
        final int MAX_SEARCH_DEPTH = 10;

        if (sourceDistance > MAX_SEARCH_DEPTH || p.equals(target))
            return 0.0;

        int y = p.getY();
        int x = p.getX();
        int currentDistance = targetDists[y][x];

        double penalty = 0.0;

        int numEnemies = enemiesInRange(p);
        if (numEnemies > 0)
            penalty = ((double) numEnemies) / (sourceDistance);

        Direction[] candidates = new Direction[4];
        int nCandidates = 0;

        if (x > 0 && targetDists[y][x-1] == currentDistance-1)
            candidates[nCandidates++] = Direction.w;

        if (x < field.getWidth()-1 && targetDists[y][x+1] == currentDistance-1)
            candidates[nCandidates++] = Direction.e;

        if (y > 0 && targetDists[y-1][x] == currentDistance-1)
            candidates[nCandidates++] = Direction.n;

        if (y < field.getHeight()-1 && targetDists[y+1][x] == currentDistance-1)
            candidates[nCandidates++] = Direction.s;

        double[] costs = new double[4];
        for (int i = 0; i < nCandidates; ++i)
            costs[i] = computePathCost(p.move(candidates[i]), sourceDistance+1);

        int min = 0;
        for (int i = 1; i < nCandidates; ++i)
            if (costs[i] < costs[min])
                min = i;
        
        return penalty + costs[min];
    }

    private Direction computeDirection() {
        assert target != null;

        int y = getPosition().getY();
        int x = getPosition().getX();

        int currentDistance = targetDists[y][x];

        Direction dir = null;
        double penalty = -1.0;

        //// WEST
        if (x > 0 && targetDists[y][x-1] == currentDistance-1) {
            dir = Direction.w;
            penalty = computePathCost(getPosition().move(dir), 1);
        }
        
        //// EAST
        if (x < field.getWidth()-1 && targetDists[y][x+1] == currentDistance-1) {
            double cost = computePathCost(getPosition().move(Direction.e), 1);
            if (dir == null || cost < penalty) {
                dir = Direction.e;
                penalty = cost;
            }
        }
				
        //// NORTH
        if (y > 0 && targetDists[y-1][x] == currentDistance-1) {
            double cost = computePathCost(getPosition().move(Direction.n), 1);
            if (dir == null || cost < penalty) {
                dir = Direction.n;
                penalty = cost;
            }
        }

        //// SOUTH
        if (y < field.getHeight()-1 && targetDists[y+1][x] == currentDistance-1) {
            double cost = computePathCost(getPosition().move(Direction.s), 1);
            if (dir == null || cost < penalty) {
                dir = Direction.s;
                penalty = cost;
            }
        }

        assert dir != null;
        return dir;
    }

    private boolean inFieldBounds(Position p) {
        return inFieldBounds(p.getY(), p.getX());
    }

    private boolean inFieldBounds(int y, int x) {
        return (x >= 0 && x < field.getWidth() &&
                y >= 0 && y < field.getHeight());
    }

    private int enemiesInRange(Position pos) {
        int y = pos.getY(), x = pos.getX();

        Position[] threshold = {
            new Position(y-2, x),
            new Position(y-1, x-1),
            new Position(y-1, x),
            new Position(y-1, x+1),
            new Position(y,   x-2),
            new Position(y,   x-1),
            new Position(y,   x),
            new Position(y,   x+1),
            new Position(y,   x+2),
            new Position(y+1, x-1),
            new Position(y+1, x),
            new Position(y+1, x+1),
            new Position(y+2, x),
        };

        int count = 0;

        for (Position p : threshold)
            if (inFieldBounds(p) &&
                field.getRobot(p) != null &&
                field.getRobot(p).getId() != getId())
                count += 1;

        return count;
    }

    /**
     * Decide on a Command for this turn.
     */
    protected Command issueCommand() {

        // First, if we have something to drop here, do it!
        LinkedList<Integer> pkgToDrop = computeDropList();
        if (pkgToDrop.size() != 0)
            return (prevCommand = new DropCommand(getId(),
                                                  detractMoney(1),
                                                  pkgToDrop));

        // Nothing to drop; see if there's something to pick up.
        if (!field.hasPackets(getPosition())) {
            sourceList.remove(getPosition());
        } else {
            Packet[] packets = field.getPackets(getPosition());
            LinkedList<Integer> shopList = computePickList(packets);

            if (!shopList.isEmpty())
                return (prevCommand = new PickCommand(getId(),
                                                      detractMoney(1),
                                                      shopList));
            else {
                if (sourceList.contains(getPosition())) {
                    sourceList.remove(getPosition());
                    suspendedSources.add(getPosition());
                }
            }
        }

        // Nothing to pick or drop. If we have a current target proceed,
        // otherwise find one.

        if (target == null || target.equals(getPosition()))
            computeNextTarget();

        if (target == null || target.equals(getPosition()))
            // Note: this sould never really happen, but if it does...
            // we would rather not die.
            return issueIdleCommand();
        else
            return (prevCommand = new MoveCommand(getId(),
                                                  detractMoney(1),
                                                  computeDirection()));
    }

    /**
     * Returns a command that is certain not to have any results.
     *
     * This should NEVER really be called, but if we really must, it's good
     * to have it handy.
     */
    private Command issueIdleCommand() {
        LinkedList<Integer> lst = new LinkedList<Integer>();
        int bogus = 0;
        while (packetDict.containsKey(bogus))
            bogus++;
        lst.add(bogus);
        return new DropCommand(getId(), detractMoney(1), lst);
    }

    //// Scanning code
    //
    // On the sad circumstances when it seems there is absolutely nothing
    // more intelligent to do then wander around the world aimlessly, we 
    // choose not to wander but rather scan the entire field for packages.
    //
    // We do so in a mostly straithforward way that is particularly suited
    // to the kinds of worlds we tipically deal with -- rectangular and
    // sparse. In such cases the robot visits a square only once _on
    // average_. Also, we try to avoid recomputing the target often (a
    // very expensive operation).

    private void inverseScanningDirection() {
        if (scanningDir == Direction.e)
            scanningDir = Direction.w;
        else
            scanningDir = Direction.e;
    }

    /**
     * Find the rightmost position that is reachable from p by moving right
     * (surprise) a number of squares, or, if no such location exists (i.e.
     * p is just left to an uncrossable or lethal square) find the first
     * crossable square. If that doesn't exist either, return null.
     */
    private Position scanForward(Position p) {
        int fx = -1;

        for (int x = p.getX() + 1; x < field.getWidth(); ++x) {
            if (sourceDists[p.getY()][x] == infinity)
                break;
            fx = x;
        }
        if (fx != -1)
            return new Position(p.getY(), fx);

        for (int x = p.getX() + 1; x < field.getWidth(); ++x)
            if (sourceDists[p.getY()][x] != infinity) {
                fx = x;
                break;
            }
        if (fx != -1)
            return new Position(p.getY(), fx);

        return null;
    }

    private Position scanBackward(Position p) {
        int fx = -1;

        for (int x = p.getX() - 1; x >= 0; --x) {
            if (sourceDists[p.getY()][x] == infinity)
                break;
            fx = x;
        }
        if (fx != -1)
            return new Position(p.getY(), fx);

        for (int x = p.getX() - 1; x >= 0; --x)
            if (sourceDists[p.getY()][x] != infinity) {
                fx = x;
                break;
            }
        if (fx != -1)
            return new Position(p.getY(), fx);

        return null;
    }

    /**
     * Finds the first (leftmost) crossable sqaure for row <tt>y</tt> or null
     * if no such a square exists.
     */
    private Position findFirstScanColForward(int y) {
        for (int x = 0; x < field.getWidth(); ++x)
            if (sourceDists[y][x] != infinity)
                return new Position(y, x);
        return null;
    }

    private Position findFirstScanColBackward(int y) {
        for (int x = field.getWidth() - 1; x >= 0; --x)
            if (sourceDists[y][x] != infinity)
                return new Position(y, x);
        return null;
    }

    private Position findFirstScanCol(int y) {
        if (scanningDir == Direction.e) {
            Position pos = findFirstScanColForward(y);
            if (pos != null)
                return pos;
        } else {
            Position pos = findFirstScanColBackward(y);
            if (pos != null)
                return pos;
        }
        return null;
    }

    /**
     * Calculate where should our map scanning proceed. ALWAYS returns a
     * valid location on the map.
     */
    private Position findNextScanTarget(Position p) {
        ensureDistsComputed();

        if (scanningDir == Direction.e) {
            Position pos = scanForward(p);
            if (pos != null)
                return pos;
        } else {
            Position pos = scanBackward(p);
            if (pos != null)
                return pos;
        }

        int y = p.getY() + 1;
        while (true) {
            if (y >= field.getHeight())
                y = 0;
            inverseScanningDirection();

            Position next = findFirstScanCol(y);
            if (next != null)
                return next;
            y += 1;
        }
    }

    protected void parseField(List<String> rows, int width, int height)
        throws InvalidBoardException
    {
        for (String row : rows)
            if (row.length() != width)
                throw new InvalidBoardException("unexpected board width");

        field = new Field(rows);

        for (int y = 0; y < field.getHeight(); y++)
            for (int x = 0; x < field.getWidth(); x++)
                if (field.getType(new Position(y, x)) == Field.CellType.BASE)
                    sourceList.add(new Position(y, x));

        infinity = field.getWidth() * field.getHeight();

        targetDists = new int[field.getHeight()][field.getWidth()];
        sourceDists = new int[field.getHeight()][field.getWidth()];

        visitedMap = new boolean[field.getHeight()][field.getWidth()];
    }

    private static void printUsageAndQuit(int exitStatus) {
        PrintStream out;
        if (exitStatus == EXIT_STATUS_SUCCESS)
            out = System.out;
        else
            out = System.err;

        out.println("usage: java it.unibo.cs.csrobots.Player [OPTION]...");
        out.println("\t-address\tthe server address [default=localhost]");
        out.println("\t-port\t\tserver port [default=7919]");

        System.exit(exitStatus);
    }

    public static void main(String[] args) {
        String serverAddr = defaultServerAddr;
        int serverPort = DEFAULT_SERVER_PORT;

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
        
        Player player = null;

        try {
            player = new Player(serverPort, serverAddr);
        } catch (StubException e) {
            System.err.printf("error: could not connect to `%s:%d' [%s]\n",
                              serverAddr, serverPort, e.getMessage());
            //e.printStackTrace();
            System.exit(EXIT_STATUS_FAILURE);
        }

        try {
            player.play();
        } catch (Exception e) {
            System.err.printf("error: an unexpected exception occured [%s]\n", e.getMessage());
            System.err.printf("stack trace follows:\n");
            e.printStackTrace();
        }
    }
}
