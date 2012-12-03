// Copyright (C) 2006, Stefano Zacchiroli
// 
// This file is part of CSRobots, the assignment of Laboratorio Metodi di
// Programmazione for the Academic Year 2005/2006.
// 
// CSRobots is free software; you can redistribute it and/or modify it under
// the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version.
// 
// CSRobots is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
// details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
// 
// For details, see the CSRobots World-Wide-Web page,
// http://mowgli.cs.unibo.it/~zacchiro/cgi-bin/moin.cgi/ProgettoLab10506

package it.unibo.cs.csrobots;

import java.io.*;
import java.net.*;

import it.unibo.cs.csrobots.*;

public class PlayersGreeter implements StubProvider {
	private static final int backlog = 10;	// listen backlog
	private static final String localhost = "127.0.0.1";

	private ServerSocket suck = null;	// listening socket

	/**
	 * @return a new greater which will wait for player connections on
	 * demand
	 * @param address String representation of the IP address of the local
	 * machine on which the greeter will accept connections
	 * @param port TCP port of the local machine on which the greeter will
	 * accept connections
	 */
	public PlayersGreeter(int port, String address) throws StubException {
		try {
			this.suck = new ServerSocket(port, backlog,
					InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			throw new StubException(address, port);
		} catch (IOException e) {
			throw new StubException(address, port);
		}
	}

	/** @return a new greater which will wait for player connections on
	 * localhost (IP address 127.0.0.1) on a gvin port.
	 * @param port TCP port of the local machine on which the greeter will
	 * accept connections
	 */
	public PlayersGreeter(int port) throws StubException {
		this(port, localhost);
	}

	/**
	 * Wait for a player connection and create a stub for talking with it
	 * @return a stub for the connected player
	 */
	public Stub getStub() throws StubException {
		try {
			return new Stub(suck.accept());
		} catch (IOException e) {
			throw new StubException("can't accept player");
		}
	}
}

