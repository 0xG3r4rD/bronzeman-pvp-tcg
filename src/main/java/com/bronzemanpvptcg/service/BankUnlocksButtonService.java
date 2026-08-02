package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.ui.collectionalbum.CollectionAlbumManager;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageUtil;

/**
 * Adds an "Unlocked" button to the bank interface that opens the collection album, so the list of
 * cards you own is reachable without leaving the bank. The button is rebuilt whenever the bank
 * interface loads, since the client discards dynamic children when the interface closes.
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
	private final CollectionAlbumManager collectionAlbumManager;

	@Inject
	public BankUnlocksButtonService(
		Client client,
		ClientThread clientThread,
		CollectionAlbumManager collectionAlbumManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.collectionAlbumManager = collectionAlbumManager;
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
		button.setAction(0, "View unlocked cards");
		button.setOnOpListener((JavaScriptCallback) e ->
			SwingUtilities.invokeLater(collectionAlbumManager::showOrBringToFront));
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setOpacity(40));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setOpacity(0));
		button.revalidate();
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
