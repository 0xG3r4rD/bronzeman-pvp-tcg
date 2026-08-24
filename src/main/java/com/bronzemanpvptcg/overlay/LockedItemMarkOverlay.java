package com.bronzemanpvptcg.overlay;

import com.bronzemanpvptcg.service.BronzemanEquipLockService;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Stamps the Vengeance spell's red skull on items whose card is not owned, so a locked item reads
 * as locked at a glance rather than only being dimmed. Rides on top of the widget-opacity fade the
 * equip lock applies; this overlay only draws, it never mutates widgets.
 */
@Singleton
public class LockedItemMarkOverlay extends WidgetItemOverlay
{
	/** Mark size relative to the slot, kept small so the item stays recognisable. */
	private static final double SCALE = 0.55;

	private final BronzemanEquipLockService equipLockService;
	private final SpriteManager spriteManager;

	/** Scaled skull, built once the sprite has finished loading. */
	private BufferedImage mark;
	private int markSize;

	@Inject
	public LockedItemMarkOverlay(BronzemanEquipLockService equipLockService, SpriteManager spriteManager)
	{
		this.equipLockService = equipLockService;
		this.spriteManager = spriteManager;
		showOnInventory();
		showOnBank();
		showOnInterfaces(InterfaceID.SHOPMAIN, InterfaceID.SHOPSIDE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!equipLockService.isItemLocked(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		int size = Math.max(8, (int) (Math.min(bounds.width, bounds.height) * SCALE));
		BufferedImage skull = scaledMark(size);
		if (skull == null)
		{
			// Sprite still loading; it self-corrects on a later frame.
			return;
		}

		// Top-right of the slot, clear of the stack-size text drawn top-left.
		graphics.drawImage(skull, bounds.x + bounds.width - size, bounds.y, null);
	}

	private BufferedImage scaledMark(int size)
	{
		if (mark != null && markSize == size)
		{
			return mark;
		}

		BufferedImage source = spriteManager.getSprite(SpriteID.SPELL_VENGEANCE, 0);
		if (source == null)
		{
			return null;
		}

		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.drawImage(source.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
		g.dispose();

		mark = scaled;
		markSize = size;
		return mark;
	}
}
