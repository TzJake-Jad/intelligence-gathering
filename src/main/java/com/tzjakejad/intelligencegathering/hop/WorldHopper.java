package com.tzjakejad.intelligencegathering.hop;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

/**
 * Cleaned-up one-click hop carried over from the original plugin (spec §3.7/§6). Resolves a
 * world id against the {@link WorldService} and hops on the client thread.
 */
@Slf4j
@Singleton
public class WorldHopper
{
	private final Client client;
	private final ClientThread clientThread;
	private final WorldService worldService;

	@Inject
	public WorldHopper(Client client, ClientThread clientThread, WorldService worldService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.worldService = worldService;
	}

	/** Hop to the given world id. No-op if the world is unknown. */
	public void hop(int worldId)
	{
		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			log.warn("World list unavailable; cannot hop to {}", worldId);
			return;
		}

		World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			log.warn("Unknown world {}", worldId);
			return;
		}

		final net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		clientThread.invokeLater(() -> client.hopToWorld(rsWorld));
	}
}
