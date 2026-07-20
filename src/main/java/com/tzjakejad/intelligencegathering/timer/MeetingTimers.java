package com.tzjakejad.intelligencegathering.timer;

import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * Manages the two meeting infoboxes (spec §3.4), both derived from
 * {@link CurrentMeeting#getScheduledAppearanceMs()}:
 *
 * <ul>
 *   <li><b>Meeting start</b> — counts down to the gangsters appearing, matching the board's
 *       "in X minutes" line.</li>
 *   <li><b>Despawn</b> — counts down the 5-minute active window; red under 60s.</li>
 * </ul>
 */
@Singleton
public class MeetingTimers
{
	private final InfoBoxManager infoBoxManager;

	private CountdownInfoBox rotationBox;
	private CountdownInfoBox despawnBox;

	// Persisted boxes read their target from here rather than capturing a single CurrentMeeting.
	// Re-reading the board mints a fresh instance with a recomputed anchor (it tracks the board's
	// whole-minute "in X minutes" line); without this the reused infoboxes would keep counting to
	// the first read's stale anchor and drift out of step with the side panel.
	private volatile CurrentMeeting meeting;

	@Inject
	public MeetingTimers(InfoBoxManager infoBoxManager)
	{
		this.infoBoxManager = infoBoxManager;
	}

	/**
	 * Reconcile the infoboxes with the current meeting and config. Safe to call repeatedly.
	 */
	public void update(CurrentMeeting meeting, IntelligenceGatheringConfig config, Plugin plugin,
		BufferedImage rotationIcon, BufferedImage despawnIcon)
	{
		this.meeting = meeting;

		if (meeting == null)
		{
			clear();
			return;
		}

		long now = System.currentTimeMillis();

		// Meeting-start timer: counts down to the gangsters appearing, so it reads the same as the
		// board's "in X minutes" line (not the +30-minute rotation end, which confusingly overshot).
		boolean wantStart = config.showRotationTimer() && meeting.getScheduledAppearanceMs() > now;
		if (wantStart && rotationBox == null)
		{
			rotationBox = new CountdownInfoBox(rotationIcon, plugin,
				"Organised crime meeting starts", this::spawnTargetMs, false);
			infoBoxManager.addInfoBox(rotationBox);
		}
		else if (!wantStart && rotationBox != null)
		{
			removeRotation();
		}

		// Despawn timer: only once the meeting is live and its 5-minute window is still open.
		boolean live = now >= meeting.getScheduledAppearanceMs();
		boolean wantDespawn = config.showDespawnTimer() && live && meeting.despawnMs() > now;
		if (wantDespawn && despawnBox == null)
		{
			despawnBox = new CountdownInfoBox(despawnIcon, plugin,
				"Gangsters despawn", this::despawnTargetMs, true);
			infoBoxManager.addInfoBox(despawnBox);
		}
		else if (!wantDespawn && despawnBox != null)
		{
			removeDespawn();
		}
	}

	private long spawnTargetMs()
	{
		CurrentMeeting m = meeting;
		return m == null ? System.currentTimeMillis() : m.getScheduledAppearanceMs();
	}

	private long despawnTargetMs()
	{
		CurrentMeeting m = meeting;
		return m == null ? System.currentTimeMillis() : m.despawnMs();
	}

	public void clear()
	{
		removeRotation();
		removeDespawn();
	}

	private void removeRotation()
	{
		if (rotationBox != null)
		{
			infoBoxManager.removeInfoBox(rotationBox);
			rotationBox = null;
		}
	}

	private void removeDespawn()
	{
		if (despawnBox != null)
		{
			infoBoxManager.removeInfoBox(despawnBox);
			despawnBox = null;
		}
	}
}
