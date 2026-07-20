package com.tzjakejad.intelligencegathering;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher: boots the real RuneLite client with this plugin side-loaded so it can be
 * tested end-to-end. Run via {@code ./gradlew run} (or as a normal main method from the IDE).
 */
public class IntelligenceGatheringPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IntelligenceGatheringPlugin.class);
		RuneLite.main(args);
	}
}
