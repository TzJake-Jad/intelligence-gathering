package com.tzjakejad.intelligencegathering;

import com.google.inject.Provides;
import com.tzjakejad.intelligencegathering.board.NoticeBoardReader;
import com.tzjakejad.intelligencegathering.combat.GangIds;
import com.tzjakejad.intelligencegathering.combat.GroundItemOverlay;
import com.tzjakejad.intelligencegathering.combat.NpcHighlightOverlay;
import com.tzjakejad.intelligencegathering.data.MeetingStore;
import com.tzjakejad.intelligencegathering.data.RotationLogger;
import com.tzjakejad.intelligencegathering.model.BossGender;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import com.tzjakejad.intelligencegathering.hop.WorldHopper;
import com.tzjakejad.intelligencegathering.nav.SceneOverlay;
import com.tzjakejad.intelligencegathering.nav.WorldMapController;
import com.tzjakejad.intelligencegathering.timer.MeetingTimers;
import com.tzjakejad.intelligencegathering.ui.IntelligenceGatheringController;
import com.tzjakejad.intelligencegathering.ui.IntelligenceGatheringPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Intelligence Gathering",
	description = "Finds the current Shayzien organised crime meeting and points you to it",
	tags = {"intelligence", "organised", "organized", "crime", "shayzien", "kourend", "gang"}
)
public class IntelligenceGatheringPlugin extends Plugin implements IntelligenceGatheringController
{
	// Confirmed against the original plugin's WidgetConstants (Mordo95/shayzien-organised-crime):
	// info board = 291, "no info" interface = 229. In the original the location text sat in
	// children 5-15 of group 291 and the time line starts with "The meeting is expected to".
	// TODO(spec §3.3/§6): swap these literals for the matching net.runelite.api.gameval.InterfaceID
	// constants (cosmetic; the numbers themselves are correct).
	private static final int NOTICE_BOARD_GROUP_ID = 291;
	private static final int NO_INFO_GROUP_ID = 229;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private IntelligenceGatheringConfig config;

	@Inject
	private NoticeBoardReader boardReader;

	@Inject
	private WorldMapController worldMapController;

	@Inject
	private SceneOverlay sceneOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MeetingTimers meetingTimers;

	@Inject
	private WorldHopper worldHopper;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private RotationLogger rotationLogger;

	@Inject
	private MeetingStore meetingStore;

	@Inject
	private GangIds gangIds;

	@Inject
	private NpcHighlightOverlay npcHighlightOverlay;

	@Inject
	private GroundItemOverlay groundItemOverlay;

	@Inject
	private SkillIconManager skillIconManager;

	@Getter
	private volatile CurrentMeeting currentMeeting;

	/** Worlds visited during the current cycle, keyed by world number. */
	private final Map<Integer, WorldStatus> worldStatuses = new ConcurrentSkipListMap<>();

	/** Tracked meeting NPCs for the highlight overlay (spec §4); mutated on the client thread. */
	private final Set<NPC> gangsters = ConcurrentHashMap.newKeySet();
	private final Set<NPC> bosses = ConcurrentHashMap.newKeySet();

	/** Tiles holding dropped gang intelligence (item 13395), for the ground highlight. */
	private final Set<Tile> intelTiles = ConcurrentHashMap.newKeySet();

	/** Last target posted to Shortest Path, for the §2 debounce. */
	private WorldPoint lastRouted;

	private BufferedImage markerIcon;
	private BufferedImage worldMapIcon;
	private IntelligenceGatheringPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		markerIcon = buildIcon();
		// Reuse the plugin badge for the world-map marker, scaled down to a legible marker size.
		worldMapIcon = ImageUtil.resizeImage(markerIcon, 32, 32);
		sceneOverlay.setMeetingSupplier(this::getCurrentMeeting);
		overlayManager.add(sceneOverlay);

		npcHighlightOverlay.setGangsterSupplier(() -> gangsters);
		npcHighlightOverlay.setBossSupplier(() -> bosses);
		overlayManager.add(npcHighlightOverlay);

