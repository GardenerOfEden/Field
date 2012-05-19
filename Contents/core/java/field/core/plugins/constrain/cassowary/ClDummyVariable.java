// $Id: ClDummyVariable.java,v 1.9 1999/04/20 00:26:27 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This Java Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
//
// ClDummyVariable
// 
package field.core.plugins.constrain.cassowary;

class ClDummyVariable extends ClAbstractVariable {
	// friend ClTableau;
	// friend ClSimplexSolver;
	public ClDummyVariable(String name) {
		super(name);
	}

	public ClDummyVariable() {
	}

	public ClDummyVariable(long number, String prefix) {
		super(number, prefix);
	}

	@Override
	public String toString() {
		return "[" + name() + ":dummy]";
	}

	@Override
	public boolean isDummy() {
		return true;
	}

	@Override
	public boolean isExternal() {
		return false;
	}

	@Override
	public boolean isPivotable() {
		return false;
	}

	@Override
	public boolean isRestricted() {
		return true;
	}
}