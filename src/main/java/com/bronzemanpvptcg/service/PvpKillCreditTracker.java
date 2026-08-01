package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.OsrsTcgConfig;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * Awards credits for PvP kills — the only external credit source. Normally one kill pays for exactly
 * one standard booster pack; in hard mode the kill pays by loot value instead
 * ({@value #HARD_MODE_CREDITS_PER_CHUNK} credits per {@value #HARD_MODE_LOOT_CHUNK} GP).
 * {@link PlayerLootReceived} fires once when the local player's kill drops a loot pile (wilderness,
 * PvP/Bounty Hunter worlds, LMS); safe activities without loot piles (Clan Wars, duels) award nothing.
 */
@Singleton
public final class PvpKillCreditTracker
{
	private static final long FALLBACK_PACK_PRICE = 2_500L;
	/** Hard mode pays pro rata, so this is a rate rather than a threshold. */
	private static final long HARD_MODE_LOOT_CHUNK = 100_000L;
	private static final long HARD_MODE_CREDITS_PER_CHUNK = 250L;

	private final PackCatalog packCatalog;
	private final CreditAwardService creditAwardService;
	private final ChatMessageManager chatMessageManager;
	private final ItemManager itemManager;
	private final TcgStateService stateService;
	private final OsrsTcgConfig config;
	private final TcgPanel tcgPanel;

	@Inject
	public PvpKillCreditTracker(
		PackCatalog packCatalog,
		CreditAwardService creditAwardService,
		ChatMessageManager chatMessageManager,
		ItemManager itemManager,
		TcgStateService stateService,
		OsrsTcgConfig config,
		TcgPanel tcgPanel)
	{
		this.packCatalog = packCatalog;
		this.creditAwardService = creditAwardService;
		this.chatMessageManager = chatMessageManager;
		this.itemManager = itemManager;
		this.stateService = stateService;
		this.config = config;
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

		if (config.hardMode())
		{
			awardHardMode(event, victimName);
			return;
		}

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

	/**
	 * Always reports the kill, even when the loot prices out at nothing — an untradeable-only pile
	 * would otherwise look identical to the award never firing at all.
	 */
	private void awardHardMode(PlayerLootReceived event, String victimName)
	{
		long lootValue = lootValue(event);
		long credits = lootValue * HARD_MODE_CREDITS_PER_CHUNK / HARD_MODE_LOOT_CHUNK;
		boolean awarded = credits > 0L && creditAwardService.awardPvpKillCredits(victimName, credits);

		if (awarded)
		{
			TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
				"PvP kill on %s: %s loot -> +%s credits (%s / %s toward a pack).",
				victimName,
				NumberFormatting.format(lootValue),
				NumberFormatting.format(credits),
				NumberFormatting.format(stateService.getCredits()),
				NumberFormatting.format(standardPackPrice())));
			tcgPanel.refresh();
			return;
		}

		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"PvP kill on %s: %s loot -> no credits (hard mode pays %s per %s of tradeable loot).",
			victimName,
			NumberFormatting.format(lootValue),
			NumberFormatting.format(HARD_MODE_CREDITS_PER_CHUNK),
			NumberFormatting.format(HARD_MODE_LOOT_CHUNK)));
	}

	/** Debug hook ({@code ::btcg-pvp}): runs the real award path against a made-up loot value. */
	public void simulateKill(long lootValue)
	{
		if (!config.hardMode())
		{
			long packPrice = standardPackPrice();
			boolean ok = creditAwardService.awardPvpKillCredits("a test target", packPrice);
			TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, ok
				? String.format("Test kill: +%s credits (normal mode pays one pack per kill).",
					NumberFormatting.format(packPrice))
				: "Test kill: award blocked (credit cooldown active — try again in a few ticks).");
			tcgPanel.refresh();
			return;
		}

		long credits = Math.max(0L, lootValue) * HARD_MODE_CREDITS_PER_CHUNK / HARD_MODE_LOOT_CHUNK;
		boolean ok = credits > 0L && creditAwardService.awardPvpKillCredits("a test target", credits);
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"Test kill: %s loot -> %s%s credits. Total %s / %s for a pack.",
			NumberFormatting.format(Math.max(0L, lootValue)),
			ok ? "+" : "",
			NumberFormatting.format(ok ? credits : 0L),
			NumberFormatting.format(stateService.getCredits()),
			NumberFormatting.format(standardPackPrice())));
		tcgPanel.refresh();
	}

	/** Grand Exchange value of the loot pile the kill dropped. */
	private long lootValue(PlayerLootReceived event)
	{
		if (event.getItems() == null)
		{
			return 0L;
		}

		long total = 0L;
		for (ItemStack item : event.getItems())
		{
			if (item == null || item.getQuantity() <= 0)
			{
				continue;
			}
			total += (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
		}
		return total;
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
