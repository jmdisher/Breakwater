package com.jeffdisher.breakwater.utilities;


/**
 * Assertion utility class.
 * Added here since we don't want Breakwater to depend on Laminar to access com.jeffdisher.laminar.utils.Assert, as used
 * by Membrane (which arguably should use its own copy, anyway).
 */
public class Assert {
	public static void assertTrue(boolean flag) {
		if (!flag) {
			throw new AssertionError("Condition must be true");
		}
	}

	public static AssertionError unexpected(Throwable t) {
		throw new AssertionError("Unexpected Throwable", t);
	}
}
