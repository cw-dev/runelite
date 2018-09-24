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

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Player;
import net.runelite.api.widgets.WidgetInfo;

@AllArgsConstructor
@Getter
enum CWTeam
{
	SARA(1, WidgetInfo.CW_TIME_REMAINING_SARA, new Color(142, 168, 255)),
	ZAM(2, WidgetInfo.CW_TIME_REMAINING_ZAM, new Color(255, 156, 115)),
	NONE(0, null, null);

	private final int team;
	private final WidgetInfo timeRemainingWidget;

	private final Color color;

	private static final Map<Integer, CWTeam> RS_TEAMS = new HashMap<>();
	private static final Map<CWTeam, CWFlag> FLAGS = new HashMap<>();
	private static final Map<CWTeam, CWBase> BASES = new HashMap<>();

	static
	{
		CWTeam[] teams = values();

		for (CWTeam team : teams)
		{
			RS_TEAMS.put(team.getTeam(), team);

			if (CWFlag.SARA.getTeam().equals(team))
			{
				FLAGS.put(team, CWFlag.SARA);
				BASES.put(team, CWBase.SARA_BASE);
			}
			else if (CWFlag.ZAM.getTeam().equals(team))
			{
				FLAGS.put(team, CWFlag.ZAM);
				BASES.put(team, CWBase.ZAM_BASE);
			}
		}
	}

	public CWTeam opposite()
	{
		return this == ZAM ? SARA : this == SARA ? ZAM : NONE;
	}

	public static CWTeam ofPlayer(Player player)
	{
		if (player == null)
		{
			return NONE;
		}
		return RS_TEAMS.getOrDefault(player.getTeam(), NONE);
	}

	public CWFlag getFlag()
	{
		return FLAGS.get(this);
	}

	public CWBase getBase()
	{
		return BASES.get(this);
	}
}
