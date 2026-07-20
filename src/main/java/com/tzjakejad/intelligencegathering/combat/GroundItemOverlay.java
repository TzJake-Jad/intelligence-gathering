package com.tzjakejad.intelligencegathering.combat;

import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Highlights dropped Shayzien gang intelligence (the boss drop) on the ground so it's easy to spot
 * in a cramped meeting (spec §4 — the boss "always drops intelligence"). The plugin feeds the live
 * set of tiles the item is sitting on; the colour comes from config. Only tiles on the player's
 * current floor are drawn.
 */
public class GroundItemOverlay extends Overlay
{
	private final Client client;
	private final IntelligenceGatheringConfig config;

	private Supplier<Collection<Tile>> tileSupplier = Collections::emptyList;

	@Inject
	public GroundItemOverlay(Client client, IntelligenceGatheringConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setTileSupplier(Supplier<Collection<Tile>> tileSupplier)
	{
		this.tileSupplier = tileSupplier;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightIntel())
		{
			return null;
		}

		for (Tile tile : tileSupplier.get())
		{
			if (tile == null || tile.getPlane() != client.getPlane())
			{
				continue;
			}

			LocalPoint lp = tile.getLocalLocation();
			if (lp == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, config.intelColor());
			}
		}

		return null;
	}
}
