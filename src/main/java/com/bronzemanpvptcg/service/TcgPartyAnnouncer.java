package com.bronzemanpvptcg.service;

import com.bronzemanpvptcg.OsrsTcgConfig;
import com.bronzemanpvptcg.model.TcgPublicStats;
import com.bronzemanpvptcg.party.BpvpChatStatsPartyMessage;
import com.bronzemanpvptcg.party.BpvpCollectionSetCompletePartyMessage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.party.PartyService;

/** Sends OSRS TCG party websocket payloads (collection set completion, !tcg stats). */
@Slf4j
@Singleton
public class TcgPartyAnnouncer
{
	private final PartyService partyService;
	private final OsrsTcgConfig config;

	@Inject
	public TcgPartyAnnouncer(PartyService partyService, OsrsTcgConfig config)
	{
		this.partyService = partyService;
		this.config = config;
	}

	public void announceCollectionSetComplete(String collectionDisplayName)
	{
		if (!partyAnnouncementsEnabled())
		{
			return;
		}
		if (collectionDisplayName == null || collectionDisplayName.trim().isEmpty())
		{
			return;
		}
		if (!partyService.isInParty())
		{
			return;
		}
		try
		{
			BpvpCollectionSetCompletePartyMessage message = new BpvpCollectionSetCompletePartyMessage();
			message.setCollectionName(collectionDisplayName.trim());
			partyService.send(message);
		}
		catch (Exception ex)
		{
			log.debug("Could not send collection set party message", ex);
		}
	}

	public void broadcastChatCommandStats(TcgPublicStats stats)
	{
		if (stats == null)
		{
			return;
		}
		if (!partyService.isInParty())
		{
			return;
		}
		try
		{
			BpvpChatStatsPartyMessage message = new BpvpChatStatsPartyMessage();
			message.setCollectionScore(stats.getCollectionScore());
			message.setCompletionPct(stats.getCompletionPct());
			message.setUniqueOwned(stats.getUniqueOwned());
			message.setUniqueFoilOwned(stats.getUniqueFoilOwned());
			message.setFoilCompletionPct(stats.getFoilCompletionPct());
			message.setTotalCardPool(stats.getTotalCardPool());
			message.setOpenedPacks(stats.getOpenedPacks());
			message.setTotalCardsOwned(stats.getTotalCardsOwned());
			message.setCustomRates(stats.isCustomRates());
			partyService.send(message);
		}
		catch (Exception ex)
		{
			log.debug("Could not send !tcg stats party message", ex);
		}
	}

	private boolean partyAnnouncementsEnabled()
	{
		return config.partyAnnounceMythicPulls();
	}
}
