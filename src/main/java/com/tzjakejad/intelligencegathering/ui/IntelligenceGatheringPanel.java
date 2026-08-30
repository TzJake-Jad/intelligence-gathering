package com.tzjakejad.intelligencegathering.ui;

import com.tzjakejad.intelligencegathering.model.BossGender;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel (spec §3.7): a current-meeting card on top (location, directions, live rotation/despawn
 * countdowns, map crop) and a hop list below of worlds seen this cycle, annotated with boss gender
 * and cleared state.
 */
public class IntelligenceGatheringPanel extends PluginPanel
{
	private static final int IMAGE_SIZE = 185;
	private static final int CONTENT_WIDTH = 170;
	private static final int COMBAT_ICON_SIZE = 16;

	private static final Color SEPARATOR = new Color(60, 60, 60);
	private static final Color ROW_HOVER = new Color(40, 40, 40);
	private static final Color OK_GREEN = new Color(90, 190, 90);
	private static final Color WARN_AMBER = new Color(220, 150, 60);
	private static final Color DANGER_RED = new Color(210, 70, 70);

	private final IntelligenceGatheringController controller;
	private final SkillIconManager skillIconManager;

	/** Scaled location screenshots by location id; loading + SCALE_SMOOTH is too slow to redo per rebuild. */
	private final Map<String, ImageIcon> locationImages = new HashMap<>();

	private final JPanel cardPanel = new JPanel(new BorderLayout());
	private final JPanel hopContainer = new JPanel();

	/** Ticks the live countdowns once a second without rebuilding the whole panel. */
	private final Timer ticker = new Timer(1000, e -> tick());

	/** Rebuilt with the card; null when no meeting is currently shown. The primary row flips
	 *  between "Spawns" (before appearance) and "Despawns" (once the gangsters are up). */
	private JLabel primaryCaption;
	private JLabel primaryValue;
	private JLabel rotationValue;

	/** Share row: "Copy" is only meaningful with a meeting to copy, and the note reports both outcomes. */
	private final JButton copyButton = new JButton("Copy code");
	private final JLabel shareNote = new JLabel();

	/** Clears {@link #shareNote} a few seconds after it is set, so feedback doesn't linger stale. */
	private final Timer noteTimer = new Timer(4000, e -> setNoteText(" "));

