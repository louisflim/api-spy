package edu.citu.apispy.service;

import edu.citu.apispy.config.AdaptiveLearningProperties;
import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.validation.MethodValidator;
import edu.citu.apispy.validation.PathParameterValidator;
import edu.citu.apispy.validation.PathValidator;
import edu.citu.apispy.validation.QueryParameterValidator;
import edu.citu.apispy.validation.SimulatedRequestValidator;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

final class SimulationTestFixtures {

	record Bundle(
			RequestSimulationService service,
			FirstAttemptService firstAttempts,
			ContractCatalogService contracts,
			MissionCatalogService missions,
			TerminalVerificationService terminalVerification,
			AdaptivePracticeService adaptivePractice,
			ConceptReviewService conceptReview,
			MissionProgressService missionProgress,
			DeviceEscalationService escalation) {
	}

	static Bundle create() {
		ObjectMapper objectMapper = new ObjectMapper();
		ContentLoader contentLoader = new ContentLoader(objectMapper);
		ContractCatalogService contracts = new ContractCatalogService(contentLoader);
		MissionCatalogService missions = new MissionCatalogService(contentLoader);
		DeviceEscalationService escalation = new DeviceEscalationService(contentLoader, contracts);
		FirstAttemptService firstAttempts = FirstAttemptService.inMemory();
		ConceptProgressService progress = ConceptProgressService.inMemory();
		ConceptDecisionAnalyzer analyzer = new ConceptDecisionAnalyzer();
		ConceptDecisionRecorder recorder = ConceptDecisionRecorder.inMemory();
		TerminalVerificationService tvp = TerminalVerificationService.inMemory(contracts, escalation);
		ConceptReviewService reviews = ConceptReviewService.inMemory(contracts);
		AdaptiveLearningProperties properties = new AdaptiveLearningProperties();
		AdaptivePracticeService adaptive = AdaptivePracticeService.inMemory(
				properties,
				firstAttempts,
				missions,
				contracts,
				reviews);
		ScenarioSelectionService scenarioSelection = new ScenarioSelectionService(escalation, contracts);
		MissionProgressService missionProgress = MissionProgressService.inMemory(
				missions,
				escalation,
				adaptive,
				scenarioSelection,
				contracts,
				tvp,
				objectMapper);
		RequestSimulationService service = new RequestSimulationService(
				contracts,
				missions,
				new SimulatedRequestValidator(List.of(
						new MethodValidator(),
						new PathValidator(),
						new PathParameterValidator(),
						new QueryParameterValidator())),
				firstAttempts,
				progress,
				analyzer,
				recorder,
				tvp,
				new AgentFeedbackService(),
				adaptive,
				reviews,
				missionProgress,
				escalation,
				scenarioSelection);
		return new Bundle(
				service,
				firstAttempts,
				contracts,
				missions,
				tvp,
				adaptive,
				reviews,
				missionProgress,
				escalation);
	}

	private SimulationTestFixtures() {
	}
}
