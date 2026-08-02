package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.data.CardDatabase;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.model.OwnedCardInstance;
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

	private static final int PANEL_X = 8;
	private static final int PANEL_Y = 34;
	private static final int PANEL_WIDTH = 472;
	private static final int PANEL_HEIGHT = 300;
	private static final int GRID_COLUMNS = 10;
	private static final int CELL = 44;
	private static final int GRID_PAD = 10;
	private static final int HEADER_HEIGHT = 18;
	private static final int FONT_PLAIN_12 = 495;
	/** Redraws the bank interface, which is how the dynamic children get discarded. */
	private static final int BANK_REBUILD_SCRIPT = 29;

	private final Client client;
	private final ClientThread clientThread;
	private final CardDatabase cardDatabase;
	private final TcgStateService stateService;

	/** Lower-case item name -> lowest matching item id; built once, then reused. */
	private final Map<String, Integer> itemIdByName = new ConcurrentHashMap<>();
	private boolean itemIndexBuilt;
	private boolean panelOpen;

	@Inject
	public BankUnlocksButtonService(
		Client client,
		ClientThread clientThread,
		CardDatabase cardDatabase,
		TcgStateService stateService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.cardDatabase = cardDatabase;
		this.stateService = stateService;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			panelOpen = false;
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
		button.setAction(0, "View unlocked items");
		button.setOnOpListener((JavaScriptCallback) e -> togglePanel());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setOpacity(40));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setOpacity(0));
		button.revalidate();
	}

	private void togglePanel()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		if (panelOpen)
		{
			panelOpen = false;
			client.runScript(BANK_REBUILD_SCRIPT);
			clientThread.invokeLater(this::addButton);
			return;
		}

		panelOpen = true;
		buildItemIndex();
		drawPanel(parent, unlockedItemIds());
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

	private void drawPanel(Widget parent, List<Integer> itemIds)
	{
		Widget background = parent.createChild(-1, WidgetType.RECTANGLE);
		background.setOriginalX(PANEL_X);
		background.setOriginalY(PANEL_Y);
		background.setOriginalWidth(PANEL_WIDTH);
		background.setOriginalHeight(PANEL_HEIGHT);
		background.setFilled(true);
		background.setTextColor(0x1A1A1A);
		background.setOpacity(15);
		background.setNoClickThrough(true);
		background.revalidate();

		int visibleRows = Math.max(1, (PANEL_HEIGHT - HEADER_HEIGHT - 12) / CELL);
		int maxCells = visibleRows * GRID_COLUMNS;
		int shown = Math.min(itemIds.size(), maxCells);

		Widget header = parent.createChild(-1, WidgetType.TEXT);
		header.setText(itemIds.isEmpty()
			? "No items unlocked yet — open a pack to start."
			: "Unlocked items: " + itemIds.size()
				+ (itemIds.size() > shown ? " (showing " + shown + ")" : ""));
		header.setTextColor(0xFF981F);
		header.setFontId(FONT_PLAIN_12);
		header.setTextShadowed(true);
		header.setOriginalX(PANEL_X + GRID_PAD);
		header.setOriginalY(PANEL_Y + 4);
		header.setOriginalWidth(PANEL_WIDTH - 2 * GRID_PAD);
		header.setOriginalHeight(HEADER_HEIGHT);
		header.revalidate();

		int gridTop = PANEL_Y + HEADER_HEIGHT + 6;
		for (int i = 0; i < shown; i++)
		{
			int itemId = itemIds.get(i);
			Widget slot = parent.createChild(-1, WidgetType.GRAPHIC);
			slot.setItemId(itemId);
			slot.setItemQuantity(1);
			slot.setItemQuantityMode(0);
			slot.setOriginalWidth(36);
			slot.setOriginalHeight(32);
			slot.setOriginalX(PANEL_X + GRID_PAD + (i % GRID_COLUMNS) * CELL);
			slot.setOriginalY(gridTop + (i / GRID_COLUMNS) * CELL);
			slot.setHasListener(true);
			slot.setNoClickThrough(true);
			ItemComposition comp = client.getItemDefinition(itemId);
			slot.setAction(0, comp == null ? "Unlocked" : comp.getName());
			slot.revalidate();
		}
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
