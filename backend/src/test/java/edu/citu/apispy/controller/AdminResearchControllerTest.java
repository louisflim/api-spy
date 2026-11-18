package edu.citu.apispy.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SRS UC-07: only accounts with the Administrator role reach the research console.
 * The test profile lists researcher@example.test in apispy.admin.emails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminResearchControllerTest {

	private static final String PASSWORD = "Password1!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void signedOutVisitorsGet401AndPlayersGet403() throws Exception {
		mockMvc.perform(get("/api/admin/reports/kpi")).andExpect(status().isUnauthorized());

		MockHttpSession player = sessionFor("plainplayer", "plainplayer@example.test");
		mockMvc.perform(get("/api/me").session(player))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("PLAYER"));
		mockMvc.perform(get("/api/admin/reports/kpi").session(player)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/participants").session(player)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/export/events.csv").session(player)).andExpect(status().isForbidden());
	}

	@Test
	void configuredResearcherIsAdministratorAndSeesGameplayInReports() throws Exception {
		MockHttpSession admin = sessionFor("researcher", "researcher@example.test");
		mockMvc.perform(get("/api/me").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMINISTRATOR"));

		// A player enters a room: the attempt is logged and counted (SCR denominator).
		MockHttpSession player = sessionFor("kpiplayer", "kpiplayer@example.test");
		String progress = mockMvc.perform(get("/api/missions/m1-recruit-l1/progress").session(player))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String generation = JsonPath.read(progress, "$.roomGeneration");
		mockMvc.perform(post("/api/missions/m1-recruit-l1/room-entered")
						.with(csrf())
						.session(player)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomGeneration\":\"" + generation + "\"}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/admin/reports/kpi").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasData").value(true))
				.andExpect(jsonPath("$.modules[0].ranks.RECRUIT.SCR.denominator", greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.modules[0].ranks.RECRUIT.summary.attempts", greaterThanOrEqualTo(1)));

		mockMvc.perform(get("/api/admin/participants").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].participant", containsString("P-")));

		mockMvc.perform(get("/api/admin/export/events.csv").session(admin))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(content().string(containsString("ROOM_ENTERED")))
				// Pseudonymous codes only: never the account id.
				.andExpect(content().string(not(containsString(playerId(player)))));
	}

	@Test
	void researchSignalsAreIgnoredForStaleRooms() throws Exception {
		MockHttpSession player = sessionFor("staleplayer", "staleplayer@example.test");
		mockMvc.perform(get("/api/missions/m1-recruit-l1/progress").session(player)).andExpect(status().isOk());
		mockMvc.perform(post("/api/missions/m1-recruit-l1/terminal-opened")
						.with(csrf())
						.session(player)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomGeneration\":\"00000000-0000-0000-0000-000000000000\",\"deviceId\":\"door-alpha\"}"))
				.andExpect(status().isNoContent());
	}

	private String playerId(MockHttpSession session) throws Exception {
		String me = mockMvc.perform(get("/api/me").session(session)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(me, "$.userId");
	}

	/** Registers the account if needed, then signs in and returns the session. */
	private MockHttpSession sessionFor(String username, String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\""
						+ PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"));
		return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession();
	}
}
