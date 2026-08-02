package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.data.CardDatabase;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.model.OwnedCardInstance;
import com.bronzemanpvptcg.util.TcgPluginGameMessages;
import net.runelite.client.chat.ChatMessageManager;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageUtil;

/**
 * Adds an icon button to the bank that opens an in-bank grid of every item you have unlocked,
 * drawn with the real item sprites. Item ids come from a one-off scan of the client's item
 * definitions, matched against card names and their aliases.
 */
@Singleton
public final class BankUnlocksButtonService
{
	private static final int BUTTON_SIZE = 24;
	/** Offsets from the bank window's top-right, clear of the existing tab row. */
	private static final int BUTTON_MARGIN_X = 68;
	private static final int BUTTON_MARGIN_Y = 4;
	/** Custom sprite slot for the plugin icon; negative ids never collide with cache sprites. */
	private static final int ICON_SPRITE_ID = -1401;


	private final Client client;
	private final ClientThread clientThread;
	private final CardDatabase cardDatabase;
	private final TcgStateService stateService;
	private final BronzemanEquipLockService equipLockService;
	private final ChatMessageManager chatMessageManager;

	/** Lower-case item name -> lowest matching item id; built once, then reused. */
	private final Map<String, Integer> itemIdByName = new ConcurrentHashMap<>();
	private boolean itemIndexBuilt;
	private boolean filterActive;

	/** Bank grid geometry, matching the client's own layout. */
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_X_SPACING = 48;
	private static final int ITEM_Y_SPACING = 36;
	private static final int ITEM_ROW_START = 51;
	private static final int ITEM_Y_START = 0;
	private static final int BANK_REBUILD_SCRIPT = 29;

