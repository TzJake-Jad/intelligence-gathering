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

	/**
	 * Why the panel is empty even though a notice board was read successfully, or null when there is
	 * no such explanation. A meeting discarded by the tracking filters is otherwise indistinguishable
	 * from one that never parsed.
	 */
	String getFilterNotice();

	/** Share code for the current cycle, or null when there is no meeting to share. */
	String exportShareCode();

	/**
	 * Apply a pasted share code.
	 *
	 * @return null once the import is under way, or a message to show the user if the code was
	 *     rejected. Validation is synchronous so the panel can report the reason immediately; the
	 *     state change itself is handed to the client thread.
	 */
	String importShareCode(String code);
}
