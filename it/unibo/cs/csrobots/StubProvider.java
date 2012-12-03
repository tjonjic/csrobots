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

import it.unibo.cs.csrobots.*;

/**
 * Implementing classes are able to provide stubs on demand.
 */
public interface StubProvider {
	Stub getStub() throws StubException;
}

