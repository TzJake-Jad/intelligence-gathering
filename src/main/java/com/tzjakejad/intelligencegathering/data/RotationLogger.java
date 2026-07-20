package com.tzjakejad.intelligencegathering.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Appends observed board reads to {@code RUNELITE_DIR/intelligence-gathering/rotations.csv} (spec §5) so
 * the 30-minute rotation order can be reverse-engineered offline. One row per read:
 * {@code utcTimestamp,locationId}. Behind the {@code logRotations} config toggle in the caller.
 */
@Slf4j
@Singleton
public class RotationLogger
{
	private static final Path DIR = RuneLite.RUNELITE_DIR.toPath().resolve("intelligence-gathering");
	private static final Path CSV = DIR.resolve("rotations.csv");
	private static final String HEADER = "utcTimestamp,locationId" + System.lineSeparator();

	/** Append one row for a board read. Best-effort: I/O errors are logged, never thrown. */
	public void log(String locationId)
	{
		if (locationId == null)
		{
			return;
		}

		try
		{
			Files.createDirectories(DIR);
			boolean fresh = !Files.exists(CSV);
			StringBuilder sb = new StringBuilder();
			if (fresh)
			{
				sb.append(HEADER);
			}
			sb.append(Instant.now()).append(',').append(locationId).append(System.lineSeparator());
			Files.write(CSV, sb.toString().getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			log.warn("Failed to append organised crime rotation row", e);
		}
	}
}
