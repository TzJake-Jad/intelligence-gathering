package com.tzjakejad.intelligencegathering.model;

import lombok.Value;

/**
 * The single globally-valid meeting for the current cycle (spec §1). One location is live
 * on every world at once, on a 30-minute rotation; gangsters despawn 5 minutes after the
 * scheduled appearance.
 *
 * <p>NOTE: the global-vs-region-scoped assumption is the load-bearing one flagged in spec §1
 * and must be verified in-game.
 */
@Value
public class CurrentMeeting
{
	/** Rotation length: a new location every 30 minutes. */
	public static final long ROTATION_MS = 30 * 60 * 1000L;

	/** Despawn window: gangsters leave 5 minutes after the scheduled appearance. */
	public static final long DESPAWN_MS = 5 * 60 * 1000L;

	/** Location resolved from the board; valid on all worlds. */
	OcLocation location;

	/** Anchor for the 30-min rotation and 5-min despawn timers (epoch ms). */
	long scheduledAppearanceMs;

	/** When the board was read (epoch ms). */
	long readAtMs;

	/** Epoch ms when this location rotates out. */
	public long rotationEndMs()
	{
		return scheduledAppearanceMs + ROTATION_MS;
	}

	/** Epoch ms when the gangsters despawn. */
	public long despawnMs()
	{
		return scheduledAppearanceMs + DESPAWN_MS;
	}
}
