package com.tlavu.moodly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.infrastructure.HabitRepository;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class ApiIntegrationTest {

	private static final String USER_ID = "__test_phase1_api_user__";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private DailyEntryRepository dailyEntryRepository;
	@Autowired
	private HabitRepository habitRepository;
	@Autowired
	private UserProfileRepository userProfileRepository;

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteByUserId(USER_ID);
		habitRepository.deleteAll(habitRepository.findByUserIdAndActiveTrue(USER_ID));
		userProfileRepository.deleteAll();
	}

	@Test
	void supportsThePhaseOneHappyPathWithConsistentResponseEnvelope() throws Exception {
		mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID).claim("email", "api@example.com")))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Exercise\",\"icon\":\"run\",\"targetFrequency\":\"daily\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("Exercise"));

		mockMvc.perform(get("/habits").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Exercise"));

		mockMvc.perform(patch("/entries/today")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"habitId\":\"exercise\",\"done\":true,\"note\":\"Completed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.habits[0].done").value(true));

		mockMvc.perform(patch("/entries/today")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"habitId\":\"reading\",\"done\":false,\"note\":\"Missed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.habits.length()").value(2));

		mockMvc.perform(put("/entries/today/mood")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"score\":4,\"tags\":[\"calm\"],\"note\":\"Good day\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mood.score").value(4));

		var entryDate = dailyEntryRepository
				.findByUserIdAndDateLessThanEqualOrderByDateDesc(USER_ID, LocalDate.now())
				.getFirst()
				.getDate();

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", entryDate.toString())
					.param("to", entryDate.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(1));

		mockMvc.perform(get("/stats/mood-trend")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].averageScore").value(4.0));

		mockMvc.perform(get("/stats/most-missed-habits")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].habitId").value("reading"))
				.andExpect(jsonPath("$.data[0].missedCount").value(1));

		mockMvc.perform(get("/habits/exercise/streak")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentStreak").value(1));
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
	void rejectsARequestWithoutAnAccessToken() throws Exception {
		mockMvc.perform(get("/habits"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsValidationErrorWhenMoodScoreIsOutsideAllowedRange() throws Exception {
		mockMvc.perform(put("/entries/today/mood")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"score\":6,\"tags\":[],\"note\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.errors[0].field").value("score"));
	}

	@Test
	void returnsInvalidRequestErrorForMalformedJson() throws Exception {
		mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsInvalidEntryDateRanges() throws Exception {
		var today = LocalDate.now();

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", today.toString())
					.param("to", today.minusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", today.plusDays(1).toString())
					.param("to", today.plusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsUnsupportedMoodTrendPeriod() throws Exception {
		mockMvc.perform(get("/stats/mood-trend")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("period", "month"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

}
