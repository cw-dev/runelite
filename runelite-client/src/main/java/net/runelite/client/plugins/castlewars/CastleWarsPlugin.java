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

import com.google.common.eventbus.Subscribe;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GraphicID;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import static net.runelite.api.Skill.HITPOINTS;
import static net.runelite.api.Skill.MAGIC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.LocalPlayerDeath;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Castle Wars",
	description = "Show post-game competitive CW statistics such as cast rate and number of barricades set",
	tags = {"castle wars", "cw", "minigame"},
	enabledByDefault = false
)
@Slf4j
public class CastleWarsPlugin extends Plugin
{
	private static final String FROZEN_MESSAGE = "You have been frozen!";

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private StatsTracker statsTracker;

	private boolean inCwGame = false;

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (!inCwGame || localPlayer == null)
		{
			return;
		}

		int animID = event.getActor().getAnimation();
		if (localPlayer.equals(event.getActor()))
		{
			if (animID == AnimationID.MAGIC_ANCIENT_BARRAGE)
			{
				statsTracker.onBarrageCast(client.getTickCount());
			}
			else if (animID == AnimationID.MAGIC_STANDARD_TELEPORT)
			{
				statsTracker.recordDeath();
			}
		}
		else if (localPlayer.equals(event.getActor().getInteracting()))
		{
			if (animID == AnimationID.DRAGON_SPEAR_SPEC || animID == AnimationID.ZAM_HASTA_SPEC)
			{
				statsTracker.recordSpeared();
			}
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (!inCwGame || localPlayer == null || !localPlayer.equals(event.getActor()))
		{
			return;
		}

		int graphicID = event.getActor().getGraphic();
		if (graphicID == GraphicID.ICE_RUSH ||
			graphicID == GraphicID.ICE_BURST ||
			graphicID == GraphicID.ICE_BLITZ ||
			graphicID == GraphicID.ICE_BARRAGE)
		{
			statsTracker.recordCastOnMe();
		}
		else if (graphicID == GraphicID.SPLASH)
		{
			statsTracker.recordSplashOnMe();
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
		Player localPlayer = client.getLocalPlayer();
		if (!inCwGame || localPlayer == null || event.getActor() != localPlayer)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
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

		ItemContainer container = event.getItemContainer();
		if (container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			statsTracker.checkHoldingFlag(client.getTickCount());
		}
		else if (container == client.getItemContainer(InventoryID.INVENTORY))
		{
			statsTracker.checkItemUsage(container, client.getTickCount());
		}
	}


	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!inCwGame)
		{
			return;
		}

		statsTracker.onMenuOptionClicked(event, client.getTickCount());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkInGame();
		if (!inCwGame)
		{
			return;
		}

