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

/**
 * Stub for talking with a peer over the net (using simple TCP sockets)
 */
public class Stub {
	private Socket suck = null;
	private BufferedReader in = null;
	private PrintWriter out = null;

	/**
	 * Create a stub from a connected TCP socket
	 */
	public Stub(Socket suck) throws StubException {
		try {
			this.suck = suck;
			this.in = new BufferedReader(new InputStreamReader(
						suck.getInputStream()));
			this.out = new PrintWriter(suck.getOutputStream(),
					true);
		} catch (IOException e) {
			throw new StubException("can't create I/O end points");
		}
	}

	/**
	 * @return the underlying connected TCP socket
	 */
	public Socket getSocket() {
		return suck;
	}

	/**
	 * @return the reader part of the stub, use it to receive lines of text
	 * from the peer
	 */
	public BufferedReader getReader() {
		return in;
	}

	/** @return the writer part of the stub, use it to send lines of text to
	 * the peer. The writer has autoflush on, each invocation of a "println"
	 * method triggers flushing.
	 */
	public PrintWriter getWriter() {
		return out;
	}
}

