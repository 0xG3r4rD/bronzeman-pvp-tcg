package com.bronzemanpvptcg;

import com.google.inject.Provides;
import com.bronzemanpvptcg.data.BoosterPackDefinition;
import com.bronzemanpvptcg.data.CardDatabase;
import com.bronzemanpvptcg.data.CardDefinition;
import com.bronzemanpvptcg.data.PackCatalog;
import com.bronzemanpvptcg.model.CardCollectionKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.bronzemanpvptcg.model.OwnedCardInstance;
import com.bronzemanpvptcg.model.TcgPublicStats;
import com.bronzemanpvptcg.overlay.CreditsInfoboxOverlay;
import com.bronzemanpvptcg.overlay.PackRevealInputListener;
import com.bronzemanpvptcg.overlay.PackRevealOverlay;
import com.bronzemanpvptcg.service.CollectionShareService;
import com.bronzemanpvptcg.service.OwnedCardNamesApiService;
import com.bronzemanpvptcg.service.CardPartyTradeService;
import com.bronzemanpvptcg.service.CardPartyTransferService;
import com.bronzemanpvptcg.service.CreditAwardService;
import com.bronzemanpvptcg.service.BankUnlocksButtonService;
import com.bronzemanpvptcg.service.BronzemanEquipLockService;
import com.bronzemanpvptcg.service.GameMessageCreditTracker;
import com.bronzemanpvptcg.service.NpcKillCreditTracker;
import com.bronzemanpvptcg.service.PvpKillCreditTracker;
import com.bronzemanpvptcg.service.CollectionSetCompletionUtil;
import com.bronzemanpvptcg.service.PackOpeningService;
import com.bronzemanpvptcg.service.PackSafeModeService;
import com.bronzemanpvptcg.service.PlayerCombatMonitor;
import com.bronzemanpvptcg.service.RollPoolFilter;
import com.bronzemanpvptcg.party.BpvpCardGiftPartyMessage;
import com.bronzemanpvptcg.party.BpvpCardGiftResponsePartyMessage;
import com.bronzemanpvptcg.party.BpvpChatStatsPartyMessage;
import com.bronzemanpvptcg.party.BpvpCollectionSetCompletePartyMessage;
import com.bronzemanpvptcg.party.BpvpPullPartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeCancelPartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeCommitPartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeInviteAckPartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeInvitePartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeInviteResponsePartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeOfferDeltaPartyMessage;
import com.bronzemanpvptcg.party.BpvpTradeReadyPartyMessage;
import com.bronzemanpvptcg.persist.TcgSaveTrigger;
import com.bronzemanpvptcg.persist.TcgStateLoadResult;
import com.bronzemanpvptcg.persist.TcgStateLoadSource;
import com.bronzemanpvptcg.service.PackRevealSoundService;
import com.bronzemanpvptcg.service.PackRevealService;
import com.bronzemanpvptcg.service.TcgChatStatsShareService;
import com.bronzemanpvptcg.service.TcgPartyAnnouncer;
import com.bronzemanpvptcg.service.TcgPublicStatsCalculator;
import com.bronzemanpvptcg.service.TcgStateService;
import com.bronzemanpvptcg.ui.TcgPanel;
import com.bronzemanpvptcg.ui.collectionalbum.CollectionAlbumManager;
import com.bronzemanpvptcg.ui.trade.TradeWindowManager;
import com.bronzemanpvptcg.ui.save.SaveRestoreManager;
import com.bronzemanpvptcg.util.NumberFormatting;
import com.bronzemanpvptcg.util.TcgPluginGameMessages;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bronzeman PVP TCG",
	description = "Bronzeman-style card collecting: unlock gear by pulling cards, earn packs through PvP kills"
)
public class OsrsTcgPlugin extends Plugin
{
	private static final String TCG_PUBLIC_CHAT_COMMAND = "!btcg";
	private static final Pattern TCG_GIVE_FOIL_SUFFIX = Pattern.compile("(?i)\\s*\\(foil\\)\\s*$");

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ChatMessageManager chatMessageManager;
	@Inject
	private OsrsTcgConfig config;
	@Inject
	private TcgStateService stateService;
	@Inject
	private CardDatabase cardDatabase;
	@Inject
	private PackCatalog packCatalog;
	@Inject
	private CreditAwardService creditAwardService;
	@Inject
	private PackOpeningService packOpeningService;
	@Inject
	private PackRevealService packRevealService;
	@Inject
	private PackRevealSoundService packRevealSoundService;
	@Inject
	private PackRevealOverlay packRevealOverlay;
	@Inject
	private CreditsInfoboxOverlay creditsInfoboxOverlay;
	@Inject
	private PackRevealInputListener packRevealInputListener;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MouseManager mouseManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private TcgPanel tcgPanel;
	@Inject
	private CollectionAlbumManager collectionAlbumManager;
	@Inject
	private EventBus eventBus;
	@Inject
	private NpcKillCreditTracker npcKillCreditTracker;
	@Inject
	private GameMessageCreditTracker gameMessageCreditTracker;
	@Inject
	private PvpKillCreditTracker pvpKillCreditTracker;
	@Inject
	private BronzemanEquipLockService bronzemanEquipLockService;
	@Inject
	private RollPoolFilter rollPoolFilter;
	@Inject
	private BankUnlocksButtonService bankUnlocksButtonService;
	@Inject
	private PartyService partyService;
	@Inject
	private WSClient wsClient;
	@Inject
	private CardPartyTransferService cardPartyTransferService;
	@Inject
	private CardPartyTradeService cardPartyTradeService;
	@Inject
	private TradeWindowManager tradeWindowManager;
	@Inject
	private SaveRestoreManager saveRestoreManager;
	@Inject
	private ChatCommandManager chatCommandManager;
	@Inject
	private ScheduledExecutorService scheduledExecutorService;
	@Inject
	private TcgPublicStatsCalculator tcgPublicStatsCalculator;
	@Inject
	private TcgChatStatsShareService tcgChatStatsShareService;
	@Inject
	private TcgPartyAnnouncer tcgPartyAnnouncer;
	@Inject
	private PlayerCombatMonitor playerCombatMonitor;
	@Inject
	private PackSafeModeService packSafeModeService;
	@Inject
	private CollectionShareService collectionShareService;
	@Inject
	private OwnedCardNamesApiService ownedCardNamesApiService;

