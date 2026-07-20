package com.tzjakejad.intelligencegathering.nav;

import com.tzjakejad.intelligencegathering.model.OcLocation;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Owns the single world-map marker for the current meeting (spec §3.1). Degrades gracefully:
 * a location with no captured {@link OcLocation#getWorldPoint()} simply gets no marker.
 */
@Singleton
public class WorldMapController
{
	private final WorldMapPointManager worldMapPointManager;

	private WorldMapPoint current;

	@Inject
	public WorldMapController(WorldMapPointManager worldMapPointManager)
	{
		this.worldMapPointManager = worldMapPointManager;
	}

	/** Show the marker for a location, replacing any existing one. No-op if coord is unknown. */
	public void set(OcLocation location, BufferedImage icon)
	{
		clear();

		WorldPoint wp = location.getWorldPoint();
		if (wp == null)
		{
			return;
		}

		// Snapshot the (async item) image onto a plain ARGB image so the world map draws it reliably,
		// the way the clue scroll plugin composites its map icon.
		BufferedImage image = flatten(icon);

		String tooltip = location.getArea() + ": " + location.getNavHint();
		current = WorldMapPoint.builder()
			.worldPoint(wp)
			.image(image)
			.tooltip(tooltip)
			.name("Intelligence Gathering")
			.jumpOnClick(true)
			.build();
		// Snap to the map edge when the meeting is off the visible map, so it's always findable when
		// you open the map from a distance — this is the clue-scroll behaviour.
		current.setSnapToEdge(true);
		if (image != null)
		{
			current.setImagePoint(new Point(image.getWidth() / 2, image.getHeight() / 2));
		}

		worldMapPointManager.add(current);
	}

	/** Copy onto a plain image; an {@code AsyncBufferedImage} isn't always drawn on the world map. */
	private static BufferedImage flatten(BufferedImage src)
	{
		if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0)
		{
			return src;
		}
		BufferedImage flat = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics g = flat.getGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return flat;
	}

	public void clear()
	{
		if (current != null)
		{
			worldMapPointManager.remove(current);
			current = null;
		}
	}
}
