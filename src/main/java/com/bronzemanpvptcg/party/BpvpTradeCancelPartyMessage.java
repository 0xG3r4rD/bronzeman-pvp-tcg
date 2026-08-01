package com.bronzemanpvptcg.party;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Cancels an active trade or declines after the window is open. Invite declines use
 * {@link BpvpTradeInviteResponsePartyMessage} instead.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BpvpTradeCancelPartyMessage extends PartyMemberMessage
{
	private String tradeId;
}
