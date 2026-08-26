package com.tzjakejad.intelligencegathering.rewards;

import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig;
import com.tzjakejad.intelligencegathering.IntelligenceGatheringConfig.RewardSkill;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Misclick guard for the reward XP dialog: swallows clicks on skills the player has not
 * allowed, so a stray click on "Attack" can't ruin a defence pure.
 *
 * <p>The reward chooser is an ordinary chatbox option list ({@link InterfaceID.Chatmenu}), so the
 * guard has to be narrow or it would eat clicks in unrelated dialogues. Two conditions must both
 * hold before anything is touched: the option list carries the dialog's own title, and the clicked
 * option reads as {@code "<skill>: <xp>"}. Anything else is left completely alone.
 */
@Slf4j
@Singleton
public class XpRewardLock
{
	/** Title the reward chooser opens with; the only dialog this class acts on. */
	private static final String DIALOG_TITLE = "what kind of training will you pursue?";

	/** Prepended to locked option text so the dialog reads as disabled rather than dead. */
	private static final String STRIKETHROUGH = "<str>";

	private final Client client;
	private final IntelligenceGatheringConfig config;

	@Inject
	public XpRewardLock(Client client, IntelligenceGatheringConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Swallow the click when it lands on a locked skill. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.lockXpReward() || event.getParam1() != InterfaceID.Chatmenu.OPTIONS)
		{
			return;
		}

		Widget[] options = rewardOptions();
		if (options == null)
		{
			return;
		}

		int index = event.getParam0();
		if (index < 0 || index >= options.length)
		{
			return;
		}

		RewardSkill skill = optionSkill(options[index]);
		if (skill != null && !allowedSkills().contains(skill))
		{
			log.debug("Blocked reward XP click on {}", skill);
			event.consume();
		}
	}

	/**
	 * Strike through the locked options so the block is visible. Driven from the game tick, which
	 * is only a redraw delay — {@link #onMenuOptionClicked} guards the click on its own.
	 */
	public void markLockedOptions()
	{
		if (!config.lockXpReward())
		{
			return;
		}

		Widget[] options = rewardOptions();
		if (options == null)
		{
			return;
		}

		Set<RewardSkill> allowed = allowedSkills();
		for (Widget option : options)
		{
			RewardSkill skill = optionSkill(option);
			if (skill == null || allowed.contains(skill))
			{
				continue;
			}

			String text = option.getText();
			if (!text.startsWith(STRIKETHROUGH))
			{
				option.setText(STRIKETHROUGH + text);
			}
		}
	}

	/**
	 * Dynamic children of the chatbox option list, but only while it is showing the reward
	 * chooser. Null for every other dialog, which is what keeps the guard from overreaching.
	 */
	private Widget[] rewardOptions()
	{
		Widget list = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (list == null || list.isHidden())
		{
			return null;
		}

		Widget[] children = list.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return null;
		}

		for (Widget child : children)
		{
			if (clean(child.getText()).equals(DIALOG_TITLE))
			{
				return children;
			}
		}

		return null;
	}

	/**
	 * The skill an option awards, or null when the text isn't a {@code "<skill>: <xp>"} row naming
	 * one of the four dialog skills. The title, any cancel row, and anything unrecognised all land
	 * here and are left clickable — an option the allowlist can't express must never be blocked.
	 */
	private static RewardSkill optionSkill(Widget option)
	{
		String text = clean(option.getText());
		int colon = text.indexOf(':');
		if (colon <= 0)
		{
			return null;
		}

		String name = text.substring(0, colon).trim();
		for (RewardSkill skill : RewardSkill.values())
		{
			if (skill.name().toLowerCase(Locale.ROOT).equals(name))
			{
				return skill;
			}
		}

		return null;
	}

	/**
	 * Configured allowlist. An empty selection blocks all four: the point of the guard is
	 * protection, so the safe failure is refusing a click, not quietly permitting every one.
	 */
	private Set<RewardSkill> allowedSkills()
	{
		Set<RewardSkill> configured = config.allowedXpSkills();
		return configured == null ? EnumSet.noneOf(RewardSkill.class) : configured;
	}

	private static String clean(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim().toLowerCase(Locale.ROOT);
	}
}