		groundItemOverlay.setTileSupplier(() -> intelTiles);
		overlayManager.add(groundItemOverlay);

		panel = new IntelligenceGatheringPanel(this, skillIconManager);
		navButton = NavigationButton.builder()
			.tooltip("Intelligence Gathering")
			.icon(markerIcon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Restore on the client thread: it touches client state (hint arrow, Shortest Path) and
		// startUp runs on the Swing thread when the plugin is toggled from the config panel.
		clientThread.invokeLater(this::restoreState);

		log.info("Intelligence Gathering started");
	}

	/** Restore a persisted meeting + hop list (spec §6) if the saved cycle is still live. */
	private void restoreState()
	{
		MeetingStore.Restored restored = meetingStore.load();
		if (restored == null)
		{
			return;
		}

		currentMeeting = restored.getMeeting();
		worldStatuses.clear();
		for (WorldStatus ws : restored.getWorlds())
		{
			worldStatuses.put(ws.getWorld(), ws);
		}

		OcLocation location = restored.getMeeting().getLocation();
		if (config.showWorldMapMarker())
		{
			worldMapController.set(location, worldMapIcon);
		}
		updateHintArrow();
		routeTo(location.getWorldPoint());
		meetingTimers.update(currentMeeting, config, this, markerIcon, markerIcon);
		refreshPanel();
		log.debug("Restored organised crime meeting: {}", location.getId());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(npcHighlightOverlay);
		overlayManager.remove(groundItemOverlay);
		clientToolbar.removeNavigation(navButton);
		worldMapController.clear();
		meetingTimers.clear();
		client.clearHintArrow();
		clearRoute();
		// Keep the persisted meeting so a restart mid-cycle restores it (spec §6); just drop the
		// in-memory copy and any tracked NPCs.
		currentMeeting = null;
		worldStatuses.clear();
		gangsters.clear();
		bosses.clear();
		intelTiles.clear();
		panel = null;
		navButton = null;
		log.info("Intelligence Gathering stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOADING
			|| state == GameState.LOGGING_IN || state == GameState.LOGIN_SCREEN
			|| state == GameState.CONNECTION_LOST)
		{
			// NPC indices from the old scene are now invalid, and RuneLite doesn't reliably fire
			// NpcDespawned for them on a hop/teardown — drop the tracked references so their outlines
			// (and ground-item tiles) don't ghost onto the new world. LOADING covers same-world region
			// changes, which reshuffle NPC indices without passing through HOPPING. They re-populate
			// from the NpcSpawned/ItemSpawned events that re-fire once the new scene finishes loading.
			gangsters.clear();
			bosses.clear();
			intelTiles.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		CurrentMeeting meeting = currentMeeting;

		// Drop the meeting once its window has fully rotated out.
		if (meeting != null && meeting.rotationEndMs() <= System.currentTimeMillis())
		{
			clearMeeting();
			return;
		}

		if (meeting != null)
		{
			// Record the current world as seen this cycle (boss gender fills in via §3 handler).
			recordCurrentWorld();
		}

		meetingTimers.update(meeting, config, this, markerIcon, markerIcon);
		refreshPanel();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == NO_INFO_GROUP_ID)
		{
			clearMeeting();
			return;
		}

		if (event.getGroupId() == NOTICE_BOARD_GROUP_ID)
		{
			// Read on the client thread once the widget has populated.
			clientThread.invokeLater(() -> readBoard(NOTICE_BOARD_GROUP_ID));
		}
	}

	private void readBoard(int groupId)
	{
		List<String> lines = collectBoardText(groupId);
		NoticeBoardReader.BoardReadResult result = boardReader.read(lines);
		if (result == null)
		{
			return;
		}

		if (config.logRotations())
		{
			rotationLogger.log(result.getLocation().getId());
		}

		long now = System.currentTimeMillis();
		long scheduled = now;
		if (result.getMinutesUntil() != null)
		{
			scheduled = now + result.getMinutesUntil() * 60_000L;
		}

		setMeeting(new CurrentMeeting(result.getLocation(), scheduled, now));

		if (!result.isExactMatch())
		{
			log.warn("Notice board matched only fuzzily to {}", result.getLocation().getId());
		}
	}

