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

public class ServerConnection {
	private static final String localhost = "127.0.0.1";

	private Socket suck = null;
	private Stub stub = null;

	/**
	 * Create a connection to a server listening on a given address,port
	 * pair
	 * @param address IP address where the server is listening
	 * @param port TCP port where the server is listening
	 */
	public ServerConnection(int port, String address) throws StubException {
		try {
			this.suck = new Socket(address, port);
		} catch (UnknownHostException e) {
			throw new StubException(address, port);
		} catch (IOException e) {
			throw new StubException(address, port);
		}
	}

	/**
	 * Create a connection to a server listening on a given port of the
	 * local machine (IP address 127.0.0.1)
	 * @param port TCP port where the server is listening
	 */
	public ServerConnection(int port) throws StubException {
		try {
			this.suck = new Socket(localhost, port);
		} catch (IOException e) {
			throw new StubException(localhost, port);
		}
	}

	/**
	 * @return a stub for talking with the server this instance is connected
	 * to (singleton method, multiple invocation will return the very same
	 * stub)
	 */
	public Stub getStub() throws StubException {
		if (stub == null) {
			stub = new Stub(suck);
		}
		return stub;
	}
}

