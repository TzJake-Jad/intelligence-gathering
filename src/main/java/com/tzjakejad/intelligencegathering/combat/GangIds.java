package com.tzjakejad.intelligencegathering.combat;

import com.google.gson.Gson;
import com.tzjakejad.intelligencegathering.model.BossGender;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry of gang-boss and gangster NPC ids, loaded from {@code oc-gang-npcs.json} (spec §3/§4).
 *
 * <p>Ids are captured in-game (log {@code npc.getId()}/{@code npc.getName()} at a live meeting) and
 * pasted into the JSON. A boss whose combat style is known goes in {@code maleBossIds} (throwing
 * knives, dangerous) or {@code femaleBossIds} (cutlass/melee, safe); one that's been seen but not yet
 * classified goes in {@code bossIds}, so it is still highlighted while its per-world dot stays
 * {@link BossGender#UNKNOWN} (grey). Nothing is guessed — an unclassified id never marks a world
 * safe or dangerous.
 */
@Slf4j
@Singleton
public class GangIds
{
	private static final String RESOURCE = "/com/tzjakejad/intelligencegathering/oc-gang-npcs.json";

	private final Set<Integer> maleBossIds;
	private final Set<Integer> femaleBossIds;
	private final Set<Integer> bossIds;
	private final Set<Integer> gangsterIds;

	@Inject
	public GangIds(Gson gson)
	{
		Model model = load(gson);
		this.maleBossIds = toSet(model.maleBossIds);
		this.femaleBossIds = toSet(model.femaleBossIds);
		this.bossIds = toSet(model.bossIds);
		this.gangsterIds = toSet(model.gangsterIds);

		int total = maleBossIds.size() + femaleBossIds.size() + bossIds.size() + gangsterIds.size();
		if (total == 0)
		{
			log.debug("No organised crime gang NPC ids seeded yet; boss colouring/highlighting inert");
		}
		else
		{
			log.debug("Loaded gang ids: {} male boss, {} female boss, {} boss (style unknown), {} gangster",
				maleBossIds.size(), femaleBossIds.size(), bossIds.size(), gangsterIds.size());
		}
	}

	/** {@link BossGender} for an NPC id, or {@link BossGender#UNKNOWN} if it isn't a known boss. */
	public BossGender bossGender(int npcId)
	{
		if (maleBossIds.contains(npcId))
		{
			return BossGender.MALE;
		}
		if (femaleBossIds.contains(npcId))
		{
			return BossGender.FEMALE;
		}
		return BossGender.UNKNOWN;
	}

	public boolean isBoss(int npcId)
	{
		return maleBossIds.contains(npcId) || femaleBossIds.contains(npcId) || bossIds.contains(npcId);
	}

	public boolean isGangster(int npcId)
	{
		return gangsterIds.contains(npcId);
	}

	private static Set<Integer> toSet(List<Integer> ids)
	{
		return ids == null ? Collections.emptySet() : new HashSet<>(ids);
	}

	private static Model load(Gson gson)
	{
		try (InputStream in = GangIds.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Gang NPC id file {} not found on classpath", RESOURCE);
				return new Model();
			}

			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Model parsed = gson.fromJson(reader, Model.class);
				return parsed == null ? new Model() : parsed;
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to read gang NPC id file", e);
			return new Model();
		}
	}

	/** Maps the JSON shape; unknown keys (e.g. {@code _comment}) are ignored by Gson. */
	private static class Model
	{
		List<Integer> maleBossIds;
		List<Integer> femaleBossIds;
		List<Integer> bossIds;
		List<Integer> gangsterIds;
	}
}
