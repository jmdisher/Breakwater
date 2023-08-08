package com.jeffdisher.breakwater.paths;


/**
 * A path parser which just matches on a string constant.
 * This is the "default parser" when a variable type isn't mentioned as it will just match on the literal path component.
 */
public class ConstantPathParser implements IPathParser
{
	private final String _match;

	public ConstantPathParser(String match)
	{
		_match = match;
	}

	@Override
	public Object parse(String raw) throws Throwable
	{
		return _match.equals(raw)
				? raw
				: null
		;
	}
}
