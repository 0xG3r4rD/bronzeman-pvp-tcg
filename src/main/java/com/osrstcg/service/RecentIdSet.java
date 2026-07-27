package com.osrstcg.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remembers recently processed party-message ids for replay protection, evicting the
 * oldest id once over capacity. A wholesale clear would also forget the newest ids,
 * reopening the replay window this set exists to close.
 */
final class RecentIdSet
{
	private final Set<String> ids;

	RecentIdSet(int capacity)
	{
		ids = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>()
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest)
			{
				return size() > capacity;
			}
		});
	}

	/** @return false if the id was already present, i.e. a replayed message. */
	synchronized boolean add(String id)
	{
		return ids.add(id);
	}

	/** True if the id was already recorded, without inserting it. */
	synchronized boolean contains(String id)
	{
		return ids.contains(id);
	}

	/** Forget an id whose message failed, so a retried message is not treated as a replay. */
	synchronized void remove(String id)
	{
		ids.remove(id);
	}
}
