package edu.citu.apispy.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryTerminalVerificationStore implements TerminalVerificationStore {

	private final ConcurrentHashMap<String, EvidenceRow> rows = new ConcurrentHashMap<>();

	@Override
	public Optional<EvidenceRow> find(
			String participantCode,
			String missionId,
			String challengeId,
			String conceptTag) {
		return Optional.ofNullable(rows.get(key(participantCode, missionId, challengeId, conceptTag)));
	}

	@Override
	public EvidenceRow insertIfAbsent(EvidenceRow row) {
		String key = key(row.participantCode(), row.missionId(), row.challengeId(), row.conceptTag());
		EvidenceRow existing = rows.putIfAbsent(key, row);
		return existing == null ? row : existing;
	}

	@Override
	public long countForTerminal(String participantCode, String missionId, String terminalId) {
		return rows.values().stream()
				.filter(row -> row.participantCode().equals(participantCode)
						&& row.missionId().equals(missionId)
						&& row.terminalId().equals(terminalId))
				.count();
	}

	private static String key(String participantCode, String missionId, String challengeId, String conceptTag) {
		return participantCode + "|" + missionId + "|" + challengeId + "|" + conceptTag;
	}
}
