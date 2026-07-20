package com.tzjakejad.intelligencegathering.ui;

import com.tzjakejad.intelligencegathering.model.CurrentMeeting;
import com.tzjakejad.intelligencegathering.model.WorldStatus;
import java.util.List;

/**
 * Read/act surface the {@link IntelligenceGatheringPanel} uses, keeping the Swing panel decoupled from
 * the plugin's event wiring.
 */
public interface IntelligenceGatheringController
{
	CurrentMeeting getCurrentMeeting();

	/** Worlds visited this cycle, sorted for display. */
	List<WorldStatus> getWorldStatuses();

	int getCurrentWorld();

	boolean isSafeWorldsOnly();

	void hop(int world);
}
