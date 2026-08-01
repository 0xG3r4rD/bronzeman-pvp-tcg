package com.bronzemanpvptcg.model;

/**
 * Self-imposed Defence cap for the card pool. Cards whose item needs more Defence than this to
 * equip are removed from the roll pool, so they can never be pulled from a pack.
 */
public enum DefenceLevel
{
	LEVEL_1("1 Defence", 1),
	LEVEL_20("20 Defence", 20),
	LEVEL_30("30 Defence", 30),
	LEVEL_40("40 Defence", 40),
	LEVEL_50("50 Defence", 50),
	LEVEL_60_PLUS("60+ Defence", Integer.MAX_VALUE);

	private final String label;
	private final int maxRequirement;

	DefenceLevel(String label, int maxRequirement)
	{
		this.label = label;
		this.maxRequirement = maxRequirement;
	}

	/** Highest {@code defenceRequirement} a card may carry and still be pullable. */
	public int getMaxRequirement()
	{
		return maxRequirement;
	}

	/** True when every card qualifies, so the pool needs no filtering pass. */
	public boolean isUnrestricted()
	{
		return maxRequirement == Integer.MAX_VALUE;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
