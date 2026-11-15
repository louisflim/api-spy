package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.learning.ConceptDecisionEntity;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.repository.ConceptDecisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persists per-concept decisions without overwriting first-attempt KPI records. */
@Service
public class ConceptDecisionRecorder {

	private final DecisionStore store;

	@Autowired
	public ConceptDecisionRecorder(ConceptDecisionRepository repository) {
		this.store = new JpaDecisionStore(repository);
	}

	private ConceptDecisionRecorder(DecisionStore store) {
		this.store = store;
	}

	public static ConceptDecisionRecorder inMemory() {
		return new ConceptDecisionRecorder(new InMemoryDecisionStore());
	}

	public void record(
			String participantCode,
			MissionDefinition mission,
			ApiContract contract,
			List<ConceptDecision> decisions,
			String submissionFingerprint,
			boolean firstEligible) {
		String rankCode = mission.getRank().name();
		String hash = sha256(submissionFingerprint);
		Instant now = Instant.now();
		for (ConceptDecision decision : decisions) {
			store.save(new ConceptDecisionEntity(
					UUID.randomUUID(),
					participantCode,
					contract.getMissionId(),
					rankCode,
					contract.getChallengeId(),
					contract.getDeviceId(),
					decision.conceptTag(),
					decision.applicable(),
					decision.correct(),
					String.join(",", decision.errorCodes()),
					hash,
					firstEligible,
					now));
		}
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			return Integer.toHexString(value.hashCode());
		}
	}

	interface DecisionStore {
		void save(ConceptDecisionEntity entity);
	}

	private static final class InMemoryDecisionStore implements DecisionStore {
		private final CopyOnWriteArrayList<ConceptDecisionEntity> rows = new CopyOnWriteArrayList<>();

		@Override
		public void save(ConceptDecisionEntity entity) {
			rows.add(entity);
		}
	}

	private static final class JpaDecisionStore implements DecisionStore {
		private final ConceptDecisionRepository repository;

		JpaDecisionStore(ConceptDecisionRepository repository) {
			this.repository = repository;
		}

		@Override
		public void save(ConceptDecisionEntity entity) {
			repository.save(entity);
		}
	}
}
