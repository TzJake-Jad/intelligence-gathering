package com.tzjakejad.intelligencegathering.combat;

import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines tracked gangster NPCs and the gang boss at a meeting (spec §4). The plugin feeds the
 * live NPC sets via suppliers; render style (hull / tile / model outline) and colours come from
 * config. The boss is drawn distinctly with its own colour because it always drops intelligence.
 */
public class NpcHighlightOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final Client client;
	private final IntelligenceGatheringConfig config;
	private final GangIds gangIds;
	private final ModelOutlineRenderer modelOutline;

	private Supplier<Collection<NPC>> gangsterSupplier = Collections::emptyList;
	private Supplier<Collection<NPC>> bossSupplier = Collections::emptyList;

	@Inject
	public NpcHighlightOverlay(Client client, IntelligenceGatheringConfig config, GangIds gangIds,
		ModelOutlineRenderer modelOutline)
	{
		this.client = client;
		this.config = config;
		this.gangIds = gangIds;
		this.modelOutline = modelOutline;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setGangsterSupplier(Supplier<Collection<NPC>> supplier)
	{
		this.gangsterSupplier = supplier;
	}

	public void setBossSupplier(Supplier<Collection<NPC>> supplier)
	{
		this.bossSupplier = supplier;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only draw for the live scene. During HOPPING/LOADING the tracked NPC references may still
		// hold stale indices from the old scene, so gating here stops their outlines ghosting onto
		// the transition/new world regardless of set contents.
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		if (config.highlightGangsters())
		{
			Color color = config.gangsterColor();
			for (NPC npc : gangsterSupplier.get())
			{
				draw(graphics, npc, color);
			}
		}

		if (config.highlightBoss())
		{
			for (NPC npc : bossSupplier.get())
			{
				draw(graphics, npc, bossColor(npc));
			}
		}

		return null;
	}

	/**
	 * Boss outline colour by danger — red for the knife-throwing (male) boss, green for the melee
	 * (female) boss, and the configurable fallback until a boss's combat style has been seen. Matches
	 * the panel's per-world dot so the two surfaces agree.
	 */
	private Color bossColor(NPC npc)
	{
		if (npc == null)
		{
			return config.bossColor();
		}
		switch (gangIds.bossGender(npc.getId()))
		{
			case MALE:
				return config.bossColorDangerous();
			case FEMALE:
				return config.bossColorSafe();
			default:
				return config.bossColor();
		}
	}

	private void draw(Graphics2D graphics, NPC npc, Color color)
	{
		if (npc == null)
		{
			return;
		}

		switch (config.bossHighlightStyle())
		{
			case HULL:
			{
				Shape hull = npc.getConvexHull();
				if (hull != null)
				{
					OverlayUtil.renderPolygon(graphics, hull, color);
				}
				break;
			}
			case TILE:
			{
				LocalPoint lp = npc.getLocalLocation();
				if (lp != null)
				{
					Polygon tile = Perspective.getCanvasTilePoly(client, lp);
					if (tile != null)
					{
						OverlayUtil.renderPolygon(graphics, tile, color);
					}
				}
				break;
			}
			case OUTLINE:
			default:
				modelOutline.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
				break;
		}
	}
}
