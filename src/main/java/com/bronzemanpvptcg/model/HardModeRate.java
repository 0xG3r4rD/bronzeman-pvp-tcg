package com.bronzemanpvptcg.model;

/** How hard mode converts PvP loot value into credits. */
public enum HardModeRate
{
	DEFAULT("Default (400 gp per point)"),
	CUSTOM("Custom");

	/** Loot value that buys one credit under {@link #DEFAULT} — 250 credits per 100,000 gp. */
	public static final int DEFAULT_GP_PER_POINT = 400;

	private final String label;

	HardModeRate(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
