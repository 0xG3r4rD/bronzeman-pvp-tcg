package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.data.BoosterPackDefinition;
import com.bronzemanpvptcg.data.PackCatalog;
import com.bronzemanpvptcg.ui.TcgPanel;
import com.bronzemanpvptcg.util.NumberFormatting;
import com.bronzemanpvptcg.util.TcgPluginGameMessages;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Player;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;

/**
 * Awards credits for PvP kills — the only external credit source: one kill pays for exactly one
 * standard booster pack. {@link PlayerLootReceived} fires once when the local player's kill drops a
 * loot pile (wilderness, PvP/Bounty Hunter worlds, LMS); safe activities without loot piles
 * (Clan Wars, Castle Wars, duels) award nothing.
 */
@Singleton
public final class PvpKillCreditTracker
{
	private static final long FALLBACK_PACK_PRICE = 2_500L;

	private final PackCatalog packCatalog;
	private final CreditAwardService creditAwardService;
	private final ChatMessageManager chatMessageManager;
	private final TcgPanel tcgPanel;

	@Inject
	public PvpKillCreditTracker(
		PackCatalog packCatalog,
		CreditAwardService creditAwardService,
		ChatMessageManager chatMessageManager,
		TcgPanel tcgPanel)
	{
		this.packCatalog = packCatalog;
		this.creditAwardService = creditAwardService;
		this.chatMessageManager = chatMessageManager;
		this.tcgPanel = tcgPanel;
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (event == null || event.getPlayer() == null)
		{
			return;
		}

		Player victim = event.getPlayer();
		String victimName = victim.getName() == null || victim.getName().trim().isEmpty()
			? "another player"
			: victim.getName().trim();

		long packPrice = standardPackPrice();
		if (!creditAwardService.awardPvpKillCredits(victimName, packPrice))
		{
			return;
		}

		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"PvP kill on %s: +%s credits — enough for a booster pack!",
			victimName, NumberFormatting.format(packPrice)));
		tcgPanel.refresh();
	}

	private long standardPackPrice()
	{
		for (BoosterPackDefinition booster : packCatalog.getBoosters())
		{
			if (booster != null && !booster.isDebugOnly() && booster.getPrice() > 0)
			{
				return booster.getPrice();
			}
		}
		return FALLBACK_PACK_PRICE;
	}
}
