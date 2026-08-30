package com.tzjakejad.intelligencegathering;

import com.google.inject.Provides;
import com.tzjakejad.intelligencegathering.board.NoticeBoardReader;
import com.tzjakejad.intelligencegathering.combat.GangIds;
import com.tzjakejad.intelligencegathering.combat.GroundItemOverlay;
import com.tzjakejad.intelligencegathering.combat.NpcHighlightOverlay;
import com.tzjakejad.intelligencegathering.data.MeetingStore;
import com.tzjakejad.intelligencegathering.data.RotationLogger;
import com.tzjakejad.intelligencegathering.data.ShareCode;
import com.tzjakejad.intelligencegathering.model.BossGender;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import com.tzjakejad.intelligencegathering.hop.WorldHopper;
import com.tzjakejad.intelligencegathering.nav.SceneOverlay;
import com.tzjakejad.intelligencegathering.nav.WorldMapController;
import com.tzjakejad.intelligencegathering.rewards.XpRewardLock;
import com.tzjakejad.intelligencegathering.timer.MeetingTimers;
import com.tzjakejad.intelligencegathering.ui.IntelligenceGatheringController;
import com.tzjakejad.intelligencegathering.ui.IntelligenceGatheringPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;
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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
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

	@Inject
	private ItemManager itemManager;

	@Inject
	private XpRewardLock xpRewardLock;

	@Inject
	private ShareCode shareCode;

	@Getter
	private volatile CurrentMeeting currentMeeting;

	/** Worlds visited during the current cycle, keyed by world number. */
	private final Map<Integer, WorldStatus> worldStatuses = new ConcurrentSkipListMap<>();

	/** Tracked meeting NPCs for the highlight overlay (spec §4); mutated on the client thread. */
	private final Set<NPC> gangsters = ConcurrentHashMap.newKeySet();
	private final Set<NPC> bosses = ConcurrentHashMap.newKeySet();

	/** Tiles holding dropped gang intelligence (item 13395), for the ground highlight. */
	private final Set<Tile> intelTiles = ConcurrentHashMap.newKeySet();

	/**
	 * Present in all 34 known board messages and specific enough that no other interface trips it.
	 * Gates the full match so the board can be found without hardcoding which interface renders it.
	 */
	private static final Pattern BOARD_SENTINEL = Pattern.compile("received\\s+reports", Pattern.CASE_INSENSITIVE);

	/**
	 * A message box has to be saying there is nothing, not merely talking about gangs, before it
	 * is allowed to clear a meeting. Word boundaries keep "no" from matching inside "north".
	 */
	private static final Pattern NO_MEETING = Pattern.compile(
		"\\b(no|not|none|nothing|never|empty|quiet|aren't|isn't|haven't|hasn't|don't)\\b",
		Pattern.CASE_INSENSITIVE);

	/**
	 * Last board read the tracking filters discarded, kept only so the panel can say why it is empty.
	 * Held alongside the reason and expired with its own rotation window.
	 */
	private volatile CurrentMeeting excludedMeeting;
	private volatile String excludedReason;

	/** Last target posted to Shortest Path, for the §2 debounce. */
	private WorldPoint lastRouted;

	/** World seen on the previous game tick; the panel is only rebuilt when it changes. */
	private int lastWorld = -1;

	private BufferedImage markerIcon;
	/** Intelligence item sprite for the despawn infobox; null until fetched on the client thread. */
	private volatile BufferedImage intelIcon;
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
		clientThread.invokeLater(() ->
		{
			intelIcon = itemManager.getImage(ItemID.SHAYZIEN_GANG_INTELLIGENCE);
			restoreState();
		});

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
		applyWorldMapMarker();
		updateHintArrow();
		routeTo(location.getWorldPoint());
		meetingTimers.update(currentMeeting, config, this, markerIcon, despawnIcon());
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
		lastWorld = -1;
		intelIcon = null;
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
		worldHopper.processPendingHop();
		xpRewardLock.markLockedOptions();

		CurrentMeeting meeting = currentMeeting;

		CurrentMeeting excluded = excludedMeeting;
		if (excluded != null && excluded.rotationEndMs() <= System.currentTimeMillis())
		{
			excludedMeeting = null;
			excludedReason = null;
			refreshPanel();
		}

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

		meetingTimers.update(meeting, config, this, markerIcon, despawnIcon());

		// The panel's own 1s ticker keeps the countdowns fresh; a full rebuild is only needed when
		// the world changes (hop list "here" marker and Hop button enablement follow the world).
		int world = client.getWorld();
		if (world != lastWorld)
		{
			lastWorld = world;
			refreshPanel();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		xpRewardLock.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Resolve the id now rather than inside the lambda, which runs a tick later.
		int groupId = event.getGroupId();
		// Read on the client thread once the widget has populated.
		clientThread.invokeLater(() -> readInterface(groupId));
	}

	/**
	 * Look for the notice board in a freshly loaded interface, whichever one it is.
	 *
	 * <p>Keying off a single interface id was too brittle: every known board message contains the
	 * word "gang", which is also one of the sentinels {@link #readMessageBox} treats as "no meeting".
	 * So if the board ever renders through MESSAGEBOX rather than NOTE, the old dispatch would both
	 * fail to parse the location and clear the meeting it should have set. Matching on the text
	 * instead removes the guess: {@link #BOARD_SENTINEL} appears in all 34 board messages and
	 * nothing else, so an unrelated interface is discarded before the matcher ever sees it.
	 */
	private void readInterface(int groupId)
	{
		List<String> lines = collectBoardText(groupId);

		if (readBoard(groupId, lines))
		{
			return;
		}

		// Only once the text is known not to be a board reading can a message box mean "no meeting".
		if (groupId == InterfaceID.MESSAGEBOX)
		{
			readMessageBox(lines);
		}
	}

	/**
	 * MESSAGEBOX is the generic text dialog used all over the game, so it only means "no meeting"
	 * when it actually carries the notice board's no-intelligence message — an unrelated message
	 * box must not wipe the tracked meeting. Failing closed is cheap: a missed clear self-corrects
	 * on the next board read or when the rotation window lapses.
	 *
	 * <p>Being on-topic is not enough on its own. The Shayzien Encampment is full of dialogue about
	 * the gangs, and the board's own reading contains "gang" too, so a topic word alone would let an
	 * ordinary conversation wipe a meeting that had just been read. A clear needs the message to
	 * also be saying that there is <em>nothing</em>.
	 */
	private void readMessageBox(List<String> lines)
	{
		if (currentMeeting == null)
		{
			return;
		}

		String text = String.join(" ", lines).toLowerCase();
		boolean topical = text.contains("intelligence") || text.contains("gang")
			|| text.contains("noticeboard") || text.contains("notice board");
		if (topical && NO_MEETING.matcher(text).find())
		{
			log.info("Message box reports no gang activity; clearing the meeting: {}", text);
			clearMeeting();
		}
	}

	/**
	 * @return true when the text was a board reading, whether or not it resolved to a location —
	 *     either way it is not a "no meeting" message box.
	 */
	private boolean readBoard(int groupId, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return false;
		}

		String joined = String.join(" ", lines);
		if (!BOARD_SENTINEL.matcher(joined).find())
		{
			return false;
		}

		if (groupId != InterfaceID.NOTE)
		{
			// Worth knowing: the interface the board actually uses was inferred, not confirmed.
			log.info("Notice board text found on interface {}, not the expected {}", groupId, InterfaceID.NOTE);
		}

		NoticeBoardReader.BoardReadResult result = boardReader.read(lines);
		if (result == null)
		{
			log.warn("Interface {} carries notice board text that matched no known location: {}", groupId, joined);
			return true;
		}

		if (config.logRotations())
		{
			rotationLogger.log(result.getLocation().getId());
		}

		// An untracked location still gets its rotation row logged above (the CSV records what was
		// observed, not what was followed) — it just isn't shown or navigated to.
		// This is the one gate between a row reaching rotations.csv and the panel updating, so it
		// says so loudly rather than at debug: from the panel a filtered read is indistinguishable
		// from a parse failure.
		String exclusion = trackingExclusion(result.getLocation());
		if (exclusion != null)
		{
			log.warn("Read a meeting at {} ({}) but the {} setting excludes it, so the panel stays empty",
				result.getLocation().getId(), result.getLocation().getArea(), exclusion);
			// clearMeeting() resets the panel, so record the reason after it and refresh again.
			clearMeeting();
			excludedMeeting = new CurrentMeeting(result.getLocation(), scheduledMs(result), System.currentTimeMillis());
			excludedReason = exclusion;
			refreshPanel();
			return true;
		}

		setMeeting(new CurrentMeeting(result.getLocation(), scheduledMs(result), System.currentTimeMillis()));

		if (!result.isExactMatch())
		{
			log.warn("Notice board matched only fuzzily to {}", result.getLocation().getId());
		}
		return true;
	}

	/** Board countdown resolved to an absolute time; absent means the meeting is already due. */
	private static long scheduledMs(NoticeBoardReader.BoardReadResult result)
	{
		long now = System.currentTimeMillis();
		return result.getMinutesUntil() == null ? now : now + result.getMinutesUntil() * 60_000L;
	}

	private void setMeeting(CurrentMeeting meeting)
	{
		// A meeting we are actually showing supersedes any note about a filtered one.
		excludedMeeting = null;
		excludedReason = null;

		CurrentMeeting previous = currentMeeting;
		OcLocation location = meeting.getLocation();

		// A different location means a new cycle — reset the per-cycle world list and tracked NPCs.
		if (previous == null || !previous.getLocation().getId().equals(location.getId()))
		{
			worldStatuses.clear();
			gangsters.clear();
			bosses.clear();
		}

		activateMeeting(meeting);
		log.debug("Current organised crime meeting: {} ({})", location.getId(), location.getArea());
	}

	/**
	 * Install a meeting as the live one and reconcile every derived display. Shared by the local
	 * board read and by {@link #applyShared}.
	 */
	private void activateMeeting(CurrentMeeting meeting)
	{
		currentMeeting = meeting;
		worldStatuses.computeIfAbsent(client.getWorld(), WorldStatus::new);

		applyWorldMapMarker();
		updateHintArrow();
		routeTo(meeting.getLocation().getWorldPoint());
		meetingTimers.update(meeting, config, this, markerIcon, despawnIcon());
		persist();
		refreshPanel();
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

	/** Whether the tracking filters (area toggles + multicombat-only) include this location. */
	private boolean isTracked(OcLocation location)
	{
		return trackingExclusion(location) == null;
	}

	/**
	 * The name of the setting that excludes this location, or null when it passes. Returning the
	 * reason rather than a bare boolean is what lets a discarded board read explain itself: the
	 * filters are invisible from the panel, so a silent drop looks exactly like a failure to parse.
	 */
	private String trackingExclusion(OcLocation location)
	{
		if (config.multiCombatOnly() && !location.isMulti())
		{
			return "Multicombat only";
		}
		switch (location.getArea())
		{
			case "Arceuus":
				return config.trackArceuus() ? null : "Track Arceuus";
			case "Hosidius":
				return config.trackHosidius() ? null : "Track Hosidius";
			case "Lovakengj":
				return config.trackLovakengj() ? null : "Track Lovakengj";
			case "Piscarilius":
				return config.trackPiscarilius() ? null : "Track Piscarilius";
			case "Shayzien":
				return config.trackShayzien() ? null : "Track Shayzien";
			default:
				return config.trackOther() ? null : "Track Other";
		}
	}

	/** Reconcile the world-map marker with the current meeting and config. */
	private void applyWorldMapMarker()
	{
		CurrentMeeting meeting = currentMeeting;
		if (config.showWorldMapMarker() && meeting != null)
		{
			worldMapController.set(meeting.getLocation(), worldMapIcon);
		}
		else
		{
			worldMapController.clear();
		}
	}

	/** Despawn infobox icon: the intelligence item sprite once fetched, else the plugin badge. */
	private BufferedImage despawnIcon()
	{
		BufferedImage icon = intelIcon;
		return icon != null ? icon : markerIcon;
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
		if (!IntelligenceGatheringConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "routeViaShortestPath":
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
				break;
			case "showHintArrow":
				// Config changes arrive on the Swing thread; the hint arrow is client state.
				clientThread.invokeLater(this::updateHintArrow);
				break;
			case "showWorldMapMarker":
				applyWorldMapMarker();
				break;
			case "safeWorldsOnly":
				refreshPanel();
				break;
			case "multiCombatOnly":
			case "trackArceuus":
			case "trackHosidius":
			case "trackLovakengj":
			case "trackPiscarilius":
			case "trackShayzien":
			case "trackOther":
			{
				// Tightening a filter drops a now-excluded meeting. Loosening one can't bring a
				// dropped meeting back (its state is gone) — the next board read picks it up.
				CurrentMeeting tracked = currentMeeting;
				if (tracked != null && !isTracked(tracked.getLocation()))
				{
					// clearMeeting touches the hint arrow, which is client state.
					clientThread.invokeLater(this::clearMeeting);
				}
				break;
			}
			default:
				break;
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
		// A tracked boss despawning dead means this world's meeting was cleared. The bosses set is
		// emptied on hop/scene teardown before their despawns arrive, so remove() returning true
		// distinguishes a live-scene despawn from teardown; isDead filters walking-away despawns.
		if (bosses.remove(npc) && npc.isDead() && currentMeeting != null)
		{
			markWorldCleared();
		}
		gangsters.remove(npc);
	}

	/** Mark the player's current world cleared for this cycle (the panel's "done" tag). */
	private void markWorldCleared()
	{
		WorldStatus status = worldStatuses.computeIfAbsent(client.getWorld(), WorldStatus::new);
		status.setLastClearedMs(System.currentTimeMillis());
		persist();
		refreshPanel();
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

	@Override
	public String getFilterNotice()
	{
		CurrentMeeting excluded = excludedMeeting;
		String reason = excludedReason;
		if (excluded == null || reason == null || excluded.rotationEndMs() <= System.currentTimeMillis())
		{
			return null;
		}
		return "Read a meeting in " + excluded.getLocation().getArea()
			+ ", but your \"" + reason + "\" setting is hiding it.";
	}

	// ------------------------------------------------------------------ Share codes

	@Override
	public String exportShareCode()
	{
		CurrentMeeting meeting = currentMeeting;
		return meeting == null ? null : shareCode.encode(meeting, worldStatuses.values());
	}

	@Override
	public String importShareCode(String code)
	{
		ShareCode.Decoded decoded;
		try
		{
			decoded = shareCode.decode(code);
		}
		catch (ShareCode.InvalidCodeException e)
		{
			return e.getMessage();
		}

		OcLocation location = decoded.getMeeting().getLocation();
		// The sender's tracking filters are not ours: a shared meeting in an area the user has
		// switched off should say so rather than silently reinstating it.
		if (!isTracked(location))
		{
			return "That meeting is in " + location.getArea() + ", which your tracking filters exclude.";
		}

		clientThread.invoke(() -> applyShared(decoded));
		return null;
	}

	/**
	 * Adopt an imported cycle. An import is an explicit request, so the code's meeting wins outright
	 * rather than being arbitrated against ours — but its world list is merged, not substituted, so
	 * importing never discards scouting the importer did first-hand.
	 */
	private void applyShared(ShareCode.Decoded decoded)
	{
		CurrentMeeting meeting = decoded.getMeeting();
		CurrentMeeting previous = currentMeeting;

		if (previous == null || !previous.getLocation().getId().equals(meeting.getLocation().getId()))
		{
			// A different location means a different cycle — everything we knew belongs to the old one.
			worldStatuses.clear();
			gangsters.clear();
			bosses.clear();
		}

		for (WorldStatus incoming : decoded.getWorlds())
		{
			WorldStatus status = worldStatuses.get(incoming.getWorld());
			if (status == null)
			{
				status = new WorldStatus(incoming.getWorld());
				worldStatuses.put(incoming.getWorld(), status);
			}

			// A gender we watched spawn ourselves outranks a shared one; otherwise fill the blank.
			if (status.getBossGender() == BossGender.UNKNOWN)
			{
				status.setBossGender(incoming.getBossGender());
			}

			// Meetings are not instanced, so someone else's clear consumed that world for us too.
			if (incoming.getLastClearedMs() > status.getLastClearedMs())
			{
				status.setLastClearedMs(incoming.getLastClearedMs());
			}
		}

		activateMeeting(meeting);
		log.debug("Imported meeting {} with {} worlds", meeting.getLocation().getId(), decoded.getWorlds().size());
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
