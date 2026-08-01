package com.bronzemanpvptcg.party;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Party sync for {@code !tcg} chat command: recipients cache the sender's public collection stats.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BpvpChatStatsPartyMessage extends PartyMemberMessage
{
	private long collectionScore;
	private double completionPct;
	private int uniqueOwned;
	private int uniqueFoilOwned;
	private double foilCompletionPct;
	private int totalCardPool;
	private long openedPacks;
	private int totalCardsOwned;
	private boolean customRates;
}
