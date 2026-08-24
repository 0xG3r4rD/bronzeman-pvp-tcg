package com.bronzemanpvptcg.overlay;

import com.bronzemanpvptcg.service.BronzemanEquipLockService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws a small red cross on items whose card is not owned, so a locked item reads as locked at a
 * glance rather than only being dimmed. Rides on top of the widget-opacity fade the equip lock
 * applies; this overlay only draws, it never mutates widgets.
 */
@Singleton
public class LockedItemCrossOverlay extends WidgetItemOverlay
{
	private static final Color CROSS = new Color(0xE0, 0x30, 0x30, 235);
	private static final Color CROSS_SHADOW = new Color(0x00, 0x00, 0x00, 160);
	/** Cross size relative to the slot, kept small so the item stays recognisable. */
	private static final double SCALE = 0.42;
	private static final Stroke STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke SHADOW_STROKE = new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private final BronzemanEquipLockService equipLockService;

	@Inject
	public LockedItemCrossOverlay(BronzemanEquipLockService equipLockService)
	{
		this.equipLockService = equipLockService;
		showOnInventory();
		showOnBank();
		showOnInterfaces(net.runelite.api.gameval.InterfaceID.SHOPMAIN,
			net.runelite.api.gameval.InterfaceID.SHOPSIDE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!equipLockService.isItemLocked(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		int size = (int) (Math.min(bounds.width, bounds.height) * SCALE);
		// Top-right of the slot, clear of the stack-size text drawn top-left.
		int x = bounds.x + bounds.width - size - 1;
		int y = bounds.y + 1;

		Object oldHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(CROSS_SHADOW);
		graphics.setStroke(SHADOW_STROKE);
		drawCross(graphics, x, y, size);

		graphics.setColor(CROSS);
		graphics.setStroke(STROKE);
		drawCross(graphics, x, y, size);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			oldHint == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldHint);
	}

	private static void drawCross(Graphics2D graphics, int x, int y, int size)
	{
		graphics.drawLine(x, y, x + size, y + size);
		graphics.drawLine(x + size, y, x, y + size);
	}
}
