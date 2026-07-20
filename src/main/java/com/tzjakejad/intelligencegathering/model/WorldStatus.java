package com.tzjakejad.intelligencegathering.model;

import lombok.Data;

/**
 * Per-world state for the current cycle (spec §1). The location does not vary by world;
 * only the boss gender and whether you have already cleared the world do.
 */
@Data
public class WorldStatus
{
	private final int world;

	/** UNKNOWN until a gang boss NPC is seen on this world. */
	private BossGender bossGender = BossGender.UNKNOWN;

	/** Epoch ms of the last clear on this world, for "already done" styling. 0 = never. */
	private long lastClearedMs;

	public WorldStatus(int world)
	{
		this.world = world;
	}

	public boolean isClearedThisCycle(long cycleStartMs)
	{
		return lastClearedMs >= cycleStartMs;
	}
}
