package com.tzjakejad.intelligencegathering;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(IntelligenceGatheringConfig.GROUP)
public interface IntelligenceGatheringConfig extends Config
{
	String GROUP = "intelligence-gathering";

	@ConfigSection(
		name = "Tracking",
		description = "Which areas to track from the notice board",
		position = 0
	)
	String trackingSection = "tracking";

	@ConfigSection(
		name = "Navigation",
		description = "World map, minimap and tile markers",
		position = 1
	)
	String navigationSection = "navigation";

	@ConfigSection(
		name = "Combat",
		description = "Gangster and boss highlighting",
		position = 2
	)
	String combatSection = "combat";

	@ConfigSection(
		name = "Timers",
		description = "Rotation and despawn infoboxes",
		position = 3
	)
	String timersSection = "timers";

	@ConfigSection(
		name = "Data",
		description = "Rotation logging",
		position = 4
	)
	String dataSection = "data";

	@ConfigSection(
		name = "Rewards",
		description = "Guards on the reward XP dialog",
		position = 5
	)
	String rewardsSection = "rewards";

	// ------------------------------------------------------------------ Tracking

	@ConfigItem(
		keyName = "multiCombatOnly",
		name = "Multicombat only",
		description = "Only show meetings in multicombat areas",
		section = trackingSection,
		position = 0
	)
	default boolean multiCombatOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "trackArceuus",
		name = "Track Arceuus",
		description = "Show meetings in the Arceuus area",
		section = trackingSection,
		position = 1
	)
	default boolean trackArceuus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackHosidius",
		name = "Track Hosidius",
		description = "Show meetings in the Hosidius area",
		section = trackingSection,
		position = 2
	)
	default boolean trackHosidius()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackLovakengj",
		name = "Track Lovakengj",
		description = "Show meetings in the Lovakengj area",
		section = trackingSection,
		position = 3
	)
	default boolean trackLovakengj()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackPiscarilius",
		name = "Track Piscarilius",
		description = "Show meetings in the Port Piscarilius area",
		section = trackingSection,
		position = 4
	)
	default boolean trackPiscarilius()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackShayzien",
		name = "Track Shayzien",
		description = "Show meetings in the Shayzien area",
		section = trackingSection,
		position = 5
	)
	default boolean trackShayzien()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackOther",
		name = "Track Other",
		description = "Show meetings in other Kourend locations",
		section = trackingSection,
		position = 6
	)
	default boolean trackOther()
	{
		return true;
	}

	// ---------------------------------------------------------------- Navigation

	@ConfigItem(
		keyName = "showWorldMapMarker",
		name = "World map marker",
		description = "Drop a marker on the world map at the current meeting spawn",
		section = navigationSection,
		position = 0
	)
	default boolean showWorldMapMarker()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTileHighlight",
		name = "Tile highlight",
		description = "Highlight the spawn tiles when in the meeting region",
		section = navigationSection,
		position = 2
	)
	default boolean showTileHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHintArrow",
		name = "Hint arrow to meeting",
		description = "Point the game hint arrow at the meeting. Shows on the minimap (clamped to the "
			+ "edge when far) and in the world, at any distance or floor. Only one hint arrow exists "
			+ "globally, so it competes with other plugins that use it.",
		section = navigationSection,
		position = 3
	)
	default boolean showHintArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tileColor",
		name = "Tile colour",
		description = "Colour of the scene tile highlight",
		section = navigationSection,
		position = 4
	)
	default Color tileColor()
	{
		return new Color(0, 200, 255);
	}

	@ConfigItem(
		keyName = "routeViaShortestPath",
		name = "Route via Shortest Path",
		description = "If the Shortest Path plugin is installed, draw a walking route to the meeting. "
			+ "The map pin and tile highlight always work regardless of this setting.",
		section = navigationSection,
		position = 6
	)
	default boolean routeViaShortestPath()
	{
		return false;
	}

	// -------------------------------------------------------------------- Combat

	@ConfigItem(
		keyName = "highlightGangsters",
		name = "Highlight gangsters",
		description = "Outline gangster NPCs at the meeting",
		section = combatSection,
		position = 0
	)
	default boolean highlightGangsters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightBoss",
		name = "Highlight boss",
		description = "Highlight the gang boss distinctly (always drops intelligence)",
		section = combatSection,
		position = 1
	)
	default boolean highlightBoss()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bossHighlightStyle",
		name = "Highlight style",
		description = "How to render the gangster and boss highlights",
		section = combatSection,
		position = 2
	)
	default BossHighlightStyle bossHighlightStyle()
	{
		return BossHighlightStyle.OUTLINE;
	}

	@ConfigItem(
		keyName = "gangsterColor",
		name = "Gangster colour",
		description = "Highlight colour for gangster NPCs",
		section = combatSection,
		position = 3
	)
	default Color gangsterColor()
	{
		return new Color(255, 220, 0);
	}

	@ConfigItem(
		keyName = "bossColorDangerous",
		name = "Boss colour (dangerous)",
		description = "Boss outline when it throws knives (male / ranged / dangerous)",
		section = combatSection,
		position = 4
	)
	default Color bossColorDangerous()
	{
		return new Color(200, 60, 60);
	}

	@ConfigItem(
		keyName = "bossColorSafe",
		name = "Boss colour (safe)",
		description = "Boss outline when it fights with a cutlass (female / melee / safe)",
		section = combatSection,
		position = 5
	)
	default Color bossColorSafe()
	{
		return new Color(60, 170, 60);
	}

	@ConfigItem(
		keyName = "bossColor",
		name = "Boss colour (unknown)",
		description = "Boss outline before its combat style has been seen; classified bosses use the "
			+ "dangerous/safe colours above",
		section = combatSection,
		position = 6
	)
	default Color bossColor()
	{
		return new Color(255, 90, 0);
	}

	@ConfigItem(
		keyName = "safeWorldsOnly",
		name = "Safe worlds only",
		description = "Hide worlds with a male (dangerous) boss in the hop list",
		section = combatSection,
		position = 7
	)
	default boolean safeWorldsOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightIntel",
		name = "Highlight dropped intelligence",
		description = "Highlight Shayzien gang intelligence (the boss drop) while it's on the ground",
		section = combatSection,
		position = 8
	)
	default boolean highlightIntel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "intelColor",
		name = "Intelligence colour",
		description = "Highlight colour for dropped intelligence",
		section = combatSection,
		position = 9
	)
	default Color intelColor()
	{
		return new Color(190, 80, 255);
	}

	// -------------------------------------------------------------------- Timers

	@ConfigItem(
		keyName = "showDespawnTimer",
		name = "Despawn timer",
		description = "Infobox counting down the 5-minute despawn window",
		section = timersSection,
		position = 0
	)
	default boolean showDespawnTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRotationTimer",
		name = "Meeting start timer",
		description = "Infobox counting down until the gang meeting starts (matches the notice board)",
		section = timersSection,
		position = 1
	)
	default boolean showRotationTimer()
	{
		return true;
	}

	// ---------------------------------------------------------------------- Data

	@ConfigItem(
		keyName = "logRotations",
		name = "Log rotations",
		description = "Append observed (timestamp, location) rows to rotations.csv for analysis",
		section = dataSection,
		position = 0
	)
	default boolean logRotations()
	{
		return false;
	}

	// ------------------------------------------------------------------- Rewards

	@ConfigItem(
		keyName = "lockXpReward",
		name = "Lock reward XP skills",
		description = "Ignore clicks on skills you have not allowed in the \"What kind of training will you pursue?\" dialog",
		section = rewardsSection,
		position = 0
	)
	default boolean lockXpReward()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowedXpSkills",
		name = "Allowed skills",
		description = "Skills that stay clickable. Ctrl-click to pick more than one; selecting none blocks every skill",
		section = rewardsSection,
		position = 1
	)
	default Set<RewardSkill> allowedXpSkills()
	{
		return EnumSet.of(RewardSkill.DEFENCE);
	}

	enum BossHighlightStyle
	{
		HULL,
		TILE,
		OUTLINE
	}

	/**
	 * The four skills the reward dialog offers, in the order it lists them. Deliberately not the
	 * full skill list: the chest only ever trains combat, and a longer list would just be noise to
	 * scroll past. Constant names must match the skill named in the dialog row, which is how
	 * {@code XpRewardLock} pairs an option with its entry here.
	 */
	enum RewardSkill
	{
		ATTACK,
		STRENGTH,
		DEFENCE,
		HITPOINTS;

		@Override
		public String toString()
		{
			return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
		}
	}
}
