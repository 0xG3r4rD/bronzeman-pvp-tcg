package com.bronzemanpvptcg.ui.trade;

import com.bronzemanpvptcg.service.CardPartyTradeService.TradeOfferView;
import com.bronzemanpvptcg.service.TradeViewFixtures;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@code hasFoilOffer} is the gate the foil animation timer uses to decide whether an offer
 * grid still needs 60fps repaints; non-foil trades would otherwise redraw both sides for nothing.
 */
public class TradeWindowFoilGateTest
{
	@Test
	public void hasFoilOfferIsFalseWhenNoOfferedCardIsFoil()
	{
		List<TradeOfferView> offers = List.of(
			TradeViewFixtures.offer("Abyssal whip", false),
			TradeViewFixtures.offer("Dragon scimitar", false));

		Assert.assertFalse(TradeWindow.hasFoilOffer(offers));
	}

	@Test
	public void hasFoilOfferIsTrueWhenAnyOfferedCardIsFoil()
	{
		List<TradeOfferView> offers = List.of(
			TradeViewFixtures.offer("Abyssal whip", false),
			TradeViewFixtures.offer("Twisted bow", true));

		Assert.assertTrue(TradeWindow.hasFoilOffer(offers));
	}

	@Test
	public void hasFoilOfferIsFalseWhenNothingIsOffered()
	{
		Assert.assertFalse(TradeWindow.hasFoilOffer(List.of()));
	}
}
