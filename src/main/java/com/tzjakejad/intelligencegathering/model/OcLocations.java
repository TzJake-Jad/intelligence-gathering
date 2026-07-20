package com.tzjakejad.intelligencegathering.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the 34 meeting locations from {@code oc-location-anchors.json} on the classpath
 * (spec §5). Replaces the original hardcoded 316-line locations class with data.
 */
@Slf4j
@Singleton
public class OcLocations
{
	private static final String RESOURCE = "/com/tzjakejad/intelligencegathering/oc-location-anchors.json";

	@Getter
	private final List<OcLocation> locations;

	private final Map<String, OcLocation> byId;

	@Inject
	public OcLocations(Gson gson)
	{
		this.locations = load(gson);
		this.byId = locations.stream()
			.collect(Collectors.toMap(OcLocation::getId, Function.identity()));
		log.debug("Loaded {} organised crime locations", locations.size());
	}

	private static List<OcLocation> load(Gson gson)
	{
		try (InputStream in = OcLocations.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Seed file {} not found on classpath", RESOURCE);
				return Collections.emptyList();
			}

			Type type = new TypeToken<List<OcLocation>>()
			{
			}.getType();

			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				List<OcLocation> parsed = gson.fromJson(reader, type);
				return parsed == null ? Collections.emptyList() : Collections.unmodifiableList(parsed);
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to read organised crime seed file", e);
			return Collections.emptyList();
		}
	}

	public OcLocation byId(String id)
	{
		return byId.get(id);
	}
}
