package com.jeffdisher.breakwater.paths;


/**
 * Used by the server to interpret paths.  This allows for variables of different types to occur within a URL path.
 */
public interface IPathParser
{
	/**
	 * The parser which attempts to interpret the given raw string as whatever special type it uses.
	 * 
	 * @param raw The string representation of the path component.
	 * @return The value or null, if it can't interpret this data.
	 * @throws Throwable Thrown as another type of failure if the input can't be interpreted.
	 */
	Object parse(String raw) throws Throwable;
}
