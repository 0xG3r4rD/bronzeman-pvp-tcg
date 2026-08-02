package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.data.CardDatabase;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.model.OwnedCardInstance;
import com.bronzemanpvptcg.overlay.BankUnlockedHighlightOverlay;
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
	private final BankUnlockedHighlightOverlay highlightOverlay;
	private final ChatMessageManager chatMessageManager;

	/** Lower-case item name -> lowest matching item id; built once, then reused. */
	private final Map<String, Integer> itemIdByName = new ConcurrentHashMap<>();
	private boolean itemIndexBuilt;

	@Inject
	public BankUnlocksButtonService(
		Client client,
		ClientThread clientThread,
		CardDatabase cardDatabase,
		TcgStateService stateService,
		BankUnlockedHighlightOverlay highlightOverlay,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.cardDatabase = cardDatabase;
		this.stateService = stateService;
		this.highlightOverlay = highlightOverlay;
		this.chatMessageManager = chatMessageManager;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
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
		button.setAction(0, "Toggle unlocked-item highlight");
		button.setOnOpListener((JavaScriptCallback) e -> togglePanel());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setOpacity(40));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setOpacity(0));
		button.revalidate();
	}

	private void togglePanel()
	{
		highlightOverlay.toggle();
		buildItemIndex();
		int unlocked = unlockedItemIds().size();
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, highlightOverlay.isEnabled()
			? String.format("Highlighting your %d unlocked items in the bank.", unlocked)
			: "Unlocked-item highlighting off.");
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
