package edu.citu.apispy.service;

import java.util.Optional;

interface TerminalVerificationStore {

	Optional<EvidenceRow> find(
			String participantCode,
			String missionId,
			String challengeId,
			String conceptTag);

	EvidenceRow insertIfAbsent(EvidenceRow row);

	long countForTerminal(String participantCode, String missionId, String terminalId);

	record EvidenceRow(
			String participantCode,
			String missionId,
			String terminalId,
			String challengeId,
			String conceptTag) {
	}
}
