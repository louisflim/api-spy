package edu.citu.apispy.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Graded mission APIs must not disclose private validation answers.
 * Ungraded practice doors may still include guided method/path examples.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GradedAnswerKeyLeakageTest {

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
								  "username": "leak%s",
								  "email": "leak%s@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		session = (MockHttpSession) register.getRequest().getSession();
	}

	@Test
	void missionDetailOmitsGradedAnswerKeys() throws Exception {
		mockMvc.perform(get("/api/missions/1/RECRUIT/1").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challenge.challengeId").exists())
				.andExpect(jsonPath("$.challenge.documentation").exists())
				.andExpect(jsonPath("$.challenge.expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.challenge.pathTemplate").doesNotExist())
				.andExpect(jsonPath("$.challenge.pathParameters").doesNotExist())
				.andExpect(jsonPath("$.challenge.queryParameters").doesNotExist())
				.andExpect(jsonPath("$.conceptVariants[0].expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.conceptVariants[0].pathTemplate").doesNotExist());
	}

	@Test
	void missionProgressOmitsGradedAnswerKeys() throws Exception {
		mockMvc.perform(get("/api/missions/m1-recruit-l1/progress").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomGeneration").exists())
				.andExpect(jsonPath("$.mandatoryDevices[0].deviceId").exists())
				.andExpect(jsonPath("$.mandatoryDevices[0].documentation").exists())
				.andExpect(jsonPath("$.mandatoryDevices[0].expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.mandatoryDevices[0].pathTemplate").doesNotExist());
	}

	@Test
	void terminalVerificationSnapshotStartsAtZeroWithoutAnswerKeys() throws Exception {
		mockMvc.perform(get("/api/missions/m1-recruit-l1/terminal-verification").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.percent").value(0))
				.andExpect(jsonPath("$.earnedSlots").value(0))
				.andExpect(jsonPath("$.requiredSlots").exists())
				.andExpect(jsonPath("$.terminalId").exists())
				.andExpect(jsonPath("$.complete").value(false))
				.andExpect(jsonPath("$.verifyAnotherScenario.expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.verifyAnotherScenario.pathTemplate").doesNotExist());
	}

	@Test
	void simulateProgressPayloadOmitsGradedAnswerKeys() throws Exception {
		String body = """
				{
				  "participantCode": "player",
				  "missionId": "m1-recruit-l1",
				  "deviceId": "door-alpha",
				  "challengeId": "chal-m1-get-door-v1",
				  "method": "GET",
				  "path": "/api/doors/alpha",
				  "headers": {},
				  "body": "",
				  "mode": "MISSION"
				}
				""";
		mockMvc.perform(post("/api/requests/simulate")
						.with(csrf())
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.missionProgress.mandatoryDevices[0].expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.missionProgress.mandatoryDevices[0].pathTemplate").doesNotExist())
				.andExpect(jsonPath("$.terminalVerification.verifyAnotherScenario.expectedMethod").doesNotExist())
				.andExpect(jsonPath("$.terminalVerification.verifyAnotherScenario.pathTemplate").doesNotExist());
	}
}
