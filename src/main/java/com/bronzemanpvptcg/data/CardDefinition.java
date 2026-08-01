package com.bronzemanpvptcg.data;

import com.google.gson.annotations.JsonAdapter;
import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class CardDefinition
{
	private String name;
	@JsonAdapter(CategoryListTypeAdapter.class)
	private List<String> category;
	private String imageUrl;
	private Integer level;
	private Long value;
	/** When set, {@code RarityMath.score} uses this instead of the level-derived contribution. */
	private Long overrideScore;
	private String examine;
	private Boolean questItem;
	/** Defence level needed to equip the item; absent means none (wearable at 1 Defence). */
	private Integer defenceRequirement;

	/** Defence requirement as a plain level, treating an absent value as no requirement. */
	public int defenceRequirementLevel()
	{
		return defenceRequirement == null ? 0 : defenceRequirement;
	}

	public List<String> getCategoryTags()
	{
		return category == null ? Collections.emptyList() : category;
	}

	/** First category tag (normalized display), for grouping and logging. */
	public String getPrimaryCategory()
	{
		List<String> tags = getCategoryTags();
		if (tags.isEmpty())
		{
			return "Unknown";
		}
		List<String> parts = CategoryTagUtil.expandCompoundParts(tags.get(0));
		if (parts.isEmpty())
		{
			return "Unknown";
		}
		String canon = CategoryTagUtil.canonicalKey(parts.get(0));
		return CategoryTagUtil.toDisplayLabel(canon);
	}
}
