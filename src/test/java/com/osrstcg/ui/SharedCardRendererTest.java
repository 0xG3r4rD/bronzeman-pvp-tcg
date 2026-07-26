package com.osrstcg.ui;

import com.google.gson.Gson;
import com.osrstcg.data.CardDatabase;
import com.osrstcg.data.CardDefinition;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Assert;
import org.junit.Test;

/**
 * Characterizes that a non-foil card face renders identically across animation frames:
 * only the foil sheen/sparkle overlays read the clock, so repaint timers may safely
 * skip frames while no foil card is on screen.
 */
public class SharedCardRendererTest
{
	@Test
	public void nonFoilCardFaceIsIdenticalAcrossAnimationFrames()
	{
		CardDatabase db = new CardDatabase(new Gson());
		db.load();
		CardDefinition card = db.getCards().get(0);
		Color rarity = db.chatRarityColorForCardName(card.getName());

		BufferedImage first = renderFace(card, rarity);
		waitForNextAnimationFrame();
		BufferedImage second = renderFace(card, rarity);

		Assert.assertArrayEquals(pixels(first), pixels(second));
	}

	private static BufferedImage renderFace(CardDefinition card, Color rarity)
	{
		BufferedImage image = new BufferedImage(
			SharedCardRenderer.DEFAULT_CARD_WIDTH, SharedCardRenderer.DEFAULT_CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();
		try
		{
			Rectangle bounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
			SharedCardRenderer.drawCardFace(g2, bounds, card, false, rarity, null, 0L, false);
		}
		finally
		{
			g2.dispose();
		}
		return image;
	}

	private static void waitForNextAnimationFrame()
	{
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start <= SharedCardRenderer.FOIL_SPARKLE_FRAME_MS)
		{
			Thread.onSpinWait();
		}
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
