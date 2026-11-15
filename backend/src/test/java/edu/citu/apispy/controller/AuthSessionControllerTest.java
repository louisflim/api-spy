package edu.citu.apispy.controller;

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

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthRemainsPublic() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void meRejectsUnauthenticated() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void registerLoginMeLogoutFlowAndRejectsWeakPassword() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "weakagent",
								  "email": "weak@example.com",
								  "password": "short",
								  "confirmPassword": "short"
								}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "rookie1",
								  "email": "rookie1@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("rookie1"))
				.andExpect(jsonPath("$.email").value("rookie1@example.com"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(post("/api/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "username": "rookie1",
								  "email": "other@example.com",
								  "password": "Password1!",
								  "confirmPassword": "Password1!"
								}
								"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "rookie1@example.com",
								  "password": "WrongPass1!"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Invalid")));

		MvcResult login = mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "rookie1@example.com",
								  "password": "Password1!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("rookie1"))
				.andReturn();

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

		mockMvc.perform(get("/api/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("rookie1@example.com"));

		mockMvc.perform(post("/api/auth/logout")
						.with(csrf())
						.session(session))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetMarkedUnavailableWithoutMail() throws Exception {
		mockMvc.perform(get("/api/auth/password-reset/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false));

		mockMvc.perform(post("/api/auth/password-reset/request")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "anyone@example.com"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("unavailable")));
	}

	@Test
	void stateChangingRequestWithoutCsrfIsForbidden() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "rookie1@example.com",
								  "password": "Password1!"
								}
								"""))
				.andExpect(status().isForbidden());
	}
}
