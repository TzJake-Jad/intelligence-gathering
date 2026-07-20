package com.tzjakejad.intelligencegathering.model;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * One notice-board meeting location. Deserialised from oc-location-anchors.json.
 *
 * <p>{@code boardMessage} is the match key against the in-game notice board (spec §4).
 * {@code worldPoint} coordinates are captured in-game and may be null until seeded;
 * consumers must degrade gracefully when {@link #getWorldPoint()} returns null (spec §3.1).
 */
@Getter
public class OcLocation
{
	/** Stable key, matches the original screenshot basenames, e.g. {@code arceuus1}. */
	private String id;

	/** Arceuus / Hosidius / Lovakengj / Piscarilius / Shayzien / Other. */
	private String area;

	/** Multicombat flag. Only arceuus7 and other4 are single-way. */
	private boolean multi;

	/** Floor: 0 ground, 1 upstairs/middle. Inferred from the description; verify in-game. */
	private int plane;

	/** Nullable coordinates from the seed file. */
	private Coord worldPoint;

	/** Exact notice-board string; the match key. */
	private String boardMessage;

	/** Human-readable directions carried over from the original plugin. */
	private String navHint;

	/**
	 * Resolved RuneLite world point at this location's plane, or {@code null} if the
	 * coordinate has not been captured yet.
	 */
	public WorldPoint getWorldPoint()
	{
		if (worldPoint == null || worldPoint.x == null || worldPoint.y == null)
		{
			return null;
		}
		return new WorldPoint(worldPoint.x, worldPoint.y, plane);
	}

	public boolean hasWorldPoint()
	{
		return getWorldPoint() != null;
	}

	/** Raw nullable x/y as stored in the seed JSON. */
	public static class Coord
	{
		public Integer x;
		public Integer y;
	}
}
