package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for immutable first-attempt records.
 * Implementations must never overwrite an existing participant/mission/device row.
 */
public interface FirstAttemptStore {

	record InsertResult(FirstAttemptRecord record, boolean inserted) {
	}

	Optional<FirstAttemptRecord> find(String participantCode, String missionId, String deviceId);

	/**
	 * Inserts {@code candidate} only when absent.
	 */
	InsertResult insertIfAbsent(FirstAttemptRecord candidate);

	List<FirstAttemptRecord> findAllForParticipant(String participantCode);

	/** Every participant's records (research reporting only), oldest first. */
	List<FirstAttemptRecord> findAll();

	void clearAll();
}
