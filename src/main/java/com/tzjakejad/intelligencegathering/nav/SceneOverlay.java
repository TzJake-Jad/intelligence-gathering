package com.tzjakejad.intelligencegathering.nav;

import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the scene-tile highlight for the current meeting when the player is on the spawn's floor
 * and the tile is inside the loaded scene (spec §3.2). Long-range guidance to the meeting is the
 * game hint arrow (handled in the plugin), not a scene overlay.
 */
public class SceneOverlay extends Overlay
{
	private final Client client;
	private final IntelligenceGatheringConfig config;

	/** Supplies the live current meeting; set by the plugin at start-up. */
	private Supplier<CurrentMeeting> meetingSupplier = () -> null;

	@Inject
	public SceneOverlay(Client client, IntelligenceGatheringConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setMeetingSupplier(Supplier<CurrentMeeting> meetingSupplier)
	{
		this.meetingSupplier = meetingSupplier;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		CurrentMeeting meeting = meetingSupplier.get();
		if (meeting == null)
		{
			return null;
		}

		OcLocation location = meeting.getLocation();
		WorldPoint wp = location.getWorldPoint();
		if (wp == null || client.getPlane() != location.getPlane())
		{
			return null;
		}

		LocalPoint lp = LocalPoint.fromWorld(client, wp);
		if (lp == null)
		{
			// Outside the loaded scene / wrong region — nothing to draw here.
			return null;
		}

		if (config.showTileHighlight())
		{
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, config.tileColor());
			}
		}

		return null;
	}
}
