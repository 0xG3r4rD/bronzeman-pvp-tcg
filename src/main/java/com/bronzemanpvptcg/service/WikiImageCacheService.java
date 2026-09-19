package com.bronzemanpvptcg.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes card art on demand and keeps a bounded number of images in heap.
 * <p>
 * Art ships inside the jar under {@code /cards/}; the wiki is never contacted at runtime.
 * {@link #publicImageUrl(String)} still returns a wiki URL, but only so Discord can fetch a
 * thumbnail for an opt-in webhook embed.
 * <p>
 * Regenerate the bundled art with {@code python tools/bundle_card_images.py}.
 */
@Slf4j
@Singleton
public class WikiImageCacheService
{
	private static final String WIKI_BASE_URL = "https://oldschool.runescape.wiki";
	/** Bundled card art, keyed by sha256 of the card's imageUrl. */
	private static final String CARD_IMAGE_RESOURCE_DIR = "/cards/";
	/** Max decoded images kept in heap; evicted entries are re-read from the jar. */
	private static final int MEMORY_CACHE_MAX_ENTRIES = 256;
	/**
	 * Longest edge kept in the memory cache. Album cards are drawn ~100px wide; the full
	 * bundled detail PNGs otherwise cause large GC pauses while decoding.
	 */
	private static final int MAX_MEMORY_IMAGE_EDGE_PX = 130;
	/** Cap concurrent decodes so album open cannot flood the heap/CPU. */
	private static final int MAX_IN_FLIGHT_LOADS = 4;
	private static final AtomicInteger IMAGE_LOADER_SEQ = new AtomicInteger();
	private static final ThreadFactory IMAGE_LOADER_THREAD_FACTORY = r ->
	{
		Thread t = new Thread(r, "osrs-tcg-wiki-image-" + IMAGE_LOADER_SEQ.incrementAndGet());
		t.setDaemon(true);
		return t;
	};

	private final Semaphore loadPermits = new Semaphore(MAX_IN_FLIGHT_LOADS);
	/** Dedicated pool so blocking ImageIO/HTTP does not stall the common ForkJoinPool. */
	private final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(
		MAX_IN_FLIGHT_LOADS, IMAGE_LOADER_THREAD_FACTORY);
	private final Map<String, BufferedImage> memoryCache = Collections.synchronizedMap(
		new LinkedHashMap<String, BufferedImage>(MEMORY_CACHE_MAX_ENTRIES + 1, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest)
			{
				return size() > MEMORY_CACHE_MAX_ENTRIES;
			}
		});
	private final Map<String, CompletableFuture<BufferedImage>> loadingFutures = new ConcurrentHashMap<>();
	/** URLs that failed to load; skip re-fetching on the overlay/album paint path. */
	private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();
	/**
	 * Fired on the loader thread after each URL settles (cached or failed), with the normalized URL.
	 * Keep listeners cheap; they may run off the EDT.
	 */
	private final List<Consumer<String>> loadListeners = new CopyOnWriteArrayList<>();

	@Inject
	public WikiImageCacheService()
	{
	}

	/** Register for image load completion. Listener may run off the EDT; argument is the normalized URL. */
	public void addLoadListener(Consumer<String> listener)
	{
		if (listener != null)
		{
			loadListeners.add(listener);
		}
	}

	public void removeLoadListener(Consumer<String> listener)
	{
		if (listener != null)
		{
			loadListeners.remove(listener);
		}
	}

	/** Normalize a wiki image URL the same way the memory cache keys entries. */
	public String normalizeImageUrl(String rawUrl)
	{
		return normalizeUrl(rawUrl);
	}

	/** True when the image is already decoded in the memory cache. */
	public boolean isInMemory(String url)
	{
		if (url == null)
		{
			return false;
		}
		String normalized = normalizeUrl(url);
		return !normalized.isEmpty() && memoryCache.containsKey(normalized);
	}

	public void preload(Collection<String> urls)
	{
		if (urls == null)
		{
			return;
		}

		urls.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(url -> !url.isEmpty())
			.forEach(this::ensureLoad);
	}

	/**
	 * Starts loads for the given URLs and blocks until each has settled in memory or failed,
	 * or until {@code timeoutMs} elapses. Safe to call off the EDT (e.g. before applying an album page).
	 */
	public void preloadAndAwait(Collection<String> urls, long timeoutMs)
	{
		if (urls == null || urls.isEmpty())
		{
			return;
		}
		List<CompletableFuture<?>> pending = new ArrayList<>();
		for (String raw : urls)
		{
			if (raw == null)
			{
				continue;
			}
			String normalized = normalizeUrl(raw.trim());
			if (normalized.isEmpty() || isSettled(normalized))
			{
				continue;
			}
			ensureLoad(normalized);
			CompletableFuture<BufferedImage> future = loadingFutures.get(normalized);
			if (future != null)
			{
				pending.add(future);
			}
		}
		if (pending.isEmpty())
		{
			return;
		}
		long waitMs = Math.max(1L, timeoutMs);
		try
		{
			CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
				.get(waitMs, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex)
		{
			log.debug("Timed out waiting for {} wiki image(s)", pending.size());
		}
		catch (Exception ex)
		{
			log.debug("Interrupted/failed waiting for wiki images", ex);
		}
	}

	/** True when the URL is in memory or known-failed (will not start a new load). */
	public boolean isSettled(String url)
	{
		if (url == null)
		{
			return true;
		}
		String normalized = normalizeUrl(url);
		return normalized.isEmpty()
			|| memoryCache.containsKey(normalized)
			|| failedUrls.contains(normalized);
	}

	/** True when the image is not yet available in memory (not started or still loading). */
	public boolean needsLoad(String url)
	{
		if (url == null)
		{
			return false;
		}

		String normalized = normalizeUrl(url);
		if (normalized.isEmpty() || memoryCache.containsKey(normalized) || failedUrls.contains(normalized))
		{
			return false;
		}

		CompletableFuture<BufferedImage> future = loadingFutures.get(normalized);
		return future == null || !future.isDone();
	}

	/** Wiki URL suitable for external embeds (e.g. Dink Discord thumbnail). */
	public String publicImageUrl(String rawUrl)
	{
		String normalized = normalizeUrl(rawUrl);
		if (normalized.isEmpty())
		{
			return "";
		}
		String fromThumb = extractFilenameFromThumbPath(rawUrl);
		if (!fromThumb.isEmpty())
		{
			return directImageUrl(fromThumb);
		}
		String fromPath = extractFilenameFromPath(normalized);
		if (!fromPath.isEmpty() && !looksLikeThumbSizeSegment(fromPath))
		{
			return directImageUrl(fromPath);
		}
		return normalized;
	}

	/** Memory-cache peek only; never starts a disk/network load. Safe on paint paths. */
	public BufferedImage getIfPresent(String url)
	{
		if (url == null)
		{
			return null;
		}
		String normalized = normalizeUrl(url);
		if (normalized.isEmpty())
		{
			return null;
		}
		return memoryCache.get(normalized);
	}

	/** Runs work on the wiki-image background pool (disk decode / card-face rasterization). */
	public void executeBackground(Runnable task)
	{
		if (task != null)
		{
			imageLoadExecutor.execute(task);
		}
	}

	/**
	 * Returns a cached image if present. Safe to call from overlay/UI paint paths:
	 * only reads the memory cache and may kick off a background load — never blocks
	 * on network/disk and never writes the cache on this thread.
	 */
	public BufferedImage getCached(String url)
	{
		if (url == null)
		{
			return null;
		}

		String normalized = normalizeUrl(url);
		if (normalized.isEmpty())
		{
			return null;
		}

		BufferedImage cached = memoryCache.get(normalized);
		if (cached != null)
		{
			return cached;
		}

		if (!failedUrls.contains(normalized))
		{
			ensureLoad(normalized);
		}
		return null;
	}

	private void ensureLoad(String rawUrl)
	{
		String url = normalizeUrl(rawUrl);
		if (url.isEmpty()
			|| memoryCache.containsKey(url)
			|| failedUrls.contains(url)
			|| loadingFutures.containsKey(url))
		{
			return;
		}

		loadingFutures.computeIfAbsent(url, key -> CompletableFuture
			.supplyAsync(() ->
			{
				loadPermits.acquireUninterruptibly();
				try
				{
					return loadImage(key);
				}
				finally
				{
					loadPermits.release();
				}
			}, imageLoadExecutor)
			.whenComplete((image, ex) ->
			{
				// Populate cache before removing the in-flight future so paint reads never
				// observe "not loading" and "not cached" at the same time.
				if (image != null)
				{
					failedUrls.remove(key);
					memoryCache.put(key, image);
				}
				else
				{
					failedUrls.add(key);
				}
				loadingFutures.remove(key);
				notifyLoadListeners(key);
			}));
	}

	private void notifyLoadListeners(String normalizedUrl)
	{
		for (Consumer<String> listener : loadListeners)
		{
			try
			{
				listener.accept(normalizedUrl);
			}
			catch (Exception ex)
			{
				log.debug("Image load listener failed", ex);
			}
		}
	}

	/**
	 * Card art ships inside the jar under {@code /cards/<sha256 of imageUrl>.png}; see
	 * {@code tools/bundle_card_images.py}. Nothing is fetched at runtime.
	 */
	private BufferedImage loadImage(String url)
	{
		String resource = CARD_IMAGE_RESOURCE_DIR + sha256Hex(url) + ".png";
		try (InputStream in = WikiImageCacheService.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.debug("No bundled card image {} for {}", resource, url);
				return null;
			}
			try (ImageInputStream imageStream = ImageIO.createImageInputStream(in))
			{
				if (imageStream == null)
				{
					return null;
				}
				var readers = ImageIO.getImageReaders(imageStream);
				if (!readers.hasNext())
				{
					return null;
				}
				ImageReader reader = readers.next();
				try
				{
					reader.setInput(imageStream, true, true);
					int maxEdge = Math.max(reader.getWidth(0), reader.getHeight(0));
					int subsample = 1;
					while (subsample < 32 && maxEdge / subsample > MAX_MEMORY_IMAGE_EDGE_PX * 2)
					{
						subsample *= 2;
					}
					ImageReadParam param = reader.getDefaultReadParam();
					if (subsample > 1)
					{
						param.setSourceSubsampling(subsample, subsample, 0, 0);
					}
					BufferedImage image = reader.read(0, param);
					return image == null ? null : downscaleForMemoryCache(image);
				}
				finally
				{
					reader.dispose();
				}
			}
		}
		catch (Exception ex)
		{
			log.debug("Bundled card image read failed for {}", resource, ex);
			return null;
		}
	}

	private static BufferedImage downscaleForMemoryCache(BufferedImage source)
	{
		if (source == null)
		{
			return null;
		}
		int maxEdge = Math.max(source.getWidth(), source.getHeight());
		if (maxEdge <= MAX_MEMORY_IMAGE_EDGE_PX)
		{
			return source;
		}
		double scale = MAX_MEMORY_IMAGE_EDGE_PX / (double) maxEdge;
		int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = scaled.createGraphics();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(source, 0, 0, w, h, null);
		}
		finally
		{
			g2.dispose();
		}
		return scaled;
	}

	private static String sha256Hex(String value)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
			char[] hex = "0123456789abcdef".toCharArray();
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest)
			{
				sb.append(hex[(b >> 4) & 0xF]).append(hex[b & 0xF]);
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	private String directImageUrl(String filename)
	{
		String safe = filename == null ? "" : filename.trim();
		if (safe.isEmpty())
		{
			return "";
		}
		// Keep wiki-safe URL encoding for parenthesized dose variants.
		safe = safe.replace("(", "%28").replace(")", "%29");
		return WIKI_BASE_URL + "/images/" + safe;
	}

	/** True for MediaWiki thumb basename segments like {@code 130px-Foo_detail.png}. */
	private static boolean looksLikeThumbSizeSegment(String segment)
	{
		return segment != null && segment.matches("\\d+px-.+");
	}

	private String extractFilenameFromThumbPath(String rawUrl)
	{
		if (rawUrl == null)
		{
			return "";
		}
		String value = rawUrl.trim();
		String marker = "/images/thumb/";
		int markerIndex = value.indexOf(marker);
		if (markerIndex < 0)
		{
			return "";
		}

		String tail = value.substring(markerIndex + marker.length());
		int slash = tail.indexOf('/');
		if (slash <= 0)
		{
			return "";
		}
		return tail.substring(0, slash);
	}

	private String extractFilenameFromPath(String normalizedUrl)
	{
		if (normalizedUrl == null)
		{
			return "";
		}
		int lastSlash = normalizedUrl.lastIndexOf('/');
		if (lastSlash < 0 || lastSlash >= normalizedUrl.length() - 1)
		{
			return "";
		}
		String segment = normalizedUrl.substring(lastSlash + 1).trim();
		return segment.isEmpty() ? "" : segment;
	}

	private String normalizeUrl(String rawUrl)
	{
		if (rawUrl == null)
		{
			return "";
		}

		String url = rawUrl.trim();
		if (url.isEmpty())
		{
			return "";
		}
		if (url.startsWith("http://") || url.startsWith("https://"))
		{
			return url;
		}
		if (url.startsWith("//"))
		{
			return "https:" + url;
		}
		if (url.startsWith("/"))
		{
			return WIKI_BASE_URL + url;
		}
		return WIKI_BASE_URL + "/" + url;
	}
}