	private NavigationButton navigationButton;
	private boolean fileBackupLoadUsedThisSession;

	@Override
	protected void startUp()
	{
		cardDatabase.load();
		packCatalog.load();
		TcgStateLoadResult loadResult = stateService.load();
		applyLoadedProfileState(loadResult);
		announceLoadResult(loadResult);
		log.info("OSRS TCG plugin started. Credits={}, ownedCards={}, cardDefinitions={}",
			NumberFormatting.format(stateService.getState().getEconomyState().getCredits()),
			NumberFormatting.format(stateService.getState().getCollectionState().getOwnedCards().size()),
			NumberFormatting.format(cardDatabase.size()));
		log.info("Card category distribution: {}", cardDatabase.categoryCounts());
		navigationButton = NavigationButton.builder()
			.tooltip("Bronzeman PVP TCG")
			.icon(buildPanelIcon())
			.priority(5)
			.panel(tcgPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(packRevealOverlay);
		overlayManager.add(creditsInfoboxOverlay);
		mouseManager.registerMouseListener(packRevealInputListener);
		mouseManager.registerMouseWheelListener(packRevealInputListener);
		keyManager.registerKeyListener(packRevealInputListener);
		eventBus.register(creditAwardService);
		creditAwardService.onPluginStarted();
		// PvP-only economy: NpcKillCreditTracker and GameMessageCreditTracker stay unregistered.
		eventBus.register(pvpKillCreditTracker);
		eventBus.register(bronzemanEquipLockService);
		eventBus.register(bankUnlocksButtonService);
		eventBus.register(cardPartyTransferService);
		eventBus.register(cardPartyTradeService);
		eventBus.register(playerCombatMonitor);
		eventBus.register(packSafeModeService);
		wsClient.registerMessage(BpvpPullPartyMessage.class);
		wsClient.registerMessage(BpvpCollectionSetCompletePartyMessage.class);
		wsClient.registerMessage(BpvpCardGiftPartyMessage.class);
		wsClient.registerMessage(BpvpCardGiftResponsePartyMessage.class);
		wsClient.registerMessage(BpvpChatStatsPartyMessage.class);
		wsClient.registerMessage(BpvpTradeInvitePartyMessage.class);
		wsClient.registerMessage(BpvpTradeInviteAckPartyMessage.class);
		wsClient.registerMessage(BpvpTradeInviteResponsePartyMessage.class);
		wsClient.registerMessage(BpvpTradeOfferDeltaPartyMessage.class);
		wsClient.registerMessage(BpvpTradeReadyPartyMessage.class);
		wsClient.registerMessage(BpvpTradeCancelPartyMessage.class);
		wsClient.registerMessage(BpvpTradeCommitPartyMessage.class);
		chatCommandManager.registerCommandAsync(
			TCG_PUBLIC_CHAT_COMMAND, this::lookupTcgPublicStatsChatCommand, this::submitTcgPublicStatsChatCommand);
		tcgPanel.start();
		stateService.setRewardTuningFlushBeforeCredits(tcgPanel::flushRewardTuningDraftToState);
		collectionShareService.setStatusListener(() -> SwingUtilities.invokeLater(tcgPanel::updateWebShareLiveIndicator));
		collectionShareService.start();
		ownedCardNamesApiService.start();
		tcgPanel.refresh();
		TcgPluginGameMessages.setPrefixColor(config.chatPrefixColor());
	}

	@Override
	protected void shutDown()
	{
		// Commit deferred pack pulls before the unload checkpoint so they are not lost.
		packRevealService.reset();

		// Flush skill baselines + write RSProfile and local tcg.save / snapshot before teardown.
		creditAwardService.flushSkillBaselineForPersist();
		stateService.saveFullCheckpoint(TcgSaveTrigger.PLUGIN_UNLOAD);

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		eventBus.unregister(creditAwardService);
		eventBus.unregister(pvpKillCreditTracker);
		eventBus.unregister(bankUnlocksButtonService);
		eventBus.unregister(bronzemanEquipLockService);
		bronzemanEquipLockService.shutdown();
		eventBus.unregister(cardPartyTransferService);
		eventBus.unregister(cardPartyTradeService);
		eventBus.unregister(playerCombatMonitor);
		eventBus.unregister(packSafeModeService);
		playerCombatMonitor.reset();
		wsClient.unregisterMessage(BpvpPullPartyMessage.class);
		wsClient.unregisterMessage(BpvpCollectionSetCompletePartyMessage.class);
		wsClient.unregisterMessage(BpvpCardGiftPartyMessage.class);
		wsClient.unregisterMessage(BpvpCardGiftResponsePartyMessage.class);
		wsClient.unregisterMessage(BpvpChatStatsPartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeInvitePartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeInviteAckPartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeInviteResponsePartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeOfferDeltaPartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeReadyPartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeCancelPartyMessage.class);
		wsClient.unregisterMessage(BpvpTradeCommitPartyMessage.class);
		chatCommandManager.unregisterCommand(TCG_PUBLIC_CHAT_COMMAND);
		npcKillCreditTracker.shutdown();
		overlayManager.remove(packRevealOverlay);
		overlayManager.remove(creditsInfoboxOverlay);
		mouseManager.unregisterMouseListener(packRevealInputListener);
		mouseManager.unregisterMouseWheelListener(packRevealInputListener);
		keyManager.unregisterKeyListener(packRevealInputListener);
		packRevealSoundService.hardStop();
		collectionAlbumManager.dispose();
		tradeWindowManager.dispose();
		saveRestoreManager.dispose();
		stateService.setRewardTuningFlushBeforeCredits(null);
		collectionShareService.setStatusListener(null);
		collectionShareService.stop();
		ownedCardNamesApiService.stop();
		tcgPanel.stop();
		log.info("OSRS TCG plugin stopped");
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// Commit deferred pack pulls, then write RSProfile keys synchronously before ConfigManager's
		// ClientShutdown handler (priority -100) runs sendConfig(); an async Future finishes too late.
		packRevealService.reset();
		stateService.saveFullCheckpoint(TcgSaveTrigger.CLIENT_SHUTDOWN);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		creditAwardService.onStatChanged(event);
		tcgPanel.refresh();
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		creditAwardService.onFakeXpDrop(event);
		tcgPanel.refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		creditAwardService.onGameStateChanged(event);
		GameState gs = event.getGameState();
		if (gs == GameState.LOGIN_SCREEN)
		{
			fileBackupLoadUsedThisSession = false;
			packRevealService.reset();
			tcgPanel.clearPackRevealSidebarFreeze();
			stateService.saveFullCheckpoint(TcgSaveTrigger.LOGOUT);
			collectionShareService.onLoggedOut();
		}
		else if (gs == GameState.HOPPING)
		{
			// Credits and non-collection state stay in memory until logout/shutdown checkpoint.
		}
		else if (gs == GameState.LOGGED_IN)
		{
			collectionShareService.onLoginOrProfileReady();
		}
		tcgPanel.refresh();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event == null || !"bronzemanpvptcg".equals(event.getGroup()))
		{
			return;
		}
		if ("webShareEnabled".equals(event.getKey()) || "webShareApiKey".equals(event.getKey()))
		{
			collectionShareService.onConfigChanged();
			tcgPanel.updateWebShareLiveIndicator();
		}
		else if ("chatPrefixColor".equals(event.getKey()))
		{
			TcgPluginGameMessages.setPrefixColor(config.chatPrefixColor());
		}
		else if ("hardModeRate".equals(event.getKey()) || "hardModeGpPerPoint".equals(event.getKey())
			|| "itemWhitelist".equals(event.getKey()))
		{
			tcgPanel.refresh();
		}
		else if ("hardMode".equals(event.getKey()))
		{
			tcgPanel.refresh();
			queueGameMessage(TcgPluginGameMessages.withPrefix(config.hardMode()
				? "Hard mode on: PvP kills pay 250 credits per 100k of loot value (1M per pack)."
				: "Hard mode off: PvP kills pay a full booster pack each."));
		}
		else if ("defenceLevel".equals(event.getKey()))
		{
			// Roll pool size and collection totals both change with the cap.
			tcgPanel.refresh();
			queueGameMessage(TcgPluginGameMessages.withPrefix(String.format(
				"Defence level set to %s: %s of %s cards are pullable.",
				config.defenceLevel(),
				NumberFormatting.format(rollPoolFilter.filterRollPool(cardDatabase.getCards()).size()),
				NumberFormatting.format(cardDatabase.size()))));
		}
	}

