package com.bronzemanpvptcg.data;

import com.bronzemanpvptcg.service.WikiImageCacheService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Card art ships in the jar instead of being fetched from the wiki, so every card needs a
 * bundled image. Regenerate with {@code python tools/bundle_card_images.py} after adding cards.
 */
public class BundledCardImagesTest
{
	private static List<String> cardImageUrls() throws Exception
	{
		try (InputStream in = BundledCardImagesTest.class.getResourceAsStream("/Card.json");
			InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			List<String> urls = new ArrayList<>();
			for (JsonElement element : new Gson().fromJson(reader, JsonArray.class))
			{
				JsonElement url = element.getAsJsonObject().get("imageUrl");
				if (url != null && !url.isJsonNull() && !url.getAsString().trim().isEmpty())
				{
					urls.add(url.getAsString().trim());
				}
			}
			return urls;
		}
	}

	private static String sha256Hex(String value) throws Exception
	{
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		char[] hex = "0123456789abcdef".toCharArray();
		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest)
		{
			sb.append(hex[(b >> 4) & 0xF]).append(hex[b & 0xF]);
		}
		return sb.toString();
	}

	@Test
	public void everyCardHasABundledImage() throws Exception
	{
		List<String> missing = new ArrayList<>();
		for (String url : cardImageUrls())
		{
			String resource = "/cards/" + sha256Hex(url) + ".png";
			if (BundledCardImagesTest.class.getResource(resource) == null)
			{
				missing.add(url);
			}
		}
		assertTrue("No bundled image for: " + missing, missing.isEmpty());
	}

	@Test
	public void bundledImagesDecode() throws Exception
	{
		List<String> urls = cardImageUrls();
		assertTrue("Card.json has no cards with images", urls.size() > 900);

		// Decoding all of them is slow; a spread is enough to catch a broken generator run.
		for (int i = 0; i < urls.size(); i += 50)
		{
			String resource = "/cards/" + sha256Hex(urls.get(i)) + ".png";
			try (InputStream in = BundledCardImagesTest.class.getResourceAsStream(resource))
			{
				assertNotNull("missing " + resource, in);
				assertNotNull("undecodable " + resource, ImageIO.read(in));
			}
		}
	}

	@Test
	public void serviceResolvesCardArtWithoutNetwork() throws Exception
	{
		List<String> urls = cardImageUrls().subList(0, 12);
		WikiImageCacheService service = new WikiImageCacheService();
		service.preloadAndAwait(urls, 30_000L);

		for (String url : urls)
		{
			assertNotNull("no image resolved for " + url, service.getIfPresent(url));
		}
	}
}
