package com.tzjakejad.intelligencegathering.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tzjakejad.intelligencegathering.model.BossGender;
import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.OcLocation;
import com.tzjakejad.intelligencegathering.model.OcLocations;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Encodes the current meeting and world scouting into a short text code that can be pasted into
 * Discord or a clan chat and imported by someone else's client.
 *
 * <p>Wire format is {@code OCI1-} followed by base64url of gzipped JSON. The prefix names the
 * payload and carries the version, so a future format can be recognised and rejected cleanly rather
 * than parsed into nonsense. Base64url is used because {@code +} and {@code /} do not always survive
 * a round trip through chat clients and URLs; gzip is what keeps a full cycle's worth of worlds
 * inside a single pasteable line.
 *
 * <p>Timestamps are absolute epoch milliseconds and are deliberately <em>not</em> rebased onto the
 * importer's clock. A code can sit on a clipboard for minutes, so elapsed time and clock skew are
 * indistinguishable here — and the plugin already depends on the local clock being roughly right,
 * since that is what its own board reads are stamped with.
 */
@Slf4j
@Singleton
public class ShareCode
{
	private static final String PREFIX = "OCI1-";

	/** Chat clients wrap long lines, and a paste often picks up surrounding whitespace. */
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private final Gson gson;
	private final OcLocations locations;

	@Inject
	public ShareCode(Gson gson, OcLocations locations)
	{
		this.gson = gson;
		this.locations = locations;
	}

	/** Encode a meeting and its world list. Never returns null. */
	public String encode(CurrentMeeting meeting, Collection<WorldStatus> worlds)
	{
		Payload payload = new Payload();
		payload.locationId = meeting.getLocation().getId();
		payload.scheduledAppearanceMs = meeting.getScheduledAppearanceMs();
		payload.readAtMs = meeting.getReadAtMs();
		payload.worlds = new ArrayList<>();
		for (WorldStatus w : worlds)
		{
			PayloadWorld pw = new PayloadWorld();
			pw.world = w.getWorld();
			pw.bossGender = w.getBossGender();
			pw.lastClearedMs = w.getLastClearedMs();
			payload.worlds.add(pw);
		}

		byte[] json = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(gzip(json));
	}

	/**
	 * Decode a pasted code.
	 *
	 * @throws InvalidCodeException with a message meant to be shown to the user
	 */
	public Decoded decode(String text) throws InvalidCodeException
	{
		if (text == null)
		{
			throw new InvalidCodeException("No code to import.");
		}

		String code = WHITESPACE.matcher(text).replaceAll("");
		if (code.isEmpty())
		{
			throw new InvalidCodeException("No code to import.");
		}

		if (!code.startsWith(PREFIX))
		{
			throw new InvalidCodeException(code.startsWith("OCI")
				? "That code is from a newer version of the plugin."
				: "That doesn't look like an Intelligence Gathering code.");
		}

		Payload payload;
		try
		{
			byte[] json = gunzip(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())));
			payload = gson.fromJson(new String(json, StandardCharsets.UTF_8), Payload.class);
		}
		catch (IllegalArgumentException | IOException | JsonSyntaxException e)
		{
			log.debug("Unreadable share code", e);
			throw new InvalidCodeException("That code is damaged — copy it again.");
		}

		if (payload == null || payload.locationId == null)
		{
			throw new InvalidCodeException("That code is damaged — copy it again.");
		}

		OcLocation location = locations.byId(payload.locationId);
		if (location == null)
		{
			throw new InvalidCodeException("That code names a meeting spot this plugin doesn't know.");
		}

		CurrentMeeting meeting = new CurrentMeeting(location, payload.scheduledAppearanceMs, payload.readAtMs);
		if (meeting.rotationEndMs() <= System.currentTimeMillis())
		{
			throw new InvalidCodeException("That code has expired — its meeting has already rotated out.");
		}

		List<WorldStatus> worlds = new ArrayList<>();
		if (payload.worlds != null)
		{
			for (PayloadWorld pw : payload.worlds)
			{
				if (pw == null || pw.world <= 0)
				{
					continue;
				}
				WorldStatus ws = new WorldStatus(pw.world);
				ws.setBossGender(pw.bossGender == null ? BossGender.UNKNOWN : pw.bossGender);
				ws.setLastClearedMs(pw.lastClearedMs);
				worlds.add(ws);
			}
		}

		return new Decoded(meeting, Collections.unmodifiableList(worlds));
	}

	private static byte[] gzip(byte[] raw)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			gz.write(raw);
		}
		catch (IOException e)
		{
			// Compressing a byte array in memory has no failure mode worth a checked exception.
			throw new IllegalStateException("Failed to compress share code", e);
		}
		return out.toByteArray();
	}

	private static byte[] gunzip(byte[] compressed) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed)))
		{
			byte[] buf = new byte[4096];
			int read;
			while ((read = gz.read(buf)) != -1)
			{
				out.write(buf, 0, read);
			}
		}
		return out.toByteArray();
	}

	@Value
	public static class Decoded
	{
		CurrentMeeting meeting;
		List<WorldStatus> worlds;
	}

	/** Carries a message written for the user, not for a log. */
	public static class InvalidCodeException extends Exception
	{
		public InvalidCodeException(String message)
		{
			super(message);
		}
	}

	private static class Payload
	{
		String locationId;
		long scheduledAppearanceMs;
		long readAtMs;
		List<PayloadWorld> worlds;
	}

	private static class PayloadWorld
	{
		int world;
		BossGender bossGender;
		long lastClearedMs;
	}
}
