package com.tlavu.moodly.modules.habits.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.support.MongoTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class HabitApiIntegrationTest {
	private static final String USER_ID = "__test_phase1_api_user__";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private HabitRepository habitRepository;

	@AfterEach
	void cleanUp() {
		habitRepository.deleteAll();
	}

	@Test
	void returnsStandardValidationErrorForInvalidRequest() throws Exception {
		mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"\",\"targetFrequency\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.errors.length()").value(2));
	}

	@Test
	void keepsTheHabitStatusFilterContract() throws Exception {
		mockMvc.perform(get("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("status", "ACTIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(get("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("status", "invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("HABIT_INVALID_STATUS"));
	}

	@Test
	void streakRequiresAnOwnedHabitButStillReturnsZeroWithoutEntries() throws Exception {
		habitRepository.save(new Habit("owned-habit", USER_ID, "Read", "book", TargetFrequency.DAILY, true));
		habitRepository.save(new Habit("other-habit", "another-user", "Walk", "walk", TargetFrequency.DAILY, true));

		mockMvc.perform(get("/habits/{habitId}/streak", "owned-habit")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentStreak").value(0));
		for (String habitId : new String[]{"missing-habit", "other-habit"}) {
			mockMvc.perform(get("/habits/{habitId}/streak", habitId)
						.with(jwt().jwt(token -> token.subject(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("HABIT_NOT_FOUND"))
					.andExpect(jsonPath("$.error.message").value("Habit was not found."));
		}
	}

	@Test
	void rejectsUnsupportedTargetFrequencyWithAFieldValidationError() throws Exception {
		mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Exercise\",\"targetFrequency\":\"WEEKLY\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.errors.length()").value(1))
				.andExpect(jsonPath("$.error.errors[0].field").value("targetFrequency"))
				.andExpect(jsonPath("$.error.errors[0].message").value("must be DAILY"));
	}

	@Test
	void supportsHabitUpdateArchiveRestoreAndStatusFiltering() throws Exception {
		String response = mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Read\",\"icon\":\"book\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode data = new tools.jackson.databind.ObjectMapper().readTree(response).path("data");
		String habitId = data.path("id").asString();
		long version = data.path("version").asLong();

		response = mockMvc.perform(patch("/habits/{habitId}", habitId)
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Read daily\",\"icon\":\"books\",\"version\":" + version + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Read daily"))
				.andReturn().getResponse().getContentAsString();
		version = new tools.jackson.databind.ObjectMapper().readTree(response).path("data").path("version").asLong();
		mockMvc.perform(patch("/habits/{habitId}", habitId)
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Stale update\",\"icon\":\"books\",\"version\":" + (version - 1) + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("HABIT_CONFLICT"));

		response = mockMvc.perform(post("/habits/{habitId}/archive", habitId)
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + version + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.active").value(false))
				.andReturn().getResponse().getContentAsString();
		version = new tools.jackson.databind.ObjectMapper().readTree(response).path("data").path("version").asLong();

		mockMvc.perform(get("/habits").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get("/habits").param("status", "archived")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(habitId));

		mockMvc.perform(post("/habits/{habitId}/restore", habitId)
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + version + "}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
	}
}
