package com.bronzemanpvptcg.service;

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
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
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
		Optional<CardDefinition> card = findCardForItemName(itemManager.getItemComposition(itemId).getName());
		boolean locked = card.isPresent() && !isCardOwned(card.get());
		lockedItemCache.put(itemId, locked);
		return locked;
	}
}
