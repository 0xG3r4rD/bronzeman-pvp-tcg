package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.OsrsTcgConfig;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.model.DefenceLevel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Roll pool is the loaded catalog minus anything the configured {@link DefenceLevel} cannot equip;
 * quest-only item rows are omitted at {@code Card.json} build time.
 */
@Singleton
public final class RollPoolFilter
{
	private final OsrsTcgConfig config;

	@Inject
	public RollPoolFilter(OsrsTcgConfig config)
	{
		this.config = config;
	}

	public List<CardDefinition> filterRollPool(List<CardDefinition> cards)
	{
		if (cards == null || cards.isEmpty())
		{
			return List.of();
		}

		DefenceLevel level = config.defenceLevel();
		boolean consumables = config.consumableCards();
		boolean unrestricted = level == null || level.isUnrestricted();
		if (unrestricted && consumables)
		{
			return cards;
		}

		int cap = unrestricted ? Integer.MAX_VALUE : level.getMaxRequirement();
		List<CardDefinition> allowed = new ArrayList<>(cards.size());
		for (CardDefinition card : cards)
		{
			if (card == null || card.defenceRequirementLevel() > cap)
			{
				continue;
			}
			// A consumable card is dead weight unless its lock is switched on.
			if (card.isConsumableCard() && !consumables)
			{
				continue;
			}
			allowed.add(card);
		}
		return List.copyOf(allowed);
	}
}
