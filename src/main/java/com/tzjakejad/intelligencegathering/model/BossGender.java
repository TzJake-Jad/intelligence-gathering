package com.tzjakejad.intelligencegathering.model;

import java.awt.Color;

/**
 * Gender of the gang boss on a given world. Fixed per world for a cycle.
 * Male throws knives (ranged, dangerous); female uses a cutlass (melee, safe).
 */
public enum BossGender
{
	UNKNOWN("Unknown", new Color(160, 160, 160)),
	MALE("Male (dangerous)", new Color(200, 60, 60)),
	FEMALE("Female (safe)", new Color(60, 170, 60));

	private final String label;
	private final Color color;

	BossGender(String label, Color color)
	{
		this.label = label;
		this.color = color;
	}

	public String getLabel()
	{
		return label;
	}

	public Color getColor()
	{
		return color;
	}
}
