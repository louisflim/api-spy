package edu.citu.apispy.controller;

import edu.citu.apispy.service.ContractCatalogService;
import edu.citu.apispy.service.FirstAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulateRequestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FirstAttemptService firstAttemptService;

	private MockHttpSession session;

	@BeforeEach
	void setUp() throws Exception {
		firstAttemptService.clearAll();
		String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
		MvcResult register = mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "sim%s",
								  "email": "sim%s@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		session = (MockHttpSession) register.getRequest().getSession();
	}

	@Test
	void healthEndpointReturnsUp() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void catalogReportsSixtyThreeMissions() throws Exception {
		mockMvc.perform(get("/api/catalog").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalMissions").value(63))
				.andExpect(jsonPath("$.implementedMissions").value(7))
				.andExpect(jsonPath("$.missions[?(@.missionId=='m1-recruit-l7')].playable").value(true));
	}

	@Test
	void lessonsAreUngraded() throws Exception {
		mockMvc.perform(get("/api/lessons").param("module", "1").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].graded").value(false))
				.andExpect(jsonPath("$[0].workedExample.path").value(containsString("/api/training/")));
	}

	@Test
	void simulateAcceptsCorrectDoorRequest() throws Exception {
		String body = """
				{
				  "participantCode": "ignored-browser-value",
				  "missionId": "m1-recruit-l1",
				  "deviceId": "%s",
				  "challengeId": "%s",
				  "method": "GET",
				  "path": "/api/doors/alpha",
				  "headers": {},
				  "body": ""
				}
				""".formatted(ContractCatalogService.DAY1_DOOR_DEVICE_ID, ContractCatalogService.DAY1_DOOR_CONTRACT_ID);

		mockMvc.perform(post("/api/requests/simulate")
						.with(csrf())
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(true))
				.andExpect(jsonPath("$.simulatedResponse.status").value(200))
				.andExpect(jsonPath("$.deviceChanges[0].unlocked").value(true))
				.andExpect(jsonPath("$.heartsChanged").value(false))
				.andExpect(jsonPath("$.firstAttemptRecorded").value(true));
	}

	@Test
	void simulateRejectsIncorrectDoorRequestWithoutHeartDamage() throws Exception {
		String body = """
				{
				  "participantCode": "ignored-browser-value",
				  "missionId": "m1-recruit-l1",
				  "deviceId": "%s",
				  "challengeId": "%s",
				  "method": "DELETE",
				  "path": "/api/doors/wrong",
				  "headers": {},
				  "body": ""
				}
				""".formatted(ContractCatalogService.DAY1_DOOR_DEVICE_ID, ContractCatalogService.DAY1_DOOR_CONTRACT_ID);

		mockMvc.perform(post("/api/requests/simulate")
						.with(csrf())
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false))
				.andExpect(jsonPath("$.simulatedResponse.status").value(400))
				.andExpect(jsonPath("$.errorCodes", hasItem("METHOD")))
				.andExpect(jsonPath("$.errorCodes", hasItem("ENDPOINT")))
				.andExpect(jsonPath("$.deviceChanges").isEmpty())
				.andExpect(jsonPath("$.heartsChanged").value(false));
	}

	@Test
	void simulateRejectsMissingSession() throws Exception {
		mockMvc.perform(post("/api/requests/simulate")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "participantCode": "x",
								  "missionId": "m1-recruit-l1",
								  "deviceId": "door-alpha",
								  "method": "GET",
								  "path": "/api/doors/alpha",
								  "headers": {},
								  "body": ""
								}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void plannedMissionIsNotPlayable() throws Exception {
		mockMvc.perform(get("/api/missions/1/SPECIAL_AGENT/7").session(session))
				.andExpect(status().isBadRequest());
	}
}
