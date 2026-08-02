package com.bronzemanpvptcg.overlay;

import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.service.BronzemanEquipLockService;
import com.bronzemanpvptcg.service.TcgStateService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines items in the bank whose card you own, the same way Quest Helper marks the items a quest
 * needs. Uses RuneLite's {@link WidgetItemOverlay} so the highlight tracks the real bank slots
 * through scrolling, searching and tab switches, rather than being drawn into custom widgets.
 */
@Singleton
public class BankUnlockedHighlightOverlay extends WidgetItemOverlay
{
	private static final Color UNLOCKED = new Color(0x3D, 0xD1, 0x6C, 200);
	private static final Color UNLOCKED_FILL = new Color(0x3D, 0xD1, 0x6C, 40);
	private static final Stroke BORDER = new BasicStroke(1.4f);

	private final ItemManager itemManager;
	private final TcgStateService stateService;
	private final BronzemanEquipLockService equipLockService;

	/** itemId -> unlocked, cleared whenever the collection changes. */
	private final Map<Integer, Boolean> cache = new HashMap<>();
	private Object cachedCollection;
	private boolean enabled;

	@Inject
	public BankUnlockedHighlightOverlay(
		ItemManager itemManager,
		TcgStateService stateService,
		BronzemanEquipLockService equipLockService)
	{
		this.itemManager = itemManager;
		this.stateService = stateService;
		this.equipLockService = equipLockService;
		showOnBank();
		showOnInventory();
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void toggle()
	{
		enabled = !enabled;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!enabled || !isUnlocked(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(UNLOCKED_FILL);
		graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setColor(UNLOCKED);
		graphics.setStroke(BORDER);
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	/** True when the item maps to a card the player owns; variants resolve to their base card. */
	private boolean isUnlocked(int itemId)
	{
		Object collection = stateService.getState().getCollectionState();
		if (collection != cachedCollection)
		{
			cache.clear();
			cachedCollection = collection;
		}

		Boolean cached = cache.get(itemId);
		if (cached != null)
		{
			return cached;
		}

		String name = itemManager.getItemComposition(itemId).getName();
		Optional<CardDefinition> card = equipLockService.findCardForItemName(name);
		boolean unlocked = card.isPresent() && !stateService.getState().getCollectionState()
			.instancesForCardName(card.get().getName()).isEmpty();
		cache.put(itemId, unlocked);
		return unlocked;
	}
}