		statsTracker.checkTindedCades();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!inCwGame)
		{
			return;
		}

		CWFlag droppedFlag = CWFlag.fromDroppedObjectID(event.getGameObject().getId());
		if (droppedFlag != null)
		{
			statsTracker.onDroppedFlagDespawned(droppedFlag, client.getTickCount(), event.getTile().getWorldLocation());
		}

	}

	@Subscribe
	public void onExperienceChanged(ExperienceChanged event)
	{
		if (!inCwGame)
		{
			return;
		}

		if (event.getSkill() == MAGIC)
		{
			statsTracker.onMageXpChanged(client.getTickCount());
		}
		else if (event.getSkill() == HITPOINTS)
		{
			statsTracker.onHPXpChanged();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!inCwGame)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		if (message.equals(FROZEN_MESSAGE))
		{
			statsTracker.recordFrozen();
		}

	}

	private void checkInGame()
	{
		boolean cwHUDVisible;
		CWTeam ourTeam = CWTeam.ofPlayer(client.getLocalPlayer());
		if (ourTeam == null || ourTeam.equals(CWTeam.NONE))
		{
			cwHUDVisible = false;
		}
		else
		{
			Widget gameTimeRemaining = client.getWidget(ourTeam.getTimeRemainingWidget());
			cwHUDVisible = gameTimeRemaining != null && !gameTimeRemaining.isHidden();
		}

		if (inCwGame != cwHUDVisible)
		{
			inCwGame = cwHUDVisible;
			if (inCwGame)
			{
				statsTracker.onJoinGame(countValidLobbyPlayers(), wearingCWBrace(client.getLocalPlayer()), client.getWorld());
			}
			else
			{
				sendGameRecordMessage(statsTracker.onLeaveGame());
				statsTracker.reset();
			}
		}
	}

	private int countValidLobbyPlayers()
	{
		return (int) client.getPlayers()
			.stream()
			.filter(Objects::nonNull)
			//.filter(this::wearingCWBrace)
			.count();
	}

	private boolean wearingCWBrace(Player p)
	{
		PlayerComposition playerComposition = p.getPlayerComposition();
		if (playerComposition == null)
		{
			return false;
		}

		int glovesID = playerComposition.getEquipmentId(KitType.HANDS);
		return glovesID == ItemID.CASTLE_WARS_BRACELET1 ||
			glovesID == ItemID.CASTLE_WARS_BRACELET2 ||
			glovesID == ItemID.CASTLE_WARS_BRACELET3;
	}

	private void sendGameRecordMessage(GameRecord gameRecord)
	{
		if (gameRecord == null)
		{
			return;
		}

		String gameSummary = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Zam ")
			.append(higlight(gameRecord.getZamScore()))
			.append(" - ")
			.append(higlight(gameRecord.getSaraScore()))
			.append(" Sara | ")
			.append(higlight(String.format("%dv%d", gameRecord.getTeamSize(), gameRecord.getTeamSize())))
			.append(String.format(" [w%d]", gameRecord.getWorld()))
			.build();

		String cwSummary = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Scored: ")
			.append(higlight(gameRecord.getFlagsScored()))
			.append(". Safed: ")
			.append(higlight(gameRecord.getFlagsSafed()))
			.append(". Cades Set: ")
			.append(higlight(gameRecord.getCadesSet()))
			.append(",  Tinded: ")
			.append(higlight(gameRecord.getCadesTinded()))
			.append(",  Exploded: ")
			.append(higlight(gameRecord.getCadesExploded()))
			.append(",  Bucketed: ")
			.append(higlight(gameRecord.getCadesBucketed()))
			.build();

		String offensiveCombat = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Dealt ")
			.append(higlight(String.format("~%.0f", gameRecord.getDamageDealt())))
			.append(" damage (Max ")
			.append(higlight(String.format("~%.0f", gameRecord.getHighestHitDealt())))
			.append("). Cast rate: ")
			.append(higlight(String.format("%.2f%%", gameRecord.getCastRate())))
			.append(" ( ")
			.append(higlight(gameRecord.getTotalCastAttempts()))
			.append(" casts, ")
			.append(higlight(gameRecord.getSplashes()))
			.append(" splashes)")
			.build();

		String defensiveCombat = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Took ")
			.append(higlight(String.format("%d", gameRecord.getDamageTaken())))
			.append(" damage (Max ")
			.append(higlight(String.format("%d", gameRecord.getHighestHitTaken())))
			.append("). Died ")
			.append(higlight(String.format("%dx", gameRecord.getDeaths())))
			.append(". Spr'd: ")
			.append(higlight(String.format("%dx", gameRecord.getTimesSpeared())))
			.append(". Frozen ")
			.append(higlight(String.format("%dx", gameRecord.getFrozenCount())))
			.append(". Casted ")
			.append(higlight(String.format("%dx", gameRecord.getTotalCastsOnMe())))
			.append(" (")
			.append(higlight(String.format("%.0f%%", gameRecord.getSplashRate())))
			.append(" splashes)")
			.build();

		send(gameSummary, cwSummary, offensiveCombat, defensiveCombat);
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

	private String higlight(int value)
	{
		return higlight(Integer.toString(value));
	}

	private String higlight(String value)
	{
		return new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(value)
			.append(ChatColorType.NORMAL)
			.build();
	}
}