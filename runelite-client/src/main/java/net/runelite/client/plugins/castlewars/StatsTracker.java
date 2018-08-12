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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import static net.runelite.api.MenuAction.ITEM_DROP;
import static net.runelite.api.MenuAction.ITEM_USE_ON_NPC;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import static net.runelite.api.Skill.HITPOINTS;
import static net.runelite.api.Skill.MAGIC;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.kit.KitType;
import net.runelite.client.util.Text;

class StatsTracker
{
	private static final int ICE_BARRAGE_SPLASH_XP = 52;
	private static final double HP_XP_PER_HIT = 1.3333;
	private static final int MAX_REASONABLE_HIT = 110;

	// Number of ticks after which we will no longer associate an action with a click.
	private static final int CLICK_ACTION_TICK_THRESHOLD = 5;

	@Inject
	private Client client;

	private GameRecord currentGame;
	private CWTeam ourTeam;

	// Splashes
	private int lastHPXP;
	private int lastMageXP;
	private int castBarrageOnTick;
	private int mageXpGained;
	private int mageXpOnTick;

	// Tinds
	private WorldPoint tindTarget;
	private Map<WorldPoint, CWBarricade> cadesLastTick = new HashMap<>();

	// Item usage
	private int invExplosLastTick;
	private int invCadesLastTick;
	private int invBucketsLastTick;
	private int droppedExploOnTick;
	private int droppedCadeOnTick;

	// Flags
	private boolean holdingTheirsLastTick = false;
	private boolean holdingOwnLastTick = false;
	private int clickedOwnDroppedFlagOnTick;
	private int clickedFlagStandOnTick;

	void onJoinGame(int validLobbyPlayers, boolean braced, int world)
	{
		ourTeam = CWTeam.ofPlayer(client.getLocalPlayer());
		currentGame = new GameRecord(validLobbyPlayers, System.currentTimeMillis(), braced, world);
	}

	GameRecord onLeaveGame()
	{
		currentGame.setSaraScore(client.getVar(Varbits.CW_SARA_SCORE));
		currentGame.setZamScore(client.getVar(Varbits.CW_ZAM_SCORE));
		return currentGame;
	}

	public void reset()
	{
		currentGame = null;
		ourTeam = null;
	}

	void onBarrageCast(int currentTick)
	{
		currentGame.totalCastAttempts++;
		castBarrageOnTick = currentTick;

		checkSplash(currentTick);
	}

	void onMageXpChanged(int currentTick)
	{
		int currentMageXP = client.getSkillExperience(MAGIC);
		mageXpGained = currentMageXP - lastMageXP;
		lastMageXP = currentMageXP;
		mageXpOnTick = currentTick;

		checkSplash(currentTick);
	}

	private void checkSplash(int currentTick)
	{
		if (currentTick == castBarrageOnTick &&
			currentTick == mageXpOnTick &&
			mageXpGained > 0 &&
			mageXpGained <= ICE_BARRAGE_SPLASH_XP)
		{
			currentGame.splashes++;
		}
	}

	void onHPXpChanged()
	{
		int currentHPXP = client.getSkillExperience(HITPOINTS);
		int hpXpGained = currentHPXP - lastHPXP;
		lastHPXP = currentHPXP;

		double approxDmg = (double) hpXpGained / HP_XP_PER_HIT;
		if (approxDmg < MAX_REASONABLE_HIT)
		{
			currentGame.recordDamageDealt(approxDmg);
		}
	}

	void recordDamageTaken(int amount)
	{
		currentGame.recordDamageTaken(amount);
	}

	void recordFrozen()
	{
		currentGame.frozenCount++;
	}

	void recordSpeared()
	{
		currentGame.timesSpeared++;
	}

	void recordCastOnMe()
	{
		currentGame.freezesOnMe++;
	}

	void recordSplashOnMe()
	{
		currentGame.splashesOnMe++;
	}

	void recordDeath()
	{
		currentGame.deaths++;
		if (holdingOwnFlag())
		{
			CWArea diedInArea = CWArea.match(client.getLocalPlayer().getWorldLocation());
			if (CWArea.NORTH_ROCKS.equals(diedInArea) || CWArea.SOUTH_ROCKS.equals(diedInArea))
			{
				currentGame.flagsSafed++;
			}
		}
	}

