package com.osrstcg.service;

import com.osrstcg.data.CardDatabase;
import com.osrstcg.data.CardDefinition;
import com.osrstcg.util.TcgPluginGameMessages;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Bronzeman mode (ported from the bronzeman-tcg companion plugin, reading the collection
 * in-process instead of through its cross-plugin snapshot): equipping an item is blocked until
 * the matching TCG card is owned. Items with no card in the catalog are never restricted —
 * they could never be unlocked. Tutorial Island (it supplies required gear) and live LMS
 * matches (temporary loadouts the player doesn't own) bypass enforcement.
 */
@Singleton
public final class BronzemanEquipLockService
{
	private static final Set<String> EQUIP_VERBS = Set.of("wear", "wield", "equip");

	/** "Amulet of glory(4)", "Games necklace(8)" -> cards carry no charge suffix. */
	private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\s*\\(\\d+\\)$");
	/** "Dharok's helm 100" -> Barrows degradation states share the undegraded card. */
	private static final Pattern DEGRADATION_SUFFIX = Pattern.compile("\\s+\\d+$");

	private final Client client;
	private final ItemManager itemManager;
	private final CardDatabase cardDatabase;
	private final TcgStateService stateService;
	private final ChatMessageManager chatMessageManager;

	@Inject
	public BronzemanEquipLockService(
		Client client,
		ItemManager itemManager,
		CardDatabase cardDatabase,
		TcgStateService stateService,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.cardDatabase = cardDatabase;
		this.stateService = stateService;
		this.chatMessageManager = chatMessageManager;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (isEnforcementBypassed())
		{
			return;
		}

		MenuAction action = event.getMenuAction();
		if (action != MenuAction.CC_OP && action != MenuAction.CC_OP_LOW_PRIORITY)
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry == null || !entry.isItemOp() || entry.getItemId() <= 0)
		{
			return;
		}

		String option = Text.removeTags(event.getMenuOption()).trim().toLowerCase(Locale.ROOT);
		if (!EQUIP_VERBS.contains(option))
		{
			return;
		}

		String itemName = itemManager.getItemComposition(entry.getItemId()).getName();
		Optional<CardDefinition> card = findCardForItemName(itemName);
		if (card.isEmpty() || isCardOwned(card.get()))
		{
			return;
		}

		event.consume();
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"%s is locked — pull its card from a pack to equip it.", card.get().getName()));
	}

	private Optional<CardDefinition> findCardForItemName(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return Optional.empty();
		}

		Optional<CardDefinition> direct = cardDatabase.findByName(itemName);
		if (direct.isPresent())
		{
			return direct;
		}

		String chargeless = CHARGE_SUFFIX.matcher(itemName.trim()).replaceFirst("");
		if (!chargeless.equals(itemName.trim()))
		{
			Optional<CardDefinition> byCharge = cardDatabase.findByName(chargeless);
			if (byCharge.isPresent())
			{
				return byCharge;
			}
		}

		String undegraded = DEGRADATION_SUFFIX.matcher(itemName.trim()).replaceFirst("");
		if (!undegraded.equals(itemName.trim()))
		{
			return cardDatabase.findByName(undegraded);
		}
		return Optional.empty();
	}

	/** Owning any instance of the card (normal or foil) unlocks the item. */
	private boolean isCardOwned(CardDefinition card)
	{
		return !stateService.getState().getCollectionState()
			.instancesForCardName(card.getName()).isEmpty();
	}

	private boolean isEnforcementBypassed()
	{
		int tutorialProgress = client.getVarpValue(VarPlayerID.TUTORIAL);
		if (tutorialProgress > 0 && tutorialProgress < 1000)
		{
			return true;
		}
		return client.getVarbitValue(VarbitID.BR_INGAME) == 1;
	}
}
