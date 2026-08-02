package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.OsrsTcgConfig;
import com.bronzemanpvptcg.data.CardDatabase;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.model.CollectionState;
import com.bronzemanpvptcg.util.TcgPluginGameMessages;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.api.widgets.WidgetUtil;
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

	/**
	 * Trailing variant marker: charges ("Amulet of glory(4)"), locked and imbued items
	 * ("Fire cape (l)", "Ring of suffering (ri)"), ornament kits ("Amulet of fury (or)"),
	 * autocast forms ("Accursed sceptre (au)") and poison grades ("Dragon dagger (p++)").
	 * No card name contains brackets, so stripping these is unambiguous.
	 */
	private static final Pattern BRACKET_SUFFIX = Pattern.compile("\\s*\\([^()]*\\)$");
	/** "Dharok's helm 100" -> Barrows degradation states share the undegraded card. */
	private static final Pattern DEGRADATION_SUFFIX = Pattern.compile("\\s+\\d+$");
	/** Upgraded forms that keep the base item's name ("Imbued saradomin cape"). */
	private static final Pattern UPGRADE_PREFIX =
		Pattern.compile("^(imbued|superior|corrupted)\\s+", Pattern.CASE_INSENSITIVE);
	private static final int MAX_NORMALISE_STEPS = 4;

	/** Fade level for locked item sprites (Alch Blocker's widget-opacity technique). */
	private static final int LOCKED_ITEM_OPACITY = 140;
	private static final int[] LOCKED_MARK_CONTAINERS = {
		InterfaceID.Inventory.ITEMS, InterfaceID.Bankmain.ITEMS, InterfaceID.Bankside.ITEMS,
		InterfaceID.Shopmain.ITEMS, InterfaceID.Shopside.ITEMS};
	private static final int MARK_SWEEP_TICKS = 5;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final CardDatabase cardDatabase;
	private final TcgStateService stateService;
	private final ChatMessageManager chatMessageManager;
	private final OsrsTcgConfig config;

	private final Map<Integer, Boolean> lockedItemCache = new HashMap<>();
	private CollectionState lockedItemCacheCollection;
	private boolean markRefreshQueued;
	private int markTickCounter;

	@Inject
	public BronzemanEquipLockService(
		Client client,
		ClientThread clientThread,
		ItemManager itemManager,
		CardDatabase cardDatabase,
		TcgStateService stateService,
		ChatMessageManager chatMessageManager,
		OsrsTcgConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.cardDatabase = cardDatabase;
		this.stateService = stateService;
		this.chatMessageManager = chatMessageManager;
		this.config = config;
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

		if (blockLockedGrandExchangeChoice(event))
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
		if (card.isEmpty() || isWhitelisted(itemName) || isCardOwned(card.get()))
		{
			return;
		}

		event.consume();
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"%s is locked — pull its card from a pack to equip it.", card.get().getName()));
	}

	/**
	 * Grand Exchange search results are drawn in the chatbox, so consuming the selection is the
	 * available hook for stopping a locked item being bought. Best effort: a keyboard-driven
	 * flow can still reach the offer screen.
	 *
	 * @return true when the click was for a locked item and has been consumed
	 */
	private boolean blockLockedGrandExchangeChoice(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		if (entry == null
			|| WidgetUtil.componentToInterface(entry.getParam1()) != InterfaceID.CHATBOX
			|| client.getWidget(InterfaceID.GE_OFFERS, 0) == null)
		{
			return false;
		}

		String itemName = Text.removeTags(event.getMenuTarget()).trim();
		Optional<CardDefinition> card = findCardForItemName(itemName);
		if (card.isEmpty() || isWhitelisted(itemName) || isCardOwned(card.get()))
		{
			return false;
		}

		event.consume();
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, String.format(
			"%s is locked — pull its card before buying it.", card.get().getName()));
		return true;
	}

	/**
	 * Resolves an in-game item to its card. Tries the exact name first, then peels one variant
	 * marker at a time, so every untradeable, imbued, ornamented or bundled variant lands on the
	 * card that unlocks it.
	 */
	private Optional<CardDefinition> findCardForItemName(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return Optional.empty();
		}

		String candidate = itemName.trim();
		for (int step = 0; step <= MAX_NORMALISE_STEPS; step++)
		{
			Optional<CardDefinition> hit = cardDatabase.findByNameOrAlias(candidate);
			if (hit.isPresent())
			{
				return hit;
			}

			String next = stripOneVariantMarker(candidate);
			if (next.equals(candidate))
			{
				return Optional.empty();
			}
			candidate = next;
		}
		return Optional.empty();
	}

	/** @return the name with one trailing/leading variant marker removed, or unchanged if none. */
	private static String stripOneVariantMarker(String name)
	{
		String stripped = BRACKET_SUFFIX.matcher(name).replaceFirst("").trim();
		if (!stripped.equals(name))
		{
			return stripped;
		}
		stripped = DEGRADATION_SUFFIX.matcher(name).replaceFirst("").trim();
		if (!stripped.equals(name))
		{
			return stripped;
		}
		return UPGRADE_PREFIX.matcher(name).replaceFirst("").trim();
	}

	/**
	 * User-managed escape hatch: a comma-separated list of item names that stay equipable
	 * regardless of the collection. Matched on the raw in-game name, case-insensitively.
	 */
	private boolean isWhitelisted(String itemName)
	{
		String list = config.itemWhitelist();
		if (list == null || list.trim().isEmpty() || itemName == null)
		{
			return false;
		}
		String needle = itemName.trim().toLowerCase(Locale.ROOT);
		for (String entry : list.split(","))
		{
			String candidate = entry.trim().toLowerCase(Locale.ROOT);
			if (!candidate.isEmpty() && candidate.equals(needle))
			{
				return true;
			}
		}
		return false;
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

	// ------------------------------------------------------------------ locked item fade

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// Redraw scripts reset widget opacity, so re-fade as soon as they finish.
		if (event.getScriptId() == ScriptID.INVENTORY_DRAWITEM
			|| event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			scheduleLockedItemMarks();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Shops build their stock on open; fade it immediately rather than waiting for
		// the periodic sweep.
		if (event.getGroupId() == InterfaceID.SHOPMAIN || event.getGroupId() == InterfaceID.SHOPSIDE)
		{
			scheduleLockedItemMarks();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Every ~3s: catches unlocks (new pulls) and bypass changes that happen
		// without an inventory redraw.
		if (++markTickCounter % MARK_SWEEP_TICKS == 0)
		{
			scheduleLockedItemMarks();
		}
	}

	/** Restore every fade on plugin shutdown. */
	public void shutdown()
	{
		clientThread.invoke(() -> setLockedItemMarks(false));
	}

	/** Coalesces the many per-slot INVENTORY_DRAWITEM firings into one pass per tick. */
	private void scheduleLockedItemMarks()
	{
		if (markRefreshQueued)
		{
			return;
		}
		markRefreshQueued = true;
		clientThread.invokeAtTickEnd(() ->
		{
			markRefreshQueued = false;
			setLockedItemMarks(!isEnforcementBypassed());
		});
	}

	/** Fade locked items in the inventory, bank and shops via widget opacity. Client thread only. */
	private void setLockedItemMarks(boolean marking)
	{
		for (int componentId : LOCKED_MARK_CONTAINERS)
		{
			Widget container = client.getWidget(componentId);
			Widget[] children = container == null ? null : container.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child == null || child.getItemId() <= 0)
				{
					continue;
				}
				if (marking && isItemLocked(child.getItemId()))
				{
					child.setOpacity(LOCKED_ITEM_OPACITY);
				}
				else if (child.getOpacity() == LOCKED_ITEM_OPACITY)
				{
					child.setOpacity(0);
				}
			}
		}
	}

	/** Same rule the click block uses; cached per item id until the collection changes. */
	private boolean isItemLocked(int itemId)
	{
		CollectionState collection = stateService.getState().getCollectionState();
		if (collection != lockedItemCacheCollection)
		{
			lockedItemCache.clear();
			lockedItemCacheCollection = collection;
		}
		Boolean cached = lockedItemCache.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		String name = itemManager.getItemComposition(itemId).getName();
		Optional<CardDefinition> card = findCardForItemName(name);
		boolean locked = card.isPresent() && !isWhitelisted(name) && !isCardOwned(card.get());
		lockedItemCache.put(itemId, locked);
		return locked;
	}
}