	void checkHoldingFlag(int currentTick)
	{
		boolean holdingOwnThisTick = holdingOwnFlag();
		if (holdingOwnLastTick && !holdingOwnThisTick)
		{
			lostOurFlag();
		}
		holdingOwnLastTick = holdingOwnThisTick;


		boolean holdingTheirsThisTick = holdingTheirFlag();
		if (holdingTheirsLastTick && !holdingTheirsThisTick)
		{
			lostTheirFlag(currentTick);
		}
		holdingTheirsLastTick = holdingTheirsThisTick;
	}

	private void lostTheirFlag(int currentTick)
	{
		CWArea playerArea = CWArea.match(client.getLocalPlayer().getWorldLocation());
		CWArea ourFourth = ourTeam.getBase().getFourth();
		boolean recentlyClickedFlagStand = currentTick - clickedFlagStandOnTick < CLICK_ACTION_TICK_THRESHOLD;
		if (ourFourth != null && ourFourth.equals(playerArea) && recentlyClickedFlagStand)
		{
			currentGame.flagsScored++;
		}
	}

	private void lostOurFlag()
	{
		CWArea playerArea = CWArea.match(client.getLocalPlayer().getWorldLocation());
		if (playerArea == null)
		{
			return;
		}

		CWArea ourGround = ourTeam.getBase().getGround();
		if (playerArea.equals(ourGround) || playerArea.equals(CWArea.NORTH_ROCKS) || playerArea.equals(CWArea.SOUTH_ROCKS))
		{
			currentGame.flagsSafed++;
		}
	}

	void onDroppedFlagDespawned(CWFlag despawnedFlag, int onTick, WorldPoint despawnedAt)
	{
		boolean isOurFlag = despawnedFlag != null && despawnedFlag.getTeam().equals(ourTeam);
		boolean recentlyClickedOwnFlag = onTick - clickedOwnDroppedFlagOnTick < CLICK_ACTION_TICK_THRESHOLD;
		boolean nextToFlag = despawnedAt.distanceTo(client.getLocalPlayer().getWorldLocation()) == 1;

		if (recentlyClickedOwnFlag && isOurFlag && inOwnBase() && nextToFlag && !visiblePlayerHoldingOurFlag())
		{
			currentGame.flagsSafed++;
		}
	}

	void checkItemUsage(ItemContainer inventory, int currentTick)
	{
		if (inventory == null)
		{
			return;
		}

		Item[] invItems = inventory.getItems();
		if (invItems == null)
		{
			return;
		}

		int invExplosThisTick = 0;
		int invCadesThisTick = 0;
		int invBucketsThisTick = 0;
		for (Item item : invItems)
		{
			switch (item.getId())
			{
				case ItemID.EXPLOSIVE_POTION:
					invExplosThisTick++;
					break;
				case ItemID.BARRICADE:
					invCadesThisTick++;
					break;
				case ItemID.BUCKET_OF_WATER:
					invBucketsThisTick++;
					break;
			}
		}

		if (invExplosLastTick - invExplosThisTick == 1 && currentTick - droppedExploOnTick > 1)
		{
			currentGame.cadesExploded++;
		}

		if (invCadesLastTick - invCadesThisTick == 1 && currentTick - droppedCadeOnTick > 1)
		{
			currentGame.cadesSet++;
		}

		if (invBucketsLastTick - invBucketsThisTick == 1)
		{
			currentGame.cadesBucketed++;
		}

		invExplosLastTick = invExplosThisTick;
		invCadesLastTick = invCadesThisTick;
		invBucketsLastTick = invBucketsThisTick;
	}

	void checkTindedCades()
	{
		Map<WorldPoint, CWBarricade> cadesThisTick = findCades();
		for (WorldPoint newCadeLocation : cadesThisTick.keySet())
		{
			CWBarricade oldCade = cadesLastTick.get(newCadeLocation);
			CWBarricade newCade = cadesThisTick.get(newCadeLocation);

			if (oldCade == null || newCade == null)
			{
				continue;
			}

			if (!oldCade.isTinded() && newCade.isTinded())
			{
				onCadeTinded(newCadeLocation, newCade.getNpcID());
			}
		}

		cadesLastTick = cadesThisTick;
	}

