package com.bronzemanpvptcg.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PackCatalog
{
	private static final Type PACK_LIST_TYPE = new TypeToken<List<BoosterPackDefinition>>() { }.getType();

	private final Gson gson;
	private List<BoosterPackDefinition> boosters = Collections.emptyList();
	private boolean loaded;

	@Inject
	public PackCatalog(Gson gson)
	{
		this.gson = gson;
	}

	public synchronized void load()
	{
		if (loaded)
		{
			return;
		}

		List<BoosterPackDefinition> merged = new ArrayList<>();
		int fromMain = appendPackResource(merged, "/Packs.json", false);
		int fromDebug = appendPackResource(merged, "/PacksDebug.json", true);
		boosters = Collections.unmodifiableList(merged);
		loaded = true;
		log.info("Loaded {} booster pack definitions ({} from Packs.json, {} from PacksDebug.json)",
			boosters.size(), fromMain, fromDebug);
	}

	public synchronized List<BoosterPackDefinition> getBoosters()
	{
		return boosters;
	}

	/** Production packs plus {@code debugOnly} entries when {@code debugLogging} is enabled. */
	public synchronized List<BoosterPackDefinition> getVisibleBoosters(boolean debugLogging)
	{
		if (boosters.isEmpty())
		{
			return boosters;
		}
		List<BoosterPackDefinition> visible = new ArrayList<>();
		for (BoosterPackDefinition booster : boosters)
		{
			if (booster == null)
			{
				continue;
			}
			if (!booster.isDebugOnly() || debugLogging)
			{
				visible.add(booster);
			}
		}
		return Collections.unmodifiableList(visible);
	}

	public synchronized void setBoostersForTesting(List<BoosterPackDefinition> testBoosters)
	{
		boosters = testBoosters == null ? Collections.emptyList() : Collections.unmodifiableList(testBoosters);
		loaded = true;
	}

	private int appendPackResource(List<BoosterPackDefinition> target, String resourcePath, boolean markDebugOnly)
	{
		try (Reader reader = openClasspathReader(resourcePath))
		{
			if (reader == null)
			{
				return 0;
			}
			List<BoosterPackDefinition> parsed = gson.fromJson(reader, PACK_LIST_TYPE);
			if (parsed == null || parsed.isEmpty())
			{
				return 0;
			}
			int count = 0;
			for (BoosterPackDefinition booster : parsed)
			{
				if (booster == null)
				{
					continue;
				}
				if (markDebugOnly)
				{
					booster.setDebugOnly(true);
				}
				target.add(booster);
				count++;
			}
			return count;
		}
		catch (IOException | JsonSyntaxException ex)
		{
			log.warn("Failed reading {} from classpath", resourcePath, ex);
			return 0;
		}
	}

	private Reader openClasspathReader(String resourcePath)
	{
		InputStream stream = getClass().getResourceAsStream(resourcePath);
		if (stream == null)
		{
			if ("/Packs.json".equals(resourcePath))
			{
				log.warn("Packs.json resource missing from plugin classpath");
			}
			return null;
		}
		return new InputStreamReader(stream, StandardCharsets.UTF_8);
	}
}