	@Subscribe
	public void onBpvpPullPartyMessage(BpvpPullPartyMessage message)
	{
		if (!config.partyAnnounceMythicPulls())
		{
			return;
		}
		if (message == null)
		{
			return;
		}
		String cardName = message.getCardName();
		if (cardName == null || cardName.trim().isEmpty())
		{
			return;
		}
		PartyMember localMember = partyService.getLocalMember();
		if (localMember != null && message.getMemberId() == localMember.getMemberId())
		{
			return;
		}
		PartyMember author = partyService.getMemberById(message.getMemberId());
		String who = author != null && author.getDisplayName() != null && !author.getDisplayName().trim().isEmpty()
			? author.getDisplayName().trim()
			: "A party member";
		String trimmed = cardName.trim();
		Color rarity = cardDatabase.chatRarityColorForCardName(trimmed);
		String formatted = TcgPluginGameMessages.formatPrefixedSomeoneAddedCollection(
			who, trimmed, message.isNewForCollection(), message.isFoil(), rarity);
		String plain = TcgPluginGameMessages.plainPrefixedSomeoneAddedCollection(
			who, trimmed, message.isNewForCollection(), message.isFoil());
		TcgPluginGameMessages.queueFormattedGameMessage(chatMessageManager, formatted, plain);
	}

