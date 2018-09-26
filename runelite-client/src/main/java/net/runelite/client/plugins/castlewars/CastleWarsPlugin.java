/*
 * Copyright (c) 2018, cw-dev
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.castlewars;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.LocalPlayerDeath;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.castlewars.data.CWFlag;
import net.runelite.client.plugins.castlewars.data.CWTeam;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Castle Wars",
	description = "Show post-game competitive CW statistics such as cast rate and number of barricades set",
	tags = {"castle wars", "cw", "minigame"}
)
@Slf4j
public class CastleWarsPlugin extends Plugin
{
	private static final String FROZEN_MESSAGE = "You have been frozen!";
	private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
	private static final Set<Integer> WAITING_REGION_IDS = ImmutableSet.of(9776, 9620);

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private StatsTracker statsTracker;

	@Getter(AccessLevel.PACKAGE)
	private int timeChangedOnTick;

	@Getter(AccessLevel.PACKAGE)
	private int minsUntilNextGame;

	private NPC lanthus;
	private CastleWarsTimer timer;
	private boolean inCwGame = false;

	private static String highlight(int value)
	{
		return highlight(Integer.toString(value));
	}

	private static String highlight(String value)
	{
		return new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(value)
			.append(ChatColorType.NORMAL)
			.build();
	}

	@Override
	protected void shutDown()
	{
		inCwGame = false;
		lanthus = null;
		statsTracker.reset();
		resetWaitingTimer();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		final Player localPlayer = client.getLocalPlayer();

		if (!inCwGame || localPlayer == null)
		{
			return;
		}

		final int animID = event.getActor().getAnimation();

		if (localPlayer == event.getActor().getInteracting())
		{
			if (animID == AnimationID.DRAGON_SPEAR_SPEC || animID == AnimationID.ZAM_HASTA_SPEC)
			{
				statsTracker.recordSpeared();
			}
		}
	}

	@Subscribe
	public void onLocalPlayerDeath(LocalPlayerDeath event)
	{
		if (!inCwGame)
		{
			return;
		}

		statsTracker.recordDeath();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		final Player localPlayer = client.getLocalPlayer();

		if (!inCwGame || localPlayer == null || event.getActor() != localPlayer)
		{
			return;
		}

		final Hitsplat hitsplat = event.getHitsplat();

		if (hitsplat != null && hitsplat.getHitsplatType() == Hitsplat.HitsplatType.DAMAGE)
		{
			statsTracker.recordDamageTaken(hitsplat.getAmount());
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event)
	{
		if (!inCwGame)
		{
			return;
		}

		final ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			statsTracker.checkHoldingFlag();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!inCwGame)
		{
			return;
		}

		statsTracker.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkInGame();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!inCwGame)
		{
			return;
		}

		final CWFlag droppedFlag = CWFlag.fromDroppedObjectID(event.getGameObject().getId());
		if (droppedFlag != null)
		{
			statsTracker.onDroppedFlagDespawned(droppedFlag, event.getTile().getWorldLocation());
		}

	}

	@Subscribe
	public void onExperienceChanged(ExperienceChanged event)
	{
		if (!inCwGame || event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}

		statsTracker.recordDamageDealt();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!inCwGame)
		{
			return;
		}

		if (FROZEN_MESSAGE.equals(Text.removeTags(event.getMessage())))
		{
			statsTracker.recordFrozen();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (npc != null && npc.getId() == NpcID.LANTHUS)
		{
			lanthus = npc;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == lanthus)
		{
			lanthus = null;
		}
	}

	public void onLanthusAnnounce(String announcement)
	{
		if (announcement != null && announcement.contains("minute"))
		{
			Matcher m = NUMBER_PATTERN.matcher(announcement);
			if (m.find())
			{
				minsUntilNextGame = Integer.valueOf(m.group());
				timeChangedOnTick = client.getTickCount();
				addWaitingTimer();
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (inCwGame || timeChangedOnTick != 0)
		{
			return;
		}

		final int updatedMinutes = client.getVar(VarPlayer.CW_GAME_MINS);
		if (updatedMinutes != 0 && updatedMinutes != minsUntilNextGame)
		{
			if (minsUntilNextGame != 0)
			{
				timeChangedOnTick = client.getTickCount();
			}
			minsUntilNextGame = updatedMinutes;
			addWaitingTimer();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final Player local = client.getLocalPlayer();
		if (local == null || !WAITING_REGION_IDS.contains(local.getWorldLocation().getRegionID()))
		{
			resetWaitingTimer();
		}
	}

	private void addWaitingTimer()
	{
		if (timer != null || timeChangedOnTick == 0)
		{
			return;
		}

		timer = new CastleWarsTimer(itemManager.getImage(ItemID.DECORATIVE_HELM_4511), this, client);
		infoBoxManager.addInfoBox(timer);
	}

	private void resetWaitingTimer()
	{
		timeChangedOnTick = minsUntilNextGame = 0;
		if (timer != null)
		{
			infoBoxManager.removeInfoBox(timer);
			timer = null;
		}
	}

	private void checkInGame()
	{
		boolean cwHUDVisible;
		CWTeam ourTeam = CWTeam.ofPlayer(client.getLocalPlayer());

		if (ourTeam == null)
		{
			cwHUDVisible = false;
		}
		else
		{
			Widget gameTimeRemaining = client.getWidget(ourTeam.getTimeRemainingWidget());
			cwHUDVisible = gameTimeRemaining != null && !gameTimeRemaining.isHidden();
		}

		if (inCwGame == cwHUDVisible)
		{
			return;
		}

		inCwGame = cwHUDVisible;

		if (inCwGame)
		{
			statsTracker.startGame(client.getWorld(), countValidLobbyPlayers());
		}
		else
		{
			sendGameRecordMessage(statsTracker.finishGame());
			statsTracker.reset();
		}
	}

	private int countValidLobbyPlayers()
	{
		return (int) client.getPlayers()
			.stream()
			.filter(Objects::nonNull)
			.count();
	}

	private void sendGameRecordMessage(GameRecord gameRecord)
	{
		if (gameRecord == null)
		{
			return;
		}

		final boolean tie = gameRecord.getSaraScore() == gameRecord.getZamScore();
		final boolean won = gameRecord.getTeam() == CWTeam.SARA
			? gameRecord.getSaraScore() > gameRecord.getZamScore()
			: gameRecord.getZamScore() > gameRecord.getSaraScore();

		final String summary = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Castle Wars - ")
			.append(highlight(String.format("%dv%d", gameRecord.getTeamSize(), gameRecord.getTeamSize())))
			.append(" - World ")
			.append(String.format("%d", gameRecord.getWorld()))
			.build();

		final String score = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append(gameRecord.getTeam().toString())
			.append(" - ")
			.append(highlight(tie ? "Tie" : won ? "Victory" : "Loss"))
			.append(" (")
			.append(String.format("%d-%d", gameRecord.getSaraScore(), gameRecord.getZamScore()))
			.append(")")
			.build();

		final String damage = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Damage dealt: ")
			.append(highlight(String.format("%.0f", gameRecord.getDamageDealt())))
			.append(" (Max ")
			.append(highlight(String.format("%.0f", gameRecord.getHighestHitDealt())))
			.append(")")
			.append(" Damage taken: ")
			.append(highlight(String.format("%d", gameRecord.getDamageTaken())))
			.append(" (Max ")
			.append(highlight(String.format("%d", gameRecord.getHighestHitTaken())))
			.append(")")
			.build();

		final String player = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Captures: ")
			.append(highlight(gameRecord.getFlagsScored()))
			.append(" Saves: ")
			.append(highlight(gameRecord.getFlagsSafed()))
			.append(" Deaths: ")
			.append(highlight(String.format("%d", gameRecord.getDeaths())))
			.append(" Stuns: ")
			.append(highlight(String.format("%d", gameRecord.getTimesSpeared())))
			.append(" Freezes: ")
			.append(highlight(String.format("%d", gameRecord.getFreezesOnMe())))
			.build();

		send(summary, score, damage, player);
	}

	private void send(String... messages)
	{
		for (String message : messages)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAME)
				.runeLiteFormattedMessage(message)
				.build());
		}
	}
}