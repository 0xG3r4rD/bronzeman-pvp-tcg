package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.util.TcgPluginGameMessages;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageUtil;

/**
 * Applies the bronzeman rules to the Grand Exchange search list: locked results are greyed out the
 * same way locked gear is in the bank, and a plugin button in the offer window filters the list
 * down to the items you have actually unlocked and can therefore buy.
 */
@Slf4j
@Singleton
public final class GrandExchangeUnlocksService
{
	private static final int LOCKED_OPACITY = 140;
	private static final int ICON_SPRITE_ID = -1402;
	private static final int BUTTON_SIZE = 22;
	private static final int BUTTON_MARGIN_X = 30;
	private static final int BUTTON_MARGIN_Y = 4;

	private final Client client;
	private final ClientThread clientThread;
	private final BronzemanEquipLockService equipLockService;
	private final ChatMessageManager chatMessageManager;

	/** Row layout before filtering, so switching the filter off restores the client's own list. */
	private final Map<Integer, Integer> originalRowY = new HashMap<>();
	private boolean filterActive;
	private boolean applying;

	@Inject
	public GrandExchangeUnlocksService(
		Client client,
		ClientThread clientThread,
		BronzemanEquipLockService equipLockService,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.equipLockService = equipLockService;
		this.chatMessageManager = chatMessageManager;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			filterActive = false;
			originalRowY.clear();
			clientThread.invokeLater(this::addButton);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// The search list is rebuilt on every keystroke, so re-apply after each build.
		if (event.getScriptId() == ScriptID.GE_ITEM_SEARCH)
		{
			clientThread.invokeLater(this::markSearchResults);
		}
	}

	private void addButton()
	{
		Widget parent = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		registerIconSprite();

		Widget button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(ICON_SPRITE_ID);
		button.setOriginalWidth(BUTTON_SIZE);
		button.setOriginalHeight(BUTTON_SIZE);
		button.setOriginalX(Math.max(0, parent.getWidth() - BUTTON_MARGIN_X));
		button.setOriginalY(BUTTON_MARGIN_Y);
		button.setHasListener(true);
		button.setNoClickThrough(true);
		button.setAction(0, "Show only unlocked items");
		button.setOnOpListener((JavaScriptCallback) e -> toggleFilter());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setOpacity(40));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setOpacity(0));
		button.revalidate();
	}

	private void toggleFilter()
	{
		filterActive = !filterActive;
		markSearchResults();
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager, filterActive
			? "Grand Exchange search filtered to items you have unlocked."
			: "Grand Exchange filter off; locked items show greyed out.");
	}

	/**
	 * Greys out every locked result, and hides them outright while the filter is on. Rows are a
	 * uniform height, so survivors repack by stepping through the spacing the client already used.
	 */
	private void markSearchResults()
	{
		Widget list = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLAREA);
		if (list == null || list.getDynamicChildren() == null || applying)
		{
			return;
		}

		applying = true;
		try
		{
			int step = rowStep(list);
			int shown = 0;
			for (Widget child : list.getDynamicChildren())
			{
				if (child == null || child.getItemId() <= 0)
				{
					continue;
				}
				originalRowY.putIfAbsent(child.getIndex(), child.getOriginalY());
				int baseY = originalRowY.getOrDefault(child.getIndex(), child.getOriginalY());
				boolean locked = equipLockService.isItemLocked(child.getItemId());

				if (filterActive && locked)
				{
					child.setHidden(true);
					child.revalidate();
					continue;
				}

				child.setHidden(false);
				child.setOpacity(locked ? LOCKED_OPACITY : 0);
				child.setOriginalY(filterActive && step > 0 ? shown * step : baseY);
				child.revalidate();
				shown++;
			}
		}
		catch (RuntimeException ex)
		{
			log.warn("Grand Exchange filter failed; leaving the list as the client built it", ex);
			filterActive = false;
		}
		finally
		{
			applying = false;
		}
	}

	/** Vertical distance between two result rows, taken from the client's own layout. */
	private int rowStep(Widget list)
	{
		int first = Integer.MIN_VALUE;
		for (Widget child : list.getDynamicChildren())
		{
			if (child == null || child.getItemId() <= 0)
			{
				continue;
			}
			int y = originalRowY.getOrDefault(child.getIndex(), child.getOriginalY());
			if (first == Integer.MIN_VALUE)
			{
				first = y;
			}
			else if (y != first)
			{
				return Math.abs(y - first);
			}
		}
		return 0;
	}

	private void registerIconSprite()
	{
		if (client.getSpriteOverrides().containsKey(ICON_SPRITE_ID))
		{
			return;
		}
		BufferedImage icon = ImageUtil.loadImageResource(GrandExchangeUnlocksService.class, "/icon.png");
		if (icon != null)
		{
			client.getSpriteOverrides().put(ICON_SPRITE_ID,
				ImageUtil.getImageSpritePixels(ImageUtil.resizeImage(icon, BUTTON_SIZE, BUTTON_SIZE), client));
		}
	}
}