	private void setMeeting(CurrentMeeting meeting)
	{
		CurrentMeeting previous = currentMeeting;
		OcLocation location = meeting.getLocation();

		// A different location means a new cycle — reset the per-cycle world list and tracked NPCs.
		if (previous == null || !previous.getLocation().getId().equals(location.getId()))
		{
			worldStatuses.clear();
			gangsters.clear();
			bosses.clear();
		}

		currentMeeting = meeting;
		worldStatuses.computeIfAbsent(client.getWorld(), WorldStatus::new);

		if (config.showWorldMapMarker())
		{
			worldMapController.set(location, worldMapIcon);
		}
		else
		{
			worldMapController.clear();
		}

		updateHintArrow();
		routeTo(location.getWorldPoint());
		meetingTimers.update(meeting, config, this, markerIcon, markerIcon);
		persist();
		refreshPanel();
		log.debug("Current organised crime meeting: {} ({})", location.getId(), location.getArea());
	}

	private void clearMeeting()
	{
		currentMeeting = null;
		worldStatuses.clear();
		gangsters.clear();
		bosses.clear();
		worldMapController.clear();
		meetingTimers.clear();
		client.clearHintArrow();
		clearRoute();
		meetingStore.clear();
		refreshPanel();
	}

	/** Record the player's current world for this cycle, persisting only when it's newly seen. */
	private void recordCurrentWorld()
	{
		int world = client.getWorld();
		if (worldStatuses.containsKey(world))
		{
			return;
		}
		worldStatuses.put(world, new WorldStatus(world));
		persist();
	}

	private void persist()
	{
		meetingStore.save(currentMeeting, worldStatuses.values());
	}

	private void refreshPanel()
	{
		if (panel != null)
		{
			panel.refresh();
		}
	}

	private void updateHintArrow()
	{
		CurrentMeeting meeting = currentMeeting;
		if (meeting == null)
		{
			client.clearHintArrow();
			return;
		}

		if (config.showHintArrow() && meeting.getLocation().hasWorldPoint())
		{
			client.setHintArrow(meeting.getLocation().getWorldPoint());
		}
		else
		{
			client.clearHintArrow();
		}
	}

	// ------------------------------------------------------------- Shortest Path (§2)

	/**
	 * Ask the Shortest Path plugin (if installed) to draw a route to the target, via a loose-coupled
	 * {@link PluginMessage}. Debounced on {@link #lastRouted} so an unchanged target doesn't kick off
	 * a pathfinder recompute every tick. If Shortest Path isn't present the message is simply unheard;
	 * the map pin and tile overlay are the always-works fallback.
	 */
	private void routeTo(WorldPoint target)
	{
		if (!config.routeViaShortestPath() || target == null || target.equals(lastRouted))
		{
			return;
		}
		lastRouted = target;
		Map<String, Object> data = new HashMap<>();
		data.put("target", target); // "start" omitted → SP uses the player's location
		// Shortest Path handles this synchronously and reads the player's world location, which
		// asserts the client thread — so post there (callers include startUp and config toggles).
		clientThread.invoke(() -> eventBus.post(new PluginMessage("shortestpath", "path", data)));
	}

	private void clearRoute()
	{
		if (lastRouted == null)
		{
			return;
		}
		lastRouted = null;
		clientThread.invoke(() -> eventBus.post(new PluginMessage("shortestpath", "clear", new HashMap<>())));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!IntelligenceGatheringConfig.GROUP.equals(event.getGroup())
			|| !"routeViaShortestPath".equals(event.getKey()))
		{
			return;
		}

