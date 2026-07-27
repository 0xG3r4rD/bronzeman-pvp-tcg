package com.osrstcg.service;

import org.junit.Assert;
import org.junit.Test;

/**
 * Replay protection for party messages: the id set must reject duplicates while full,
 * evicting only the oldest entries, never the recent ones a replayed message would reuse.
 */
public class RecentIdSetTest
{
	@Test
	public void duplicateIdIsRejected()
	{
		RecentIdSet ids = new RecentIdSet(600);
		Assert.assertTrue(ids.add("msg-1"));
		Assert.assertFalse(ids.add("msg-1"));
	}

	@Test
	public void containsReportsMembershipWithoutInserting()
	{
		RecentIdSet ids = new RecentIdSet(600);
		Assert.assertFalse(ids.contains("msg-1"));
		ids.add("msg-1");
		Assert.assertTrue(ids.contains("msg-1"));
		// A bare contains() must not record the id, so a later add() still counts as first-seen.
		Assert.assertFalse(ids.contains("msg-2"));
		Assert.assertTrue(ids.add("msg-2"));
	}

	@Test
	public void evictsOldestFirstWhenFull()
	{
		RecentIdSet ids = new RecentIdSet(3);
		ids.add("a");
		ids.add("b");
		ids.add("c");
		ids.add("d");
		Assert.assertFalse("recent ids must survive eviction", ids.add("b"));
		Assert.assertFalse(ids.add("c"));
		Assert.assertFalse(ids.add("d"));
		Assert.assertTrue("oldest id should have been evicted", ids.add("a"));
	}

	/** Regression: the old "empty the whole set on overflow" eviction forgot every recent id at once. */
	@Test
	public void recentIdsStillRejectedAfterOverflow()
	{
		RecentIdSet ids = new RecentIdSet(600);
		for (int i = 0; i < 601; i++)
		{
			ids.add("msg-" + i);
		}
		Assert.assertFalse("just-processed id must still be deduplicated", ids.add("msg-600"));
		Assert.assertFalse(ids.add("msg-599"));
		Assert.assertTrue("only the oldest id should be evicted", ids.add("msg-0"));
	}

	@Test
	public void removedIdCanBeAddedAgain()
	{
		RecentIdSet ids = new RecentIdSet(600);
		ids.add("msg-1");
		ids.remove("msg-1");
		Assert.assertTrue("removed id must be re-addable so a failed message can retry", ids.add("msg-1"));
	}
}
