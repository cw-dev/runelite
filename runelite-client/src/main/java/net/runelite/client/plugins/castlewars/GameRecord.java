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

import lombok.Data;


@Data
class GameRecord
{
	public int teamSize;
	public int world;
	public boolean braced;
	public int zamScore;
	public int saraScore;
	public long createdTs;

	public int cadesSet;
	public int cadesTinded;
	public int cadesBucketed;
	public int cadesExploded;

	public int totalCastAttempts;
	public int splashes;
	public int frozenCount;
	public int freezesOnMe;
	public int splashesOnMe;

	public int deaths;
	private int damageTaken;
	private int highestHitTaken;
	public int timesSpeared;

	private double damageDealt;
	private double highestHitDealt;

	public int flagsSafed;
	public int flagsScored;

	GameRecord(int teamSize, long createdTs, boolean braced, int world)
	{
		this.teamSize = teamSize;
		this.createdTs = createdTs;
		this.braced = braced;
		this.world = world;
	}

	void recordDamageDealt(double approxDmg)
	{
		damageDealt += approxDmg;
		if (approxDmg > highestHitDealt)
		{
			highestHitDealt = approxDmg;
		}
	}

	void recordDamageTaken(int dmg)
	{
		damageTaken += dmg;
		if (dmg > highestHitTaken)
		{
			highestHitTaken = dmg;
		}
	}

	double getCastRate()
	{
		if (totalCastAttempts == 0)
		{
			return 0.0;
		}

		return (1 - (splashes / (double) totalCastAttempts)) * 100;
	}

	int getTotalCastsOnMe()
	{
		return splashesOnMe + freezesOnMe;
	}

	double getSplashRate()
	{
		int totalCastsOnMe = getTotalCastsOnMe();
		if (totalCastsOnMe == 0)
		{
			return 0;
		}

		return (splashesOnMe / (double) totalCastsOnMe) * 100;
	}
}