		if (config.routeViaShortestPath())
		{
			CurrentMeeting meeting = currentMeeting;
			if (meeting != null)
			{
				routeTo(meeting.getLocation().getWorldPoint());
			}
		}
		else
		{
			clearRoute();
		}
	}

	// ------------------------------------------------------ Boss gender / NPC tracking (§3/§4)

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (currentMeeting == null)
		{
			return;
		}

		NPC npc = event.getNpc();
		int id = npc.getId();
		if (gangIds.isBoss(id))
		{
			bosses.add(npc);
			applyBossGender(gangIds.bossGender(id));
		}
		else if (gangIds.isGangster(id))
		{
			gangsters.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		bosses.remove(npc);
		gangsters.remove(npc);
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (event.getItem().getId() == ItemID.SHAYZIEN_GANG_INTELLIGENCE)
		{
			intelTiles.add(event.getTile());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (event.getItem().getId() == ItemID.SHAYZIEN_GANG_INTELLIGENCE)
		{
			intelTiles.remove(event.getTile());
		}
	}

	/** Record the gang boss's gender for the player's current world (spec §3). */
	private void applyBossGender(BossGender gender)
	{
		if (gender == BossGender.UNKNOWN)
		{
			return;
		}
		WorldStatus status = worldStatuses.computeIfAbsent(client.getWorld(), WorldStatus::new);
		if (status.getBossGender() != gender)
		{
			status.setBossGender(gender);
			persist();
			refreshPanel();
		}
	}

	/**
	 * Pull all text from the board widget's children. Deliberately scans a range of child
	 * indices and recurses so it does not depend on a specific child layout (spec §3.3).
	 */
	private List<String> collectBoardText(int groupId)
	{
		List<String> out = new ArrayList<>();
		for (int childId = 0; childId < 40; childId++)
		{
			Widget w = client.getWidget(groupId, childId);
			collectText(w, out);
		}
		return out;
	}

	private static void collectText(Widget w, List<String> out)
	{
		if (w == null)
		{
			return;
		}

		String text = w.getText();
		if (text != null && !text.trim().isEmpty())
		{
			out.add(text);
		}

		addChildren(w.getStaticChildren(), out);
		addChildren(w.getDynamicChildren(), out);
		addChildren(w.getNestedChildren(), out);
	}

	private static void addChildren(Widget[] children, List<String> out)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			collectText(child, out);
		}
	}

	// ------------------------------------------------------ IntelligenceGatheringController

	@Override
	public List<WorldStatus> getWorldStatuses()
	{
		return new ArrayList<>(worldStatuses.values());
	}

	@Override
	public int getCurrentWorld()
	{
		return client.getWorld();
	}

	@Override
	public boolean isSafeWorldsOnly()
	{
		return config.safeWorldsOnly();
	}

	@Override
	public void hop(int world)
	{
		worldHopper.hop(world);
	}

	@Provides
	IntelligenceGatheringConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IntelligenceGatheringConfig.class);
	}

	private static BufferedImage buildIcon()
	{
		try
		{
			BufferedImage badge = ImageUtil.loadImageResource(IntelligenceGatheringPlugin.class,
				"/com/tzjakejad/intelligencegathering/gangboss-badge_96.png");
			if (badge != null)
			{
				return badge;
			}
		}
		catch (RuntimeException e)
		{
			// Badge resource missing — fall through to the drawn pin below.
		}

		// Fallback: simple map pin if the badge resource is missing.
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Simple map pin.
		g.setColor(new Color(200, 60, 60));
		g.fillOval(3, 1, 10, 10);
		g.setColor(new Color(120, 20, 20));
		g.drawOval(3, 1, 10, 10);
		g.setColor(new Color(200, 60, 60));
		int[] xs = {5, 11, 8};
		int[] ys = {9, 9, 15};
		g.fillPolygon(xs, ys, 3);
		g.setColor(Color.WHITE);
		g.fillOval(6, 4, 4, 4);
		g.dispose();
		return img;
	}
}
