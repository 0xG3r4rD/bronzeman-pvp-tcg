package com.bronzemanpvptcg.model;

public final class EconomyState
{
	/** One standard booster pack (Packs.json price) so a fresh profile can open its first pack. */
	private static final long STARTING_CREDITS = 2_500L;

	private final long credits;
	private final long openedPacks;

	public EconomyState(long credits, long openedPacks)
	{
		this.credits = Math.max(0L, credits);
		this.openedPacks = Math.max(0L, openedPacks);
	}

	public static EconomyState empty()
	{
		return new EconomyState(STARTING_CREDITS, 0L);
	}

	public long getCredits()
	{
		return credits;
	}

	public long getOpenedPacks()
	{
		return openedPacks;
	}
}