	@Subscribe
	public void onBpvpCollectionSetCompletePartyMessage(BpvpCollectionSetCompletePartyMessage message)
	{
		if (!config.partyAnnounceMythicPulls())
		{
			return;
		}
		if (message == null)
		{
			return;
		}
		String collectionName = message.getCollectionName();
		if (collectionName == null || collectionName.trim().isEmpty())
		{
			return;
		}
		PartyMember localMember = partyService.getLocalMember();
		if (localMember != null && message.getMemberId() == localMember.getMemberId())
		{
			return;
		}
		PartyMember author = partyService.getMemberById(message.getMemberId());
		String who = author != null && author.getDisplayName() != null && !author.getDisplayName().trim().isEmpty()
			? author.getDisplayName().trim()
			: "A party member";
		TcgPluginGameMessages.queuePrefixedGameMessage(chatMessageManager,
			String.format(Locale.US, "%s just finished %s!", who, collectionName.trim()));
	}

	@Subscribe
	public void onBpvpChatStatsPartyMessage(BpvpChatStatsPartyMessage message)
	{
		if (message == null)
		{
			return;
		}
		tcgChatStatsShareService.ingestPartyMessage(message, partyService);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		TcgStateLoadResult loadResult = stateService.load();
		applyLoadedProfileState(loadResult);
		announceLoadResult(loadResult);
		collectionShareService.onLoginOrProfileReady();
	}

