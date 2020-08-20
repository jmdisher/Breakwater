package com.jeffdisher.breakwater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A multi-map utility class intended for use around HTTP calls, so all keys are strings.  Note that it cannot contain
 * null keys or values.
 * 
 * This is distinct from the Jetty MultiMap since exposing a utility from an underlying library seemed like exposing an
 * implementation detail and that implementation directly extends HashMap, which makes for an easily misused interface.
 */
public class StringMultiMap<T> {
	private final Map<String, List<T>> _map = new HashMap<>();
	private int _valueCount;

	/**
	 * @return The number of values in the map (includes multiple values for a given key).
	 */
	public int valueCount() {
		return _valueCount;
	}

	/**
	 * Adds the given key-value pair to the map, creating the key if this is the first value or appending the value to
	 * the existing list of values.
	 * 
	 * @param key The key used to store the value.
	 * @param value The value to store.
	 */
	public void append(String key, T value) {
		if (null == key) {
			throw new NullPointerException();
		}
		if (null == value) {
			throw new NullPointerException();
		}
		List<T> list = _map.get(key);
		if (null == list) {
			list = new ArrayList<T>();
			_map.put(key, list);
		}
		list.add(value);
		_valueCount += 1;
	}

	/**
	 * @return The set of entries mapping the key to lists of values.
	 */
	public Set<Map.Entry<String, List<T>>> entrySet() {
		return Collections.unmodifiableSet(_map.entrySet());
	}

	/**
	 * A common idiom is the need to require precisely one element.  To handle this common case, this helper will return
	 * the value for the key if exactly 1 value was given for the key.  It returns null if there is no such value or if
	 * there are more than 1.
	 * 
	 * @param key The key to look up.
	 * @return The value for this key if there is only 1, otherwise null.
	 */
	public T getIfSingle(String key) {
		if (null == key) {
			throw new NullPointerException();
		}
		List<T> list = _map.get(key);
		return ((null != list) && (1 == list.size()))
				? list.get(0)
				: null;
	}
}
