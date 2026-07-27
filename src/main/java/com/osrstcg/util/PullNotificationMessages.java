package com.osrstcg.util;

import java.util.List;

public final class PullNotificationMessages
{
	private PullNotificationMessages()
	{
	}

	public static String collectionMessage(String playerName, String cardName, boolean newForCollection, boolean foil)
	{
		String who = playerName == null || playerName.trim().isEmpty() ? "Unknown player" : playerName.trim();
		String card = cardName == null ? "" : cardName.trim();
		String duplicatePrefix = newForCollection ? "" : "duplicate ";
		String foilSuffix = foil ? " (foil)" : "";
		return who + " just added " + duplicatePrefix + card + foilSuffix + " to their collection!";
	}

	public static String dinkCollectionMessage(String cardName, boolean newForCollection, boolean foil)
	{
		return collectionMessage("%USERNAME%", cardName, newForCollection, foil);
	}

	public static String dinkPackSummaryMessage(List<String> newCards, List<String> duplicates)
	{
		StringBuilder message = new StringBuilder("%USERNAME% opened a booster pack!");
		appendCardSection(message, "New cards", newCards);
		appendCardSection(message, "Duplicates", duplicates);
		return message.toString();
	}

	private static void appendCardSection(StringBuilder message, String heading, List<String> cards)
	{
		if (cards == null || cards.isEmpty())
		{
			return;
		}
		message.append("\n\n**").append(heading).append("**");
		for (String card : cards)
		{
			if (card == null || card.trim().isEmpty())
			{
				continue;
			}
			message.append("\n- ").append(card);
		}
	}
}