	/** After {@link TcgStateService#load()} on login / profile switch; clears UI when debug-tainted saves are reset. */
	private void applyLoadedProfileState(TcgStateLoadResult loadResult)
	{
		creditAwardService.resetExperienceCreditBaseline();
		if (loadResult != null && loadResult.isDebugResetOnLoad())
		{
			packRevealService.discardActiveReveal();
			tcgPanel.clearPackRevealSidebarFreeze();
			tcgPanel.syncRewardDraftFromPersistent();
			tcgPanel.resetSessionUi();
			queueGameMessage(
				"[Bronzeman PVP TCG] This profile was saved with debug mode on; collection and credits were reset.");
		}
		else
		{
			tcgPanel.syncRewardDraftFromPersistent();
			tcgPanel.refresh();
		}
	}

	private void announceLoadResult(TcgStateLoadResult loadResult)
	{
		if (loadResult == null)
		{
			return;
		}

		if (loadResult.isConfigLoadFailed())
		{
			queueGameMessage("[Bronzeman PVP TCG] Could not load saved progress from profile; trying disk saves.");
		}

		if (loadResult.isAllBackupsFailed())
		{
			queueGameMessage("[Bronzeman PVP TCG] Could not restore progress from any save.");
			return;
		}

		if (loadResult.getSource() == TcgStateLoadSource.DISK)
		{
			queueGameMessage("[Bronzeman PVP TCG] Restored progress from tcg.save.");
		}
		else if (loadResult.getSource() == TcgStateLoadSource.DISK_SNAPSHOT)
		{
			queueGameMessage("[Bronzeman PVP TCG] Restored progress from a disk snapshot.");
		}
		else if (loadResult.getSource() == TcgStateLoadSource.CONFIG && !loadResult.isDebugResetOnLoad())
		{
			queueLoadSuccessMessage();
		}
	}

