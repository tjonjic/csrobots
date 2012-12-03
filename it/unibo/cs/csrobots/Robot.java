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

import java.util.LinkedHashMap;
import java.util.LinkedList;

public class Robot extends GameObject {
    /** A robot's strength (immutable). */
    private final int strength;

    /** Its wealth, for making bids... */
    private int money;

    /** At any time, a robot has a valid position. */
    private Position position;

    private boolean idle = false;

    private int currentLoad = 0;

    public int getCurrentLoad() { return currentLoad; }

    private LinkedHashMap<Integer, Packet> packetLoadMap;
    private LinkedList<Packet> packetLoad;

    public Robot(int id, int strength, int money, Position position) {
        super(id);

        this.strength = strength;

        this.money = (money < 0) ? 0 : money;

        this.position = position;

        packetLoad = new LinkedList<Packet>();
        packetLoadMap = new LinkedHashMap<Integer, Packet>();
    }

    public Robot(int id, int strength, int money) {
        this(id, strength, money, null);
    }

    public Robot(int id, Position position) {
        this(id, -1, -1, position);
    }

    public boolean isIdle() {
        return idle;
    }

    public void setIdle(boolean idle) {
        this.idle = idle;
    }

    public int getStrength()  {
        return strength;
    }

    public int getMoney() {
        return money;
    }

    public void detractMoney(int amount) {
        money -= amount;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Packet dropLast() {
        try {
            Packet tmp = packetLoad.removeLast();
            currentLoad -= tmp.getWeight();
            packetLoadMap.remove(tmp.getId());
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    public Packet drop(int packetId) {
        if (packetLoadMap.containsKey(packetId)) {
            Packet tmp = packetLoadMap.get(packetId);
            currentLoad -= tmp.getWeight();
            packetLoad.remove(tmp);
            packetLoadMap.remove(packetId);
            return tmp;
        } else
            return null;
    }

    public void pick(Packet packet) {
        packetLoad.add(packet);
        packetLoadMap.put(packet.getId(), packet);
        currentLoad += packet.getWeight();
    }

    public String toString() {
        return String.format("%s [id=%d,strength=%d,money=%d,position=(%d,%d)]",
                             getClass().getName(),
                             getId(),
                             strength, money,
                             position.getY(), position.getX());
    }
}
