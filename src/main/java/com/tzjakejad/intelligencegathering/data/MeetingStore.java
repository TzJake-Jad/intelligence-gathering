package com.tzjakejad.intelligencegathering.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import com.tzjakejad.intelligencegathering.model.BossGender;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.OcLocations;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Persists the current meeting and per-world statuses across restarts (spec §6) by serialising a
 * compact form to the plugin's config group via Gson. Restores only if the saved cycle is still
 * live, so a stale meeting from a previous session is discarded rather than shown.
 */
@Slf4j
@Singleton
public class MeetingStore
{
	private static final String KEY_MEETING = "savedMeeting";
	private static final String KEY_WORLDS = "savedWorlds";
	private static final Type WORLD_LIST = new TypeToken<List<SavedWorld>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final OcLocations locations;

	@Inject
	public MeetingStore(ConfigManager configManager, Gson gson, OcLocations locations)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.locations = locations;
	}

	public void save(CurrentMeeting meeting, Collection<WorldStatus> worlds)
	{
		if (meeting == null)
		{
			clear();
			return;
		}

		SavedMeeting saved = new SavedMeeting(
			meeting.getLocation().getId(),
			meeting.getScheduledAppearanceMs(),
			meeting.getReadAtMs());
		configManager.setConfiguration(IntelligenceGatheringConfig.GROUP, KEY_MEETING, gson.toJson(saved));

		List<SavedWorld> savedWorlds = new ArrayList<>();
		for (WorldStatus w : worlds)
		{
			savedWorlds.add(new SavedWorld(w.getWorld(), w.getBossGender(), w.getLastClearedMs()));
		}
		configManager.setConfiguration(IntelligenceGatheringConfig.GROUP, KEY_WORLDS, gson.toJson(savedWorlds));
	}

	public void clear()
	{
		configManager.unsetConfiguration(IntelligenceGatheringConfig.GROUP, KEY_MEETING);
		configManager.unsetConfiguration(IntelligenceGatheringConfig.GROUP, KEY_WORLDS);
	}

	/** Restore the saved state, or {@code null} if none / stale / unparseable. */
	public Restored load()
	{
		String meetingJson = configManager.getConfiguration(IntelligenceGatheringConfig.GROUP, KEY_MEETING);
		if (meetingJson == null || meetingJson.isEmpty())
		{
			return null;
		}

		try
		{
			SavedMeeting saved = gson.fromJson(meetingJson, SavedMeeting.class);
			if (saved == null || saved.locationId == null)
			{
				return null;
			}

			OcLocation location = locations.byId(saved.locationId);
			if (location == null)
			{
				log.debug("Saved meeting references unknown location {}", saved.locationId);
				clear();
				return null;
			}

			CurrentMeeting meeting = new CurrentMeeting(location, saved.scheduledAppearanceMs, saved.readAtMs);

			// Discard a meeting whose cycle has already rotated out.
			if (meeting.rotationEndMs() <= System.currentTimeMillis())
			{
				clear();
				return null;
			}

			List<WorldStatus> worlds = new ArrayList<>();
			String worldsJson = configManager.getConfiguration(IntelligenceGatheringConfig.GROUP, KEY_WORLDS);
			if (worldsJson != null && !worldsJson.isEmpty())
			{
				List<SavedWorld> savedWorlds = gson.fromJson(worldsJson, WORLD_LIST);
				if (savedWorlds != null)
				{
					for (SavedWorld sw : savedWorlds)
					{
						WorldStatus ws = new WorldStatus(sw.world);
						ws.setBossGender(sw.bossGender == null ? BossGender.UNKNOWN : sw.bossGender);
						ws.setLastClearedMs(sw.lastClearedMs);
						worlds.add(ws);
					}
				}
			}

			return new Restored(meeting, Collections.unmodifiableList(worlds));
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Failed to parse saved organised crime meeting; clearing", e);
			clear();
			return null;
		}
	}

	@Value
	public static class Restored
	{
		CurrentMeeting meeting;
		List<WorldStatus> worlds;
	}

	private static class SavedMeeting
	{
		final String locationId;
		final long scheduledAppearanceMs;
		final long readAtMs;

		SavedMeeting(String locationId, long scheduledAppearanceMs, long readAtMs)
		{
			this.locationId = locationId;
			this.scheduledAppearanceMs = scheduledAppearanceMs;
			this.readAtMs = readAtMs;
		}
	}

	private static class SavedWorld
	{
		final int world;
		final BossGender bossGender;
		final long lastClearedMs;

		SavedWorld(int world, BossGender bossGender, long lastClearedMs)
		{
			this.world = world;
			this.bossGender = bossGender;
			this.lastClearedMs = lastClearedMs;
		}
	}
}