	private void onCadeTinded(WorldPoint newTindedCadeLocation, int newTindedCadeId)
	{
		Actor ourTarget = client.getLocalPlayer().getInteracting();
		if (newTindedCadeLocation == null || ourTarget == null || !(ourTarget instanceof NPC))
		{
			return;
		}

		NPC ourTargetCade = ((NPC) ourTarget);
		WorldPoint ourPosition = client.getLocalPlayer().getWorldLocation();

		if (ourTargetCade.getId() == newTindedCadeId &&
			newTindedCadeLocation.equals(ourTargetCade.getWorldLocation()) &&
			ourPosition.distanceTo(ourTargetCade.getWorldLocation()) == 1 &&
			newTindedCadeLocation.equals(tindTarget))
		{
			currentGame.cadesTinded++;
		}

	}

	public void onMenuOptionClicked(MenuOptionClicked event, int currentTick)
	{
		if (matchesTindCade(event))
		{
			CWBarricade cade = CWBarricade.fromNPCId(event.getId());
			if (cade != null && !cade.isTinded())
			{
				tindTarget = getMenuNPCLocation(event.getId());
			}
		}

		if (matchesDropCade(event))
		{
			droppedCadeOnTick = currentTick;
		}

		if (matchesDropExplo(event))
		{
			droppedExploOnTick = currentTick;
		}

		if ("Capture".equals(event.getMenuOption()))
		{
			clickedFlagStandOnTick = currentTick;
		}

		if (matchesOwnDroppedFlag(event))
		{
			clickedOwnDroppedFlagOnTick = currentTick;
		}
	}

	private WorldPoint getMenuNPCLocation(int menuId)
	{
		final NPC[] cachedNPCs = client.getCachedNPCs();
		if (menuId < cachedNPCs.length)
		{
			NPC cachedNPC = cachedNPCs[menuId];
			if (cachedNPC != null)
			{
				return cachedNPC.getWorldLocation();
			}
		}

		return null;
	}

	private boolean holdingTheirFlag()
	{
		CWFlag flag = getWieldedFlag();
		return flag != null && flag.getTeam().opposite().equals(ourTeam);
	}

	private boolean holdingOwnFlag()
	{
		CWFlag flag = getWieldedFlag();
		return flag != null && ourTeam.equals(flag.getTeam());
	}

	private CWFlag getWieldedFlag()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		PlayerComposition playerComposition = local.getPlayerComposition();
		if (playerComposition == null)
		{
			return null;
		}

		return CWFlag.fromEquipment(playerComposition.getEquipmentId(KitType.WEAPON));
	}

	private Map<WorldPoint, CWBarricade> findCades()
	{
		HashMap<WorldPoint, CWBarricade> cadeMap = new HashMap<>();
		for (NPC npc : client.getNpcs())
		{
			CWBarricade cade = CWBarricade.fromNPCId(npc.getId());
			if (cade != null)
			{
				cadeMap.put(npc.getWorldLocation(), cade);
			}
		}

		return cadeMap;
	}

	private boolean inOwnBase()
	{
		CWBase currentBase = CWBase.match(client.getLocalPlayer().getWorldLocation());
		return currentBase != null && currentBase.getTeam().equals(ourTeam);
	}

	private boolean visiblePlayerHoldingOurFlag()
	{
		Player me = client.getLocalPlayer();
		return client.getPlayers().stream()
			.filter(Objects::nonNull)
			.filter(player -> !player.equals(me))
			.filter(player -> player.getPlayerComposition() != null)
			.anyMatch(player ->
			{
				int weaponID = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
				CWFlag flag = CWFlag.fromEquipment(weaponID);
				return flag != null && ourTeam.equals(flag.getTeam());
			});
	}

	private boolean matchesOwnDroppedFlag(MenuOptionClicked event)
	{
		String menuTarget = Text.removeTags(event.getMenuTarget());
		CWFlag ownFlag = ourTeam.getFlag();
		return ownFlag != null && ownFlag.getMenuName().equals(menuTarget);
	}

	private boolean matchesDropExplo(MenuOptionClicked event)
	{
		return ITEM_DROP == event.getMenuAction() && event.getId() == ItemID.EXPLOSIVE_POTION;
	}

	private boolean matchesDropCade(MenuOptionClicked event)
	{
		return ITEM_DROP == event.getMenuAction() && event.getId() == ItemID.BARRICADE;
	}

	private boolean matchesTindCade(MenuOptionClicked event)
	{
		String menuTarget = Text.removeTags(event.getMenuTarget());
		return ITEM_USE_ON_NPC == event.getMenuAction() && "Tinderbox -> Barricade".equals(menuTarget);
	}
}