	private void queueLoadSuccessMessage()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		queueGameMessage("[Bronzeman PVP TCG] Collection successfully loaded.");
	}

	private void queueGameMessage(String message)
	{
		if (client == null || clientThread == null || message == null || message.isEmpty())
		{
			return;
		}

		clientThread.invokeLater(() ->
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (event == null)
		{
			return;
		}
		String cmd = event.getCommand();
		// Commands are namespaced "btcg-*" so they cannot collide with the upstream OSRS TCG plugin.
		if (cmd == null || cmd.length() < 5 || !cmd.regionMatches(true, 0, "btcg", 0, 4))
		{
			return;
		}

		if ("btcg-pvp".equalsIgnoreCase(cmd))
		{
			if (!stateService.isDebugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] That command requires Overview debug mode.",
					null);
				return;
			}
			String[] args = event.getArguments();
			long lootValue = 100_000L;
			if (args != null && args.length > 0)
			{
				try
				{
					lootValue = Long.parseLong(args[0].trim().replace(",", "").replace("_", ""));
				}
				catch (NumberFormatException ex)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"[Bronzeman PVP TCG] Usage: ::btcg-pvp <loot value>", null);
					return;
				}
			}
			pvpKillCreditTracker.simulateKill(lootValue);
			return;
		}

		// Read-only diagnostic, so it is not gated behind debug mode like the state-changing commands.
		if ("btcg-escapes".equalsIgnoreCase(cmd))
		{
			List<String> escapes = bronzemanEquipLockService.findUnmatchedEquipableItems();
			log.info("[Bronzeman PVP TCG] {} equipable items resolve to no card: {}",
				escapes.size(), escapes);
			queueGameMessage(TcgPluginGameMessages.withPrefix(String.format(
				"%d equipable items have no card (full list in the client log).", escapes.size())));
			int shown = Math.min(escapes.size(), 20);
			if (shown > 0)
			{
				queueGameMessage(TcgPluginGameMessages.withPrefix(
					"First " + shown + ": " + String.join(", ", escapes.subList(0, shown))));
			}
			return;
		}

		if ("btcg-set".equalsIgnoreCase(cmd))
		{
			if (!stateService.isDebugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] That command requires Overview debug mode.",
					null);
				return;
			}
			handleSetCreditsCommand(event);
			return;
		}

		if ("btcg-reset".equalsIgnoreCase(cmd))
		{
			handleResetCommand();
			return;
		}

		if ("btcg-give".equalsIgnoreCase(cmd))
		{
			if (!stateService.isDebugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] That command requires Overview debug mode.",
					null);
				return;
			}
			handleGiveCardCommand(event);
			return;
		}

		if ("btcg-apex".equalsIgnoreCase(cmd))
		{
			if (!stateService.isDebugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] That command requires Overview debug mode.",
					null);
				return;
			}
			handleOpenFirstBoosterCommand(true);
			return;
		}

		if ("btcg-complete".equalsIgnoreCase(cmd))
		{
			if (!stateService.isDebugLogging())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] That command requires Overview debug mode.",
					null);
				return;
			}
			handleCompleteAlbumCommand();
			return;
		}

		if ("btcg-open".equalsIgnoreCase(cmd))
		{
			handleOpenFirstBoosterCommand(false);
			return;
		}

		if ("btcg-load".equalsIgnoreCase(cmd))
		{
			handleLoadDiskSaveCommand();
			return;
		}

		if ("btcg-save".equalsIgnoreCase(cmd))
		{
			handleSaveCheckpointCommand();
		}
	}

	private void handleSaveCheckpointCommand()
	{
		tcgPanel.flushRewardTuningDraftToState();
		if (stateService.saveCheckpoint(TcgSaveTrigger.MANUAL))
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				String.format(Locale.US,
					"[Bronzeman PVP TCG] Saved checkpoint. Credits: %s, cards: %s.",
					NumberFormatting.format(stateService.getState().getEconomyState().getCredits()),
					NumberFormatting.format(stateService.getState().getCollectionState().getOwnedInstances().size())),
				null);
			return;
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"[Bronzeman PVP TCG] Failed to save checkpoint.", null);
	}

	private void handleLoadDiskSaveCommand()
	{
		if (fileBackupLoadUsedThisSession)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] ::btcg-load can only be used once per login session.", null);
			return;
		}

		saveRestoreManager.showPicker(this::applyRestoredDiskSave);
	}

	private void applyRestoredDiskSave(String profileDirId, String fileName)
	{
		clientThread.invoke(() ->
		{
			if (fileBackupLoadUsedThisSession)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] ::btcg-load can only be used once per login session.", null);
				return;
			}

			if (!stateService.restoreFromDiskFile(profileDirId, fileName))
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Bronzeman PVP TCG] Failed to restore the selected save.", null);
				return;
			}

			fileBackupLoadUsedThisSession = true;
			// Collection/economy come from the save; skill baselines become this profile's live stats.
			creditAwardService.rebaseExperienceCreditBaselineToCurrentStats();
			stateService.saveCheckpoint(TcgSaveTrigger.LOAD);
			packRevealService.discardActiveReveal();
			tcgPanel.clearPackRevealSidebarFreeze();
			tcgPanel.syncRewardDraftFromPersistent();
			tcgPanel.refresh();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				String.format(Locale.US,
					"[Bronzeman PVP TCG] Loaded disk save. Credits: %s, cards: %s.",
					NumberFormatting.format(stateService.getState().getEconomyState().getCredits()),
					NumberFormatting.format(stateService.getState().getCollectionState().getOwnedInstances().size())),
				null);
		});
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() != creditsInfoboxOverlay)
		{
			return;
		}

		OverlayMenuEntry entry = event.getEntry();
		if (entry == null || !CreditsInfoboxOverlay.MENU_OPTION_OPEN.equals(entry.getOption()))
		{
			return;
		}

		String target = entry.getTarget();
		for (BoosterPackDefinition booster : packCatalog.getVisibleBoosters(stateService.isDebugLogging()))
		{
			if (CreditsInfoboxOverlay.packMenuTarget(booster).equals(target))
			{
				openBooster(booster, false);
				return;
			}
		}
	}

	private void handleOpenFirstBoosterCommand(boolean forcedApex)
	{
		List<BoosterPackDefinition> visibleBoosters = packCatalog.getVisibleBoosters(stateService.isDebugLogging());
		if (visibleBoosters.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Bronzeman PVP TCG] No booster packs loaded.", null);
			return;
		}

		openBooster(visibleBoosters.get(0), forcedApex);
	}

	private void openBooster(BoosterPackDefinition booster, boolean forcedApex)
	{
		if (booster == null)
		{
			return;
		}
		if (packRevealService.isActive())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Finish the current pack reveal first.", null);
			return;
		}

		tcgPanel.beginPackRevealSidebarFreeze();
		HashSet<CardCollectionKey> preOwned = new HashSet<>(stateService.getState().getCollectionState().getOwnedCards().keySet());
		boolean showScrollWheelHint = stateService.getState().getEconomyState().getOpenedPacks() == 0L;
		var result = forcedApex
			? packOpeningService.buyAndOpenApexPackForDebug(booster)
			: packOpeningService.buyAndOpenPack(booster);
		if (!result.isSuccess())
		{
			tcgPanel.clearPackRevealSidebarFreeze();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Bronzeman PVP TCG] " + result.getMessage(), null);
			tcgPanel.refresh();
			return;
		}

		String openedLine = forcedApex
			? String.format(Locale.US, "[Bronzeman PVP TCG] Opened apex pack for %s credits. New balance: %s. Pulled %s cards.",
				NumberFormatting.format(result.getPackPrice()), NumberFormatting.format(result.getCreditsAfter()),
				NumberFormatting.format(result.getPulls().size()))
			: String.format(Locale.US, "[Bronzeman PVP TCG] Opened pack for %s credits. New balance: %s. Pulled %s cards.",
				NumberFormatting.format(result.getPackPrice()), NumberFormatting.format(result.getCreditsAfter()),
				NumberFormatting.format(result.getPulls().size()));
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", openedLine, null);
		packRevealService.startReveal(result.getPulls(), preOwned, result.getBoosterDisplayName(),
			result.getBoosterPackId(), showScrollWheelHint, result.isApexPack());
		tcgPanel.refresh();
	}

	private void handleCompleteAlbumCommand()
	{
		cardDatabase.load();
		Set<String> catalogNames = new LinkedHashSet<>();
		for (CardDefinition card : cardDatabase.getCards())
		{
			if (card == null || card.getName() == null)
			{
				continue;
			}
			String name = card.getName().trim();
			if (!name.isEmpty())
			{
				catalogNames.add(name);
			}
		}

		if (catalogNames.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] No cards loaded from Card.json.", null);
			return;
		}

		String who = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? Text.sanitize(client.getLocalPlayer().getName())
			: "";
		String provenance = OwnedCardInstance.withDebugPullMetadataPrefix(who);
		long now = System.currentTimeMillis();

		Map<CardCollectionKey, Integer> ownedBefore = stateService.copyOwnedCardsSnapshot();
		int added = stateService.addOneOfEachCatalogCard(new ArrayList<>(catalogNames), provenance, now);

		if (tcgPartyAnnouncer != null && added > 0)
		{
			Map<CardCollectionKey, Integer> ownedAfter = stateService.getState().getCollectionState().getOwnedCards();
			List<CardDefinition> rollPool = rollPoolFilter.filterRollPool(cardDatabase.getCards());
			for (String category : CollectionSetCompletionUtil.newlyCompletedPrimaryCategories(ownedBefore, ownedAfter, rollPool))
			{
				tcgPartyAnnouncer.announceCollectionSetComplete(category);
			}
		}

		collectionAlbumManager.refreshIfVisible();
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format(Locale.US, "[Bronzeman PVP TCG] Added 1× each catalog card (%s cards).",
				NumberFormatting.format(added)),
			null);
		tcgPanel.refresh();
	}

	private void handleGiveCardCommand(CommandExecuted event)
	{
		String[] arguments = event.getArguments();
		if (arguments == null || arguments.length == 0)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Provide a card name, optionally followed by (foil).", null);
			return;
		}

		String joined = Arrays.stream(arguments)
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.joining(" "));
		if (joined.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Provide a card name, optionally followed by (foil).", null);
			return;
		}

		boolean foil = TCG_GIVE_FOIL_SUFFIX.matcher(joined).find();
		String cardQuery = TCG_GIVE_FOIL_SUFFIX.matcher(joined).replaceFirst("").trim();
		if (cardQuery.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Provide a card name, optionally followed by (foil).", null);
			return;
		}

		Optional<String> resolved = cardDatabase.getCards().stream()
			.filter(Objects::nonNull)
			.map(CardDefinition::getName)
			.filter(Objects::nonNull)
			.filter(n -> n.trim().equalsIgnoreCase(cardQuery))
			.findFirst()
			.map(n -> n.trim());

		if (!resolved.isPresent())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				String.format(Locale.US, "[Bronzeman PVP TCG] No card named \"%s\" in Card.json.", cardQuery), null);
			return;
		}

		String canonicalName = resolved.get();
		String who = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? Text.sanitize(client.getLocalPlayer().getName())
			: "";
		stateService.addCard(canonicalName, foil, 1, OwnedCardInstance.withDebugPullMetadataPrefix(who),
			System.currentTimeMillis());
		collectionAlbumManager.refreshIfVisible();
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format(Locale.US, "[Bronzeman PVP TCG] Gave 1× %s%s.", canonicalName, foil ? " (foil)" : ""), null);
		tcgPanel.refresh();
	}

	private void handleSetCreditsCommand(CommandExecuted event)
	{
		String[] arguments = event.getArguments();
		if (arguments == null || arguments.length < 1)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Provide a credit amount.", null);
			return;
		}

		String amountRaw = String.join("", Arrays.asList(arguments)).trim();
		long amount;
		try
		{
			amount = Long.parseLong(amountRaw);
		}
		catch (NumberFormatException ex)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Invalid credit amount.", null);
			return;
		}

		if (amount < 0)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Bronzeman PVP TCG] Credits cannot be negative.", null);
			return;
		}

		long currentCredits = stateService.getCredits();
		if (amount > currentCredits)
		{
			stateService.addCredits(amount - currentCredits);
		}
		else if (amount < currentCredits)
		{
			stateService.spendCredits(currentCredits - amount);
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			String.format("[Bronzeman PVP TCG] Credits set to %s.", NumberFormatting.format(stateService.getCredits())), null);
		tcgPanel.refresh();
	}

	private void handleResetCommand()
	{
		tcgPanel.performCollectionReset();
	}

	private void lookupTcgPublicStatsChatCommand(ChatMessage chatMessage, String message)
	{
		if (!message.trim().equalsIgnoreCase(TCG_PUBLIC_CHAT_COMMAND))
		{
			return;
		}

		final String player;
		if (ChatMessageType.PRIVATECHATOUT.equals(chatMessage.getType()))
		{
			if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
			{
				return;
			}
			player = Text.sanitize(client.getLocalPlayer().getName());
		}
		else
		{
			player = Text.sanitize(chatMessage.getName());
		}

		TcgPublicStats stats = tcgChatStatsShareService.getBySanitizedPlayerName(player);
		if (stats == null)
		{
			return;
		}

		String response = tcgChatStatsShareService.buildColoredLine(stats);
		MessageNode messageNode = chatMessage.getMessageNode();
		if (messageNode == null)
		{
			return;
		}
		messageNode.setRuneLiteFormatMessage(response);
		client.refreshChat();
	}

	private boolean submitTcgPublicStatsChatCommand(ChatInput chatInput, String value)
	{
		if (!value.trim().equalsIgnoreCase(TCG_PUBLIC_CHAT_COMMAND))
		{
			return false;
		}
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return false;
		}

		TcgPublicStats stats = tcgPublicStatsCalculator.computeLive();
		tcgChatStatsShareService.putSanitizedPlayerName(Text.sanitize(client.getLocalPlayer().getName()), stats);

		scheduledExecutorService.execute(() ->
		{
			try
			{
				tcgPartyAnnouncer.broadcastChatCommandStats(stats);
			}
			catch (Exception ex)
			{
				log.debug("!tcg party broadcast failed", ex);
			}
			finally
			{
				chatInput.resume();
			}
		});
		return true;
	}

	private BufferedImage buildPanelIcon()
	{
		return ImageUtil.loadImageResource(OsrsTcgPlugin.class, "/panel_icon.png");
	}

	@Provides
	OsrsTcgConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsTcgConfig.class);
	}
}
