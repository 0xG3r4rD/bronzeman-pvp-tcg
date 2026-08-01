package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.service.CardPartyTradeService.TradeOfferView;

/** Builds {@link TradeOfferView} instances for tests outside this package. */
public final class TradeViewFixtures
{
	private TradeViewFixtures()
	{
	}

	public static TradeOfferView offer(String cardName, boolean foil)
	{
		return new TradeOfferView("test-" + cardName, cardName, foil, "Player", 1710000000000L);
	}
}
