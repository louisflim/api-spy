package edu.citu.apispy.controller;

import edu.citu.apispy.web.InputLimits;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestPayloadGuardTest {

	@Autowired
	private MockMvc mockMvc;

	private MockHttpSession session;

	@BeforeEach
	void setUp() throws Exception {
		String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
		MvcResult register = mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "guard%s",
								  "email": "guard%s@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		session = (MockHttpSession) register.getRequest().getSession();
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request payload."));
	}

	@Test
	void rejectsOversizedPayload() throws Exception {
		String huge = "x".repeat(InputLimits.MAX_REQUEST_BYTES + 8);
		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.co\",\"password\":\"" + huge + "\"}"))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("Request payload too large."));
	}

	@Test
	void rejectsOversizedSimulatePath() throws Exception {
		String longPath = "/" + "a".repeat(InputLimits.PATH);
		mockMvc.perform(post("/api/requests/simulate")
						.with(csrf())
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "participantCode": "p",
								  "missionId": "m1-recruit-l1",
								  "deviceId": "door-alpha",
								  "method": "GET",
								  "path": "%s",
								  "headers": {},
								  "body": ""
								}
								""".formatted(longPath)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid request payload."));
	}

	@Test
	void rejectsInvalidUsernameCharacters() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "bad name!",
								  "email": "badname@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid request payload."));
	}
}
