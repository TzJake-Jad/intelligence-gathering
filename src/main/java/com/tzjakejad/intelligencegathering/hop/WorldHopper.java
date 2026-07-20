package com.tzjakejad.intelligencegathering.hop;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

/**
 * One-click hop (spec §3.7/§6). Resolves a world id against the {@link WorldService}, then hops
 * the way the built-in world hopper does: open the world switcher, wait a tick for its list
 * widget to load, and only then call {@link Client#hopToWorld} — the hop silently fails when the
 * switcher isn't open.
 */
@Slf4j
@Singleton
public class WorldHopper
{
	/** Ticks to wait for the world switcher before abandoning the hop (matches the core plugin). */
	private static final int MAX_SWITCHER_ATTEMPTS = 3;

	private final Client client;
	private final ClientThread clientThread;
	private final WorldService worldService;

	/** Hop target waiting on the world switcher to open; touched on the client thread only. */
	private net.runelite.api.World pendingHop;
	private int switcherAttempts;

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

		clientThread.invokeLater(() ->
		{
			final net.runelite.api.World rsWorld = client.createWorld();
			rsWorld.setActivity(world.getActivity());
			rsWorld.setAddress(world.getAddress());
			rsWorld.setId(world.getId());
			rsWorld.setPlayerCount(world.getPlayers());
			rsWorld.setLocation(world.getLocation());
			rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

			if (client.getGameState() == GameState.LOGIN_SCREEN)
			{
				client.changeWorld(rsWorld);
				return;
			}

			pendingHop = rsWorld;
			switcherAttempts = 0;
			client.openWorldHopper();
		});
	}

	/**
	 * Complete a pending hop once the world switcher list has loaded. Called every game tick by
	 * the plugin (on the client thread).
	 */
	public void processPendingHop()
	{
		if (pendingHop == null)
		{
			return;
		}

		// Same component the built-in world hopper polls for before it trusts hopToWorld.
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++switcherAttempts >= MAX_SWITCHER_ATTEMPTS)
			{
				log.warn("World switcher did not open; abandoning hop to {}", pendingHop.getId());
				pendingHop = null;
			}
		}
		else
		{
			client.hopToWorld(pendingHop);
			pendingHop = null;
		}
	}
}
