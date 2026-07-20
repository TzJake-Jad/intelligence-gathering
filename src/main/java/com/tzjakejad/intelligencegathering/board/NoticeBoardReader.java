package com.tzjakejad.intelligencegathering.board;

import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.OcLocations;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Robust notice-board parser (spec §3.3). Takes the raw text pulled from the board widget and
 * resolves it to an {@link OcLocation} plus a countdown, without relying on brittle child
 * indices or an exact {@code .equals()} on an English sentinel string.
 *
 * <p>Matching normalises whitespace and smart quotes on both sides before comparing, then falls
 * back to a best-substring match with a logged warning so a changed Jagex string degrades to a
 * warning instead of throwing.
 */
@Slf4j
@Singleton
public class NoticeBoardReader
{
	// "... in 12 minutes", "... in 1 minute". Also matches "now"/"imminently" separately.
	private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*minute", Pattern.CASE_INSENSITIVE);
	private static final Pattern IMMINENT = Pattern.compile("\\b(now|imminent|imminently|any moment)\\b",
		Pattern.CASE_INSENSITIVE);

	private final OcLocations ocLocations;

	/** Normalised board message -> location, built once from the seed set. */
	private final Map<String, OcLocation> byNormalisedMessage;

	@Inject
	public NoticeBoardReader(OcLocations ocLocations)
	{
		this.ocLocations = ocLocations;
		this.byNormalisedMessage = ocLocations.getLocations().stream()
			.collect(Collectors.toMap(
				loc -> normalise(loc.getBoardMessage()),
				Function.identity(),
				(a, b) -> a));
	}

	/**
	 * Parse the collected board text. Returns {@code null} when no location can be resolved at
	 * all (caller should treat as "no meeting"); otherwise a result whose {@code exactMatch}
	 * flag is false when only a fuzzy match was possible.
	 */
	public BoardReadResult read(List<String> boardLines)
	{
		if (boardLines == null || boardLines.isEmpty())
		{
			return null;
		}

		String joined = normalise(String.join(" ", boardLines));

		OcLocation match = byNormalisedMessage.get(joined);
		boolean exact = match != null;

		if (match == null)
		{
			// Substring pass: the location line may be embedded amongst other board text.
			for (Map.Entry<String, OcLocation> e : byNormalisedMessage.entrySet())
			{
				if (joined.contains(e.getKey()))
				{
					match = e.getValue();
					exact = true;
					break;
				}
			}
		}

		if (match == null)
		{
			match = fuzzyMatch(joined);
			if (match != null)
			{
				log.warn("Notice board text did not match any known message exactly; "
					+ "best-guess='{}'. A board string may have changed. Text was: {}",
					match.getId(), joined);
			}
		}

		if (match == null)
		{
			log.warn("Could not resolve notice board text to any location: {}", joined);
			return null;
		}

		return new BoardReadResult(match, exact, parseMinutes(joined), isImminent(joined));
	}

	/** Token-overlap fallback against the known messages. */
	private OcLocation fuzzyMatch(String normalisedText)
	{
		String[] textTokens = normalisedText.split(" ");
		OcLocation best = null;
		double bestScore = 0.0;

		for (OcLocation loc : ocLocations.getLocations())
		{
			String[] msgTokens = normalise(loc.getBoardMessage()).split(" ");
			int hits = 0;
			for (String mt : msgTokens)
			{
				for (String tt : textTokens)
				{
					if (mt.equals(tt))
					{
						hits++;
						break;
					}
				}
			}
			double score = (double) hits / msgTokens.length;
			if (score > bestScore)
			{
				bestScore = score;
				best = loc;
			}
		}

		// Require a clear majority of tokens to overlap before trusting a fuzzy hit.
		return bestScore >= 0.6 ? best : null;
	}

	private static Integer parseMinutes(String normalisedText)
	{
		Matcher m = MINUTES.matcher(normalisedText);
		if (m.find())
		{
			try
			{
				return Integer.parseInt(m.group(1));
			}
			catch (NumberFormatException ignored)
			{
				// fall through
			}
		}
		return null;
	}

	private static boolean isImminent(String normalisedText)
	{
		return IMMINENT.matcher(normalisedText).find();
	}

	/** Collapse whitespace, unify smart quotes/apostrophes, lowercase for comparison. */
	static String normalise(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s
			.replace('’', '\'')   // right single quote
			.replace('‘', '\'')   // left single quote
			.replace('“', '"')    // left double quote
			.replace('”', '"')    // right double quote
			.replace(' ', ' ')    // non-breaking space
			.replaceAll("\\s+", " ")
			.trim()
			.toLowerCase();
	}

	/** Outcome of a board read. */
	@Value
	public static class BoardReadResult
	{
		OcLocation location;

		/** True for an exact/substring match, false when only a fuzzy match was possible. */
		boolean exactMatch;

		/** Minutes until appearance, or null if not present on the board. */
		Integer minutesUntil;

		/** True when the board says the meeting is now/imminent. */
		boolean imminent;
	}
}