	public IntelligenceGatheringPanel(IntelligenceGatheringController controller, SkillIconManager skillIconManager)
	{
		super(false);
		this.controller = controller;
		this.skillIconManager = skillIconManager;

		setLayout(new BorderLayout(0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(12, 12, 12, 12));

		cardPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cardPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		cardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		hopContainer.setLayout(new BoxLayout(hopContainer, BoxLayout.Y_AXIS));
		hopContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		hopContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		body.add(buildHeader());
		body.add(Box.createRigidArea(new Dimension(0, 10)));
		body.add(cardPanel);
		body.add(Box.createRigidArea(new Dimension(0, 12)));
		body.add(worldsSectionHeader());
		body.add(Box.createRigidArea(new Dimension(0, 6)));
		body.add(hopContainer);
		body.add(Box.createRigidArea(new Dimension(0, 12)));
		body.add(buildShareRow());

		add(body, BorderLayout.NORTH);

		refresh();
	}

	/** Header: plugin badge alongside the title. */
	private JPanel buildHeader()
	{
		JPanel header = leftPanel(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		BufferedImage badge = loadBadge();
		if (badge != null)
		{
			Image scaled = badge.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
			header.add(new JLabel(new ImageIcon(scaled)), BorderLayout.WEST);
		}

		JLabel title = new JLabel("Intelligence Gathering");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		header.add(title, BorderLayout.CENTER);
		return header;
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		ticker.start();
	}

	@Override
	public void removeNotify()
	{
		ticker.stop();
		noteTimer.stop();
		super.removeNotify();
	}

	/** Rebuild the whole panel from the controller. Safe to call from any thread. */
	public void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			rebuildCard();
			rebuildHopList();
			copyButton.setEnabled(controller.getCurrentMeeting() != null);
			revalidate();
			repaint();
		});
	}

	/** Refresh only the countdown labels; cheap enough to run every second. */
	private void tick()
	{
		CurrentMeeting meeting = controller.getCurrentMeeting();
		if (meeting == null || primaryValue == null || rotationValue == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		long untilSpawn = meeting.getScheduledAppearanceMs() - now;
		if (untilSpawn > 0)
		{
			// Gangsters haven't appeared yet — count down to the spawn.
			primaryCaption.setText("Spawns");
			applySpawn(primaryValue, untilSpawn);
		}
		else
		{
			// They're up (or already gone) — count down to despawn.
			primaryCaption.setText("Despawns");
			applyDespawn(primaryValue, meeting.despawnMs() - now);
		}
		applyCountdown(rotationValue, meeting.rotationEndMs() - now, Color.WHITE);
	}

	private void rebuildCard()
	{
		cardPanel.removeAll();
		primaryCaption = null;
		primaryValue = null;
		rotationValue = null;

		CurrentMeeting meeting = controller.getCurrentMeeting();
		if (meeting == null)
		{
			// A board read that the tracking filters discarded looks identical to one that never
			// parsed, so say which it was rather than leaving the user to guess at a bug.
			String notice = controller.getFilterNotice();
			JLabel empty = wrappedLabel(notice != null ? notice : "No meeting known — read a notice board.");
			if (notice != null)
			{
				empty.setForeground(WARN_AMBER);
			}
			cardPanel.add(empty, BorderLayout.CENTER);
			return;
		}

		OcLocation loc = meeting.getLocation();

		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel area = new JLabel(loc.getArea());
		area.setForeground(Color.WHITE);
		area.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		lines.add(area);
		lines.add(Box.createRigidArea(new Dimension(0, 6)));

		lines.add(wrappedLabel(loc.getNavHint()));

		// The plane is authoritative for the floor, so always surface an "Upstairs" cue for a raised
		// spawn — a couple of hints only say "middle floor" (arceuus3/4), which doesn't read as "go
		// up". Suppress the derived label only when the hint already states the same floor, so the
		// card never doubles up (§1.3).
		String multiPart = loc.isMulti() ? "Multi" : "Single-way";
		String floorLabel = derivedFloorLabel(loc.getNavHint(), loc.getPlane());
		String meta = floorLabel == null ? multiPart : floorLabel + "  •  " + multiPart;
		lines.add(Box.createRigidArea(new Dimension(0, 6)));
		lines.add(metaLabel(meta));

		if (!loc.hasWorldPoint())
		{
			lines.add(Box.createRigidArea(new Dimension(0, 4)));
			JLabel warn = metaLabel("Map marker unavailable (coord not captured)");
			warn.setForeground(WARN_AMBER);
			lines.add(warn);
		}

		// Live countdowns.
		lines.add(Box.createRigidArea(new Dimension(0, 8)));
		lines.add(separator());
		lines.add(Box.createRigidArea(new Dimension(0, 8)));

		primaryCaption = new JLabel();
		primaryValue = countdownValueLabel();
		rotationValue = countdownValueLabel();
		lines.add(countdownRow(primaryCaption, primaryValue));
		lines.add(Box.createRigidArea(new Dimension(0, 4)));
		lines.add(countdownRow(new JLabel("Rotates"), rotationValue));
		tick();

		// Location screenshot (the map crop from the original plugin), same as it displayed it.
		JLabel image = locationImage(loc);
		if (image != null)
		{
			lines.add(Box.createRigidArea(new Dimension(0, 10)));
			lines.add(image);
		}

		cardPanel.add(lines, BorderLayout.CENTER);
	}

	private void rebuildHopList()
	{
		hopContainer.removeAll();

		List<WorldStatus> statuses = controller.getWorldStatuses();
		int currentWorld = controller.getCurrentWorld();
		long cycleStartMs = cycleStartMs();

		if (statuses.isEmpty())
		{
			JLabel empty = new JLabel("No worlds visited yet.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			hopContainer.add(empty);
			return;
		}

		boolean first = true;
		for (WorldStatus status : statuses)
		{
			if (controller.isSafeWorldsOnly() && status.getBossGender() == BossGender.MALE)
			{
				continue;
			}
			if (!first)
			{
				hopContainer.add(Box.createRigidArea(new Dimension(0, 4)));
			}
			hopContainer.add(buildHopRow(status, currentWorld, cycleStartMs));
			first = false;
		}
	}

	private JPanel buildHopRow(WorldStatus status, int currentWorld, long cycleStartMs)
	{
		boolean isCurrent = status.getWorld() == currentWorld;
		boolean cleared = cycleStartMs > 0 && status.isClearedThisCycle(cycleStartMs);

		JPanel row = new JPanel(new BorderLayout(6, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// A left accent stripe marks the world you are on; others get a matching-width transparent
		// stripe so the text stays aligned.
		Color stripe = isCurrent ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR;
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, stripe),
			new EmptyBorder(4, 7, 4, 5)));

		BossGender gender = status.getBossGender();
		JLabel dot = combatStyleIcon(gender);

		JLabel worldLabel = new JLabel("World " + status.getWorld());
		worldLabel.setFont(FontManager.getRunescapeSmallFont());
		worldLabel.setForeground(isCurrent ? Color.WHITE
			: cleared ? ColorScheme.LIGHT_GRAY_COLOR.darker() : ColorScheme.LIGHT_GRAY_COLOR);

		JPanel left = new JPanel(new BorderLayout(6, 0));
		left.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		left.add(dot, BorderLayout.WEST);
		left.add(worldLabel, BorderLayout.CENTER);

		JPanel rightSide = new JPanel(new BorderLayout(6, 0));
		rightSide.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (isCurrent)
		{
			left.add(tag("here", ColorScheme.BRAND_ORANGE), BorderLayout.EAST);
		}
		else if (cleared)
		{
			rightSide.add(tag("done", OK_GREEN), BorderLayout.WEST);
		}

		JButton hopBtn = new JButton("Hop");
		hopBtn.setFont(FontManager.getRunescapeSmallFont());
		hopBtn.setFocusPainted(false);
		// Let the button size to its text; a forced tiny width truncates it to "H…" under the
		// RuneLite look-and-feel, so just trim the margin instead.
		hopBtn.setMargin(new Insets(2, 6, 2, 6));
		hopBtn.setEnabled(!isCurrent);
		hopBtn.addActionListener(e -> controller.hop(status.getWorld()));
		rightSide.add(hopBtn, BorderLayout.EAST);

		row.add(left, BorderLayout.CENTER);
		row.add(rightSide, BorderLayout.EAST);

		// Subtle hover so rows feel interactive, but never override the "here" highlight.
		if (!isCurrent)
		{
			addHover(row, left, rightSide);
		}
		return row;
	}

	/**
	 * Per-world combat-style marker: the Ranged icon for the knife-throwing (male, dangerous) boss and
	 * the Attack icon for the cutlass (female, safe) boss, so the style is legible at a glance rather
	 * than only colour-coded. Falls back to the neutral grey dot until a boss's style has been seen.
	 */
	private JLabel combatStyleIcon(BossGender gender)
	{
		Skill skill;
		switch (gender)
		{
			case MALE:
				skill = Skill.RANGED;
				break;
			case FEMALE:
				skill = Skill.ATTACK;
				break;
			default:
			{
				JLabel dot = new JLabel("●");
				dot.setForeground(gender.getColor());
				dot.setToolTipText(gender.getLabel());
				return dot;
			}
		}

		BufferedImage icon = skillIconManager.getSkillImage(skill, true);
		Image scaled = icon.getScaledInstance(COMBAT_ICON_SIZE, COMBAT_ICON_SIZE, Image.SCALE_SMOOTH);
		JLabel label = new JLabel(new ImageIcon(scaled));
		label.setToolTipText(gender.getLabel());
		return label;
	}

	private static void addHover(JPanel row, JPanel... children)
	{
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				paint(ROW_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				paint(ColorScheme.DARKER_GRAY_COLOR);
			}

			private void paint(Color bg)
			{
				row.setBackground(bg);
				for (JPanel child : children)
				{
					child.setBackground(bg);
				}
			}
		});
	}

	// --- countdown helpers -------------------------------------------------

	private long cycleStartMs()
	{
		CurrentMeeting meeting = controller.getCurrentMeeting();
		return meeting == null ? 0 : meeting.getScheduledAppearanceMs();
	}

	private static JLabel countdownValueLabel()
	{
		JLabel value = new JLabel();
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(Color.WHITE);
		return value;
	}

	/** A "Label ............ value" row that stretches the full card width. */
	private static JPanel countdownRow(JLabel caption, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(caption, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	/** Spawn counts down to the gangsters appearing; turns amber in the final minute. */
	private static void applySpawn(JLabel value, long remainingMs)
	{
		applyCountdown(value, remainingMs, remainingMs <= 60_000 ? WARN_AMBER : Color.WHITE);
	}

	/** Despawn counts down to gangsters leaving, going amber then red as it runs out. */
	private static void applyDespawn(JLabel value, long remainingMs)
	{
		if (remainingMs <= 0)
		{
			value.setText("gone");
			value.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			return;
		}
		Color color = remainingMs <= 60_000 ? DANGER_RED
			: remainingMs <= 120_000 ? WARN_AMBER : OK_GREEN;
		applyCountdown(value, remainingMs, color);
	}

	private static void applyCountdown(JLabel value, long remainingMs, Color color)
	{
		if (remainingMs <= 0)
		{
			value.setText("--:--");
			value.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			return;
		}
		long totalSeconds = remainingMs / 1000;
		value.setText(String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60));
		value.setForeground(color);
	}

	// --- static widget helpers ---------------------------------------------

	private static JLabel tag(String text, Color color)
	{
		JLabel tag = new JLabel(text);
		tag.setFont(FontManager.getRunescapeSmallFont());
		tag.setForeground(color);
		return tag;
	}

	/**
	 * Share row: copy the cycle to the clipboard, or import a code someone pasted you. Both go via
	 * the system clipboard rather than a text field — the code is long, and nobody types it by hand.
	 */
	private JComponent buildShareRow()
	{
		JPanel section = leftPanel(new BorderLayout(0, 4));

		JLabel header = new JLabel("SHARE");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);

		JPanel head = leftPanel(new BorderLayout(0, 4));
		head.add(header, BorderLayout.NORTH);
		head.add(separator(), BorderLayout.CENTER);

		JPanel buttons = leftPanel(new BorderLayout(6, 0));
		copyButton.setFont(FontManager.getRunescapeSmallFont());
		copyButton.setFocusPainted(false);
		copyButton.setMargin(new Insets(2, 6, 2, 6));
		copyButton.setToolTipText("Copy this cycle's meeting and world list to the clipboard");
		copyButton.addActionListener(e -> onCopy());

		JButton importButton = new JButton("Import");
		importButton.setFont(FontManager.getRunescapeSmallFont());
		importButton.setFocusPainted(false);
		importButton.setMargin(new Insets(2, 6, 2, 6));
		importButton.setToolTipText("Import a code from the clipboard");
		importButton.addActionListener(e -> onImport());

		buttons.add(copyButton, BorderLayout.WEST);
		buttons.add(importButton, BorderLayout.EAST);

		// A space rather than an empty string, so the row keeps its height and the buttons above it
		// do not jump when feedback appears and clears.
		shareNote.setText(" ");
		shareNote.setFont(FontManager.getRunescapeSmallFont());
		shareNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel below = leftPanel(new BorderLayout(0, 4));
		below.add(buttons, BorderLayout.NORTH);
		below.add(shareNote, BorderLayout.CENTER);

		section.add(head, BorderLayout.NORTH);
		section.add(below, BorderLayout.CENTER);
		noteTimer.setRepeats(false);
		return section;
	}

	private void onCopy()
	{
		String code = controller.exportShareCode();
		if (code == null)
		{
			note("Nothing to share yet — read the notice board first.", DANGER_RED);
			return;
		}

		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
		}
		catch (IllegalStateException e)
		{
			// Another application is holding the clipboard open; nothing to do but say so.
			note("Clipboard is busy — try again.", DANGER_RED);
			return;
		}
		note("Copied. Paste it to a friend.", OK_GREEN);
	}

	private void onImport()
	{
		String pasted;
		try
		{
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor))
			{
				note("No text on the clipboard to import.", DANGER_RED);
				return;
			}
			pasted = (String) clipboard.getData(DataFlavor.stringFlavor);
		}
		catch (IllegalStateException | UnsupportedFlavorException | IOException e)
		{
			note("Couldn't read the clipboard — try again.", DANGER_RED);
			return;
		}

		String error = controller.importShareCode(pasted);
		if (error != null)
		{
			note(error, DANGER_RED);
			return;
		}
		note("Imported.", OK_GREEN);
	}

	/** Show a short-lived line under the share buttons. */
	private void note(String text, Color color)
	{
		shareNote.setForeground(color);
		// The sidebar is narrow, so let the longer rejection messages wrap instead of being clipped.
		setNoteText("<html><body style='width:" + CONTENT_WIDTH + "px'>" + text + "</body></html>");
		noteTimer.restart();
	}

	/** A wrapped note changes the row's height, so the panel has to lay out again around it. */
	private void setNoteText(String text)
	{
		shareNote.setText(text);
		revalidate();
		repaint();
	}

	private JComponent worldsSectionHeader()
	{
		JPanel panel = leftPanel(new BorderLayout(0, 4));
		JLabel label = new JLabel("WORLDS THIS CYCLE");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		panel.add(label, BorderLayout.NORTH);
		panel.add(separator(), BorderLayout.CENTER);
		return panel;
	}

	private static JComponent separator()
	{
		JPanel line = new JPanel();
		line.setBackground(SEPARATOR);
		line.setPreferredSize(new Dimension(0, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}

	private JLabel locationImage(OcLocation loc)
	{
		ImageIcon icon = locationImages.computeIfAbsent(loc.getId(), IntelligenceGatheringPanel::loadLocationIcon);
		if (icon == null)
		{
			return null;
		}

		JLabel label = new JLabel(icon);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createLineBorder(SEPARATOR));
		return label;
	}

	private static ImageIcon loadLocationIcon(String locationId)
	{
		BufferedImage img;
		try
		{
			img = ImageUtil.loadImageResource(IntelligenceGatheringPanel.class,
				"/com/tzjakejad/intelligencegathering/ui/" + locationId + ".png");
		}
		catch (RuntimeException e)
		{
			// No bundled screenshot for this location — just omit it.
			return null;
		}

		if (img == null)
		{
			return null;
		}

		return new ImageIcon(img.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE, Image.SCALE_SMOOTH));
	}

	private static JLabel wrappedLabel(String text)
	{
		// HTML lets the label wrap within the fixed panel width.
		JLabel label = new JLabel("<html><body style='width:" + CONTENT_WIDTH + "px'>"
			+ escape(text) + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel metaLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** A background-matched panel that left-aligns inside the vertical BoxLayouts. */
	private static JPanel leftPanel(BorderLayout layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private BufferedImage loadBadge()
	{
		try
		{
			return ImageUtil.loadImageResource(IntelligenceGatheringPanel.class,
				"/com/tzjakejad/intelligencegathering/gangboss-badge_96.png");
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Floor descriptors that may appear verbatim in a nav hint (spec §1.3). */
	private static final String[] FLOOR_WORDS =
		{"ground floor", "upstairs", "downstairs", "middle floor", "top floor", "basement"};

	private static boolean hintNamesFloor(String hint)
	{
		if (hint == null)
		{
			return false;
		}
		String lower = hint.toLowerCase();
		for (String word : FLOOR_WORDS)
		{
			if (lower.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Floor line to show under the hint, or {@code null} when the hint already states it. Upstairs
	 * spawns always get an "Upstairs" cue unless the hint literally says so; ground spawns only get a
	 * label when the hint doesn't already name a floor (§1.3), to avoid doubling up.
	 */
	private static String derivedFloorLabel(String hint, int plane)
	{
		if (plane > 0)
		{
			if (hintSaysUpstairs(hint))
			{
				return null;
			}
			return plane == 1 ? "Upstairs" : "Upstairs (floor " + plane + ")";
		}
		return hintNamesFloor(hint) ? null : "Ground floor";
	}

	private static boolean hintSaysUpstairs(String hint)
	{
		if (hint == null)
		{
			return false;
		}
		String lower = hint.toLowerCase();
		return lower.contains("upstairs") || lower.contains("up the stairs");
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
