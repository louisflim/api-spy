package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/local fallback store. Production uses {@link JpaFirstAttemptStore}. */
public final class InMemoryFirstAttemptStore implements FirstAttemptStore {

	private final ConcurrentHashMap<String, FirstAttemptRecord> records = new ConcurrentHashMap<>();

	@Override
	public Optional<FirstAttemptRecord> find(String participantCode, String missionId, String deviceId) {
		return Optional.ofNullable(records.get(FirstAttemptRecord.key(participantCode, missionId, deviceId)));
	}

	@Override
	public InsertResult insertIfAbsent(FirstAttemptRecord candidate) {
		String key = FirstAttemptRecord.key(
				candidate.getParticipantCode(),
				candidate.getMissionId(),
				candidate.getDeviceId());
		FirstAttemptRecord existing = records.putIfAbsent(key, candidate);
		if (existing == null) {
			return new InsertResult(candidate, true);
		}
		return new InsertResult(existing, false);
	}

	@Override
	public List<FirstAttemptRecord> findAllForParticipant(String participantCode) {
		return records.values().stream()
				.filter(record -> record.getParticipantCode().equals(participantCode))
				.sorted((a, b) -> a.getSubmittedAt().compareTo(b.getSubmittedAt()))
				.toList();
	}

	@Override
	public List<FirstAttemptRecord> findAll() {
		return records.values().stream()
				.sorted((a, b) -> a.getSubmittedAt().compareTo(b.getSubmittedAt()))
				.toList();
	}

	@Override
	public void clearAll() {
		records.clear();
	}
}
