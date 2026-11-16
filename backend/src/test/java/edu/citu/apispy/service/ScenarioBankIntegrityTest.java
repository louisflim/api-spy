package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.content.MissionEscalationDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioBankIntegrityTest {

	private ContractCatalogService contracts;
	private DeviceEscalationService escalation;
	private ScenarioSelectionService selection;

	@BeforeEach
	void setUp() {
		ContentLoader loader = new ContentLoader(new ObjectMapper());
		contracts = new ContractCatalogService(loader);
		escalation = new DeviceEscalationService(loader, contracts);
		selection = new ScenarioSelectionService(escalation, contracts);
	}

	@Test
	void challengeIdsAreUniqueAndContractsAreSolvable() {
		Set<String> seen = new HashSet<>();
		for (ApiContract contract : contracts.allContracts()) {
			assertTrue(seen.add(contract.getChallengeId()), "Duplicate challengeId: " + contract.getChallengeId());
			assertFalse(contract.getExpectedMethod().isBlank());
			assertFalse(contract.getPathTemplate().isBlank());
			assertFalse(contract.getDocumentation().isBlank());
			assertFalse(contract.getConceptTags().isEmpty());
			if (contract.getMissionId().startsWith("m1-recruit-")
					&& !contract.getDeviceId().startsWith("final-lock-")) {
				String pasted = contract.getExpectedMethod() + " " + contract.getPathTemplate();
				assertFalse(
						contract.getDocumentation().contains(pasted),
						"Graded brief must not paste complete method+path answer for " + contract.getChallengeId());
			}
		}
	}

	@Test
	void moduleOneConceptCoverageMeetsDevelopmentTargets() {
		assertTrue(countConcept("http.method.get") >= 5);
		assertTrue(countConcept("http.endpoint") >= 5);
		assertTrue(countConcept("http.path_parameter") >= 4);
		assertTrue(countConcept("http.query_parameter") >= 4);
		assertTrue(countConcept("http.method.post") >= 5);
		assertTrue(countConcept("http.method.put") >= 5);
		assertTrue(countConcept("http.method.delete") >= 5);
	}

	@Test
	void escalationPoolsReferenceExistingChallenges() {
		for (String missionId : List.of(
				"m1-recruit-l1",
				"m1-recruit-l2",
				"m1-recruit-l3",
				"m1-recruit-l4",
				"m1-recruit-l5",
				"m1-recruit-l6",
				"m1-recruit-l7")) {
			MissionEscalationDefinition definition = escalation.find(missionId).orElseThrow();
			for (String id : definition.mandatoryChallengeIds()) {
				assertTrue(contracts.findByChallengeId(id).isPresent(), "Missing mandatory " + id);
			}
			if (definition.mandatoryScenarioPools() != null) {
				for (List<String> pool : definition.mandatoryScenarioPools()) {
					assertFalse(pool.isEmpty());
					for (String id : pool) {
						assertTrue(contracts.findByChallengeId(id).isPresent(), "Missing pool challenge " + id);
					}
				}
			}
			if (definition.verificationScenarioPool() != null) {
				for (String id : definition.verificationScenarioPool()) {
					assertTrue(contracts.findByChallengeId(id).isPresent(), "Missing verification " + id);
				}
			}
			assertTrue(contracts.findByChallengeId(definition.finalLockChallengeId()).isPresent());
		}
	}

	@Test
	void selectionIsStableForSameRoomGenerationAndVariesAcrossGenerations() {
		UUID generation = UUID.fromString("11111111-1111-1111-1111-111111111111");
		List<String> first = selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, generation, List.of());
		List<String> again = selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, generation, List.of());
		assertEquals(first, again, "refresh must preserve room-generation selection");
		assertFalse(first.isEmpty());
		assertTrue(contracts.findByChallengeId(first.getFirst()).isPresent());

		UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
		List<String> otherGen = selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, other, List.of());
		assertFalse(otherGen.isEmpty());
		assertTrue(contracts.findByChallengeId(otherGen.getFirst()).isPresent());

		// Across enough seeds, pool selection should not collapse to a single fixed id.
		Set<String> distinctPrimaries = new HashSet<>();
		for (int i = 0; i < 24; i++) {
			UUID seed = UUID.nameUUIDFromBytes(("seed-" + i).getBytes());
			distinctPrimaries.add(
					selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, seed, List.of()).getFirst());
		}
		assertTrue(distinctPrimaries.size() >= 2, "controlled pool selection must vary by roomGeneration");

		List<String> rotated = selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, other, first);
		assertFalse(rotated.isEmpty());
		assertTrue(contracts.findByChallengeId(rotated.getFirst()).isPresent());
	}

	@Test
	void verificationPlanSeedsDistinctSecondaryWhenPoolAllows() {
		UUID generation = UUID.fromString("33333333-3333-3333-3333-333333333333");
		List<String> selected = selection.selectMandatoryChallengeIds("m1-recruit-l1", 0, generation, List.of());
		List<ApiContract> plan = selection.verificationPlan("m1-recruit-l1", selected, generation);
		assertTrue(plan.size() >= 2);
		assertEquals(selected.getFirst(), plan.getFirst().getChallengeId());
		assertFalse(plan.get(0).getChallengeId().equals(plan.get(1).getChallengeId()));
	}

	@Test
	void meaningfulResourceFamiliesExistBeyondLabelSubstitution() {
		assertTrue(contracts.findByChallengeId("chal-m1-get-camera-v1").isPresent());
		assertTrue(contracts.findByChallengeId("chal-m1-path-panel-v1").isPresent());
		assertTrue(contracts.findByChallengeId("chal-m1-query-badge-v1").isPresent());
		assertTrue(contracts.findByChallengeId("chal-m1-post-intake-v1").isPresent());
		assertTrue(contracts.findByChallengeId("chal-m1-put-profile-v1").isPresent());
		assertTrue(contracts.findByChallengeId("chal-m1-delete-session-v1").isPresent());

		ApiContract camera = contracts.requireByChallengeId("chal-m1-get-camera-v1");
		ApiContract door = contracts.requireByChallengeId("chal-m1-get-door-v1");
		assertFalse(camera.getPathTemplate().equals(door.getPathTemplate()));
		assertFalse(camera.getDocumentation().equals(door.getDocumentation()));
	}

	private long countConcept(String tag) {
		return contracts.allContracts().stream()
				.filter(c -> c.getMissionId().startsWith("m1-recruit-"))
				.filter(c -> !c.getDeviceId().startsWith("final-lock-"))
				.filter(c -> c.getConceptTags().contains(tag))
				.count();
	}
}