	@Inject
	public BankUnlocksButtonService(
		Client client,
		ClientThread clientThread,
		CardDatabase cardDatabase,
		TcgStateService stateService,
		BronzemanEquipLockService equipLockService,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.cardDatabase = cardDatabase;
		this.stateService = stateService;
		this.equipLockService = equipLockService;
		this.chatMessageManager = chatMessageManager;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			filterActive = false;
			clientThread.invokeLater(this::addButton);
		}
	}

	private void addButton()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		registerIconSprite();

		Widget button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(ICON_SPRITE_ID);
		button.setOriginalWidth(BUTTON_SIZE);
		button.setOriginalHeight(BUTTON_SIZE);
		button.setOriginalX(parent.getWidth() - BUTTON_MARGIN_X);
		button.setOriginalY(BUTTON_MARGIN_Y);
		button.setHasListener(true);
		button.setNoClickThrough(true);
		button.setAction(0, "Show only unlocked items");
		button.setOnOpListener((JavaScriptCallback) e -> togglePanel());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setOpacity(40));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setOpacity(0));
		button.revalidate();
	}

	private void togglePanel()
	{
		filterActive = !filterActive;
		if (filterActive)
		{
			applyBankFilter();
			TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager,
				"Bank filtered to your unlocked items. Click the icon again to show everything.");
		}
		else
		{
			// Let the client redraw the bank normally, which restores every hidden slot.
			client.runScript(BANK_REBUILD_SCRIPT);
			clientThread.invokeLater(this::addButton);
			TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, "Bank filter off.");
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// The bank redraws on search, tab change and deposits; re-apply so the filter sticks.
		if (filterActive && event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			clientThread.invokeLater(this::applyBankFilter);
		}
	}

	/**
	 * Hides every bank slot whose item is not unlocked and repacks the survivors into a gapless
	 * grid, the way the bank-tag plugins filter. Scroll height is recomputed so the bar matches.
	 */
	private void applyBankFilter()
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.getDynamicChildren() == null)
		{
			return;
		}

		int shown = 0;
		for (Widget child : container.getDynamicChildren())
		{
			if (child == null || child.getItemId() <= 0)
			{
				continue;
			}
			if (!isUnlockedItem(child.getItemId()))
			{
				child.setHidden(true);
				child.setOriginalX(0);
				child.setOriginalY(0);
				child.revalidate();
				continue;
			}
			child.setHidden(false);
			child.setOriginalX(ITEM_ROW_START + (shown % ITEMS_PER_ROW) * ITEM_X_SPACING);
			child.setOriginalY(ITEM_Y_START + (shown / ITEMS_PER_ROW) * ITEM_Y_SPACING);
			child.revalidate();
			shown++;
		}

		int rows = (shown + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		container.setScrollHeight(Math.max(0, rows * ITEM_Y_SPACING + ITEM_Y_START));
		container.revalidate();
		Widget scrollbar = client.getWidget(InterfaceID.Bankmain.SCROLLBAR);
		if (scrollbar != null)
		{
			client.runScript(ScriptID.UPDATE_SCROLLBAR, scrollbar.getId(), container.getId(),
				container.getScrollY());
		}
	}

	/** True when the item, or the base item behind a variant, has an owned card. */
	private boolean isUnlockedItem(int itemId)
	{
		ItemComposition comp = client.getItemDefinition(itemId);
		if (comp == null || comp.getName() == null || !BronzemanEquipLockService.isEquipable(comp))
		{
			return false;
		}
		Optional<CardDefinition> card = equipLockService.findCardForItemName(comp.getName());
		return card.isPresent() && !stateService.getState().getCollectionState()
			.instancesForCardName(card.get().getName()).isEmpty();
	}

	/** Item ids for every card the player owns, including the extra items a bundled card covers. */
	private List<Integer> unlockedItemIds()
	{
		Set<String> ownedCards = new LinkedHashSet<>();
		for (OwnedCardInstance instance : stateService.getState().getCollectionState().getOwnedInstances())
		{
			if (instance != null && instance.getCardName() != null)
			{
				ownedCards.add(instance.getCardName());
			}
		}

		List<Integer> ids = new ArrayList<>();
		Set<Integer> seen = new LinkedHashSet<>();
		for (String cardName : ownedCards)
		{
			List<String> lookups = new ArrayList<>();
			lookups.add(cardName);
			Optional<CardDefinition> card = cardDatabase.findByNameOrAlias(cardName);
			card.ifPresent(c -> lookups.addAll(c.getAliasNames()));

			for (String name : lookups)
			{
				Integer id = itemIdByName.get(name.trim().toLowerCase(Locale.ROOT));
				if (id != null && seen.add(id))
				{
					ids.add(id);
				}
			}
		}
		return ids;
	}

	/**
	 * One pass over the client's item definitions, keeping only names the catalog cares about.
	 * The lowest id wins so placeholders and noted copies do not shadow the real item.
	 */
	private void buildItemIndex()
	{
		if (itemIndexBuilt)
		{
			return;
		}

		Set<String> wanted = new LinkedHashSet<>();
		for (CardDefinition card : cardDatabase.getCards())
		{
			if (card == null || card.getName() == null)
			{
				continue;
			}
			wanted.add(card.getName().trim().toLowerCase(Locale.ROOT));
			for (String alias : card.getAliasNames())
			{
				wanted.add(alias.trim().toLowerCase(Locale.ROOT));
			}
		}

		int count = client.getItemCount();
		for (int id = 0; id < count; id++)
		{
			ItemComposition comp;
			try
			{
				comp = client.getItemDefinition(id);
			}
			catch (RuntimeException ex)
			{
				continue;
			}
			if (comp == null || comp.getName() == null || comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			String key = comp.getName().trim().toLowerCase(Locale.ROOT);
			if (wanted.contains(key))
			{
				itemIdByName.putIfAbsent(key, id);
			}
		}
		itemIndexBuilt = true;
	}

	/** Publishes the plugin's card icon as a client sprite so a widget can draw it. */
	private void registerIconSprite()
	{
		if (client.getSpriteOverrides().containsKey(ICON_SPRITE_ID))
		{
			return;
		}
		BufferedImage icon = ImageUtil.loadImageResource(BankUnlocksButtonService.class, "/icon.png");
		if (icon != null)
		{
			client.getSpriteOverrides().put(ICON_SPRITE_ID,
				ImageUtil.getImageSpritePixels(ImageUtil.resizeImage(icon, BUTTON_SIZE, BUTTON_SIZE), client));
		}
	}
}
