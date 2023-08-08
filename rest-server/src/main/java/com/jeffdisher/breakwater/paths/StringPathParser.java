package com.jeffdisher.breakwater.paths;


/**
 * The path parser which will match any provided data as a raw string.
 * This is the most basic of all "variable-type parsers" since it never fails.
 */
public class StringPathParser implements IPathParser
{
	@Override
	public Object parse(String raw) throws Throwable
	{
		return raw;
	}
}
