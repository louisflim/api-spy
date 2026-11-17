package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import edu.citu.apispy.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Captures the first eligible mandatory submission per player/mission/device.
 * Successful retries never overwrite an earlier incorrect first attempt.
 */
@Service
public class FirstAttemptService {

	private final FirstAttemptStore store;

	public FirstAttemptService(FirstAttemptStore store) {
		this.store = store;
	}

	/** Unit-test helper that keeps prior in-memory semantics without Spring. */
	public static FirstAttemptService inMemory() {
		return new FirstAttemptService(new InMemoryFirstAttemptStore());
	}

	public record CaptureResult(FirstAttemptRecord record, boolean newlyRecorded) {
	}

	public CaptureResult captureIfAbsent(
			String participantCode,
			ApiContract contract,
			ApiRequest request,
			ValidationResult validationResult) {
		FirstAttemptRecord created = buildRecord(participantCode, contract, request, validationResult);
		FirstAttemptStore.InsertResult inserted = store.insertIfAbsent(created);
		return new CaptureResult(inserted.record(), inserted.inserted());
	}

	/**
	 * Replay / non-RCA view: returns the immutable first-clear record when present,
	 * otherwise an ephemeral snapshot that is <strong>not</strong> persisted.
	 */
	public CaptureResult viewWithoutRecording(
			String participantCode,
			ApiContract contract,
			ApiRequest request,
			ValidationResult validationResult) {
		Optional<FirstAttemptRecord> existing = store.find(
				participantCode,
				contract.getMissionId(),
				contract.getDeviceId());
		if (existing.isPresent()) {
			return new CaptureResult(existing.get(), false);
		}
		return new CaptureResult(buildRecord(participantCode, contract, request, validationResult), false);
	}

	public Optional<FirstAttemptRecord> find(String participantCode, String missionId, String deviceId) {
		return store.find(participantCode, missionId, deviceId);
	}

	public List<FirstAttemptRecord> findAllForParticipant(String participantCode) {
		return store.findAllForParticipant(participantCode);
	}

	public void clearAll() {
		store.clearAll();
	}

	private static FirstAttemptRecord buildRecord(
			String participantCode,
			ApiContract contract,
			ApiRequest request,
			ValidationResult validationResult) {
		List<String> errorCodes = validationResult.getErrorCodes().stream()
				.map(Enum::name)
				.sorted()
				.collect(Collectors.toList());
		boolean correct = validationResult.isCorrect();
		Map<String, Boolean> perConcept = new LinkedHashMap<>();
		for (String conceptTag : contract.getConceptTags()) {
			perConcept.put(conceptTag, correctForConcept(conceptTag, validationResult, contract));
		}

		return new FirstAttemptRecord(
				participantCode,
				contract.getMissionId(),
				contract.getDeviceId(),
				contract.getChallengeId(),
				contract.getVariantId(),
				contract.getConceptTags(),
				request.getMethod(),
				request.getPath() + formatQuery(request.getQueryParameters()),
				request.getHeaders(),
				request.getBody(),
				errorCodes,
				correct,
				perConcept,
				Instant.now());
	}

	static boolean correctForConcept(
			String conceptTag,
			ValidationResult result,
			ApiContract contract) {
		if (result.isCorrect()) {
			return true;
		}
		return switch (conceptTag) {
			case "http.method.get",
					"http.method.post",
					"http.method.put",
					"http.method.patch",
					"http.method.delete" -> !result.getErrorCodes().contains(ErrorCode.METHOD);
			case "http.endpoint" -> !result.getErrorCodes().contains(ErrorCode.ENDPOINT);
			case "http.path_parameter" -> pathParameterConceptCorrect(result, contract);
			case "http.query_parameter" -> queryParameterConceptCorrect(result, contract);
			case "rest.fundamentals" -> !result.getErrorCodes().contains(ErrorCode.METHOD)
					&& !result.getErrorCodes().contains(ErrorCode.ENDPOINT);
			case "http.response" -> false;
			default -> false;
		};
	}

	private static boolean pathParameterConceptCorrect(ValidationResult result, ApiContract contract) {
		if (contract.getPathParameters().isEmpty()) {
			return !result.getErrorCodes().contains(ErrorCode.ENDPOINT);
		}
		if (result.getErrorCodes().contains(ErrorCode.ENDPOINT)) {
			return false;
		}
		return !result.pathParameterFailed();
	}

	private static boolean queryParameterConceptCorrect(ValidationResult result, ApiContract contract) {
		if (contract.getQueryParameters().isEmpty()) {
			return true;
		}
		return !result.queryParameterFailed();
	}

	private static String formatQuery(Map<String, String> queryParameters) {
		if (queryParameters == null || queryParameters.isEmpty()) {
			return "";
		}
		return "?" + queryParameters.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("&"));
	}
}
