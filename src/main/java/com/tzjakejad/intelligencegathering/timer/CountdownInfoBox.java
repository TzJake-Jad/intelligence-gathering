package com.tzjakejad.intelligencegathering.timer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.LongSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * A generic mm:ss countdown infobox towards a target epoch-ms supplied lazily, so it keeps
 * ticking without needing per-frame updates pushed into it (spec §3.4).
 */
class CountdownInfoBox extends InfoBox
{
	private final LongSupplier targetMs;
	private final boolean redWhenLow;

	CountdownInfoBox(BufferedImage image, Plugin plugin, String tooltip, LongSupplier targetMs,
		boolean redWhenLow)
	{
		super(image, plugin);
		this.targetMs = targetMs;
		this.redWhenLow = redWhenLow;
		setTooltip(tooltip);
	}

	private long remainingMs()
	{
		return Math.max(0, targetMs.getAsLong() - System.currentTimeMillis());
	}

	@Override
	public String getText()
	{
		long totalSeconds = remainingMs() / 1000L;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d:%02d", minutes, seconds);
	}

	@Override
	public Color getTextColor()
	{
		if (redWhenLow && remainingMs() <= 60_000L)
		{
			return Color.RED;
		}
		return Color.WHITE;
	}
}
