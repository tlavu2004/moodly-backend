package com.tlavu.moodly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.support.MongoTestConfiguration;
import com.tlavu.moodly.shared.time.MoodlyTime;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MongoTestConfiguration.class, ApiIntegrationTest.FixedClockConfiguration.class})
class ApiIntegrationTest {

	private static final String USER_ID = "__test_phase1_api_user__";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private Clock clock;
	@Autowired
	private DailyEntryRepository dailyEntryRepository;
	@Autowired
	private HabitRepository habitRepository;

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteAll();
		habitRepository.deleteAll();
	}

	@Test
	void supportsThePhaseOneHappyPathWithConsistentResponseEnvelope() throws Exception {
		mockMvc.perform(get("/entries/today").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.date").value(MoodlyTime.today(clock).toString()))
				.andExpect(jsonPath("$.data.checkedIn").value(false))
				.andExpect(jsonPath("$.data.entry").doesNotExist());

		String createHabitResponse = mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID).claim("email", "api@example.com")))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Exercise\",\"icon\":\"run\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.userId").value(USER_ID))
				.andExpect(jsonPath("$.data.name").value("Exercise"))
				.andExpect(jsonPath("$.data.icon").value("run"))
				.andExpect(jsonPath("$.data.targetFrequency").value("DAILY"))
				.andExpect(jsonPath("$.data.active").value(true))
				.andExpect(jsonPath("$.data.version").isNumber())
				.andReturn().getResponse().getContentAsString();
		String habitId = new tools.jackson.databind.ObjectMapper().readTree(createHabitResponse).path("data").path("id").asString();

		mockMvc.perform(get("/habits").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Exercise"));

		mockMvc.perform(patch("/entries/today")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"habitId\":\"" + habitId + "\",\"done\":true,\"note\":\"Completed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(USER_ID))
				.andExpect(jsonPath("$.data.date").value(MoodlyTime.today(clock).toString()))
				.andExpect(jsonPath("$.data.habits[0].habitId").value(habitId))
				.andExpect(jsonPath("$.data.habits[0].done").value(true));

		String readingResponse = mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Reading\",\"icon\":\"book\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode reading = new tools.jackson.databind.ObjectMapper().readTree(readingResponse).path("data");
		String readingId = reading.path("id").asString();
		mockMvc.perform(patch("/entries/today")
				.with(jwt().jwt(token -> token.subject(USER_ID)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"habitId\":\"" + readingId + "\",\"done\":true,\"note\":\"Before archive\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/habits/{habitId}/archive", readingId)
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + reading.path("version").asLong() + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/entries/today")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"habitId\":\"" + readingId + "\",\"done\":false,\"note\":\"Missed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.habits.length()").value(2));

		mockMvc.perform(put("/entries/today/mood")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"score\":4,\"tags\":[\"calm\"],\"note\":\"Good day\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mood.score").value(4))
				.andExpect(jsonPath("$.data.mood.tags[0]").value("calm"))
				.andExpect(jsonPath("$.data.mood.note").value("Good day"));

		mockMvc.perform(get("/entries/today").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.checkedIn").value(true))
				.andExpect(jsonPath("$.data.entry.mood.score").value(4))
				.andExpect(jsonPath("$.data.entry.habits.length()").value(2));

		LocalDate entryDate = dailyEntryRepository
				.findByUserIdAndDateLessThanEqualOrderByDateDesc(USER_ID, MoodlyTime.today(clock))
				.getFirst()
				.getDate();

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", entryDate.toString())
					.param("to", entryDate.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.hasNext").value(false));

		mockMvc.perform(get("/stats/mood-trend")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].date").value(entryDate.toString()))
				.andExpect(jsonPath("$.data[0].averageScore").value(4.0));

		mockMvc.perform(get("/stats/most-missed-habits")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].habitId").value(readingId))
				.andExpect(jsonPath("$.data[0].missedCount").value(1));

		mockMvc.perform(get("/dashboard")
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(jsonPath("$.data.todayEntry.date").value(entryDate.toString()))
				.andExpect(jsonPath("$.data.todayEntry.userId").value(USER_ID))
				.andExpect(jsonPath("$.data.todayEntry.habits[0].habitId").value(habitId))
				.andExpect(jsonPath("$.data.todayEntry.mood.score").value(4))
				.andExpect(jsonPath("$.data.activeHabits.length()").value(1))
				.andExpect(jsonPath("$.data.activeHabits[0].id").value(habitId))
				.andExpect(jsonPath("$.data.activeHabits[0].userId").value(USER_ID))
				.andExpect(jsonPath("$.data.activeHabits[0].targetFrequency").value("DAILY"))
				.andExpect(jsonPath("$.data.completedHabitCount").value(1))
				.andExpect(jsonPath("$.data.totalHabitCount").value(1))
				.andExpect(jsonPath("$.data.completionRatio").value(1.0))
				.andExpect(jsonPath("$.data.weeklyMood.averageScore").value(4.0))
				.andExpect(jsonPath("$.data.weeklyMood.entryCount").value(1))
				.andExpect(jsonPath("$.data.bestCurrentStreak").value(1));

		mockMvc.perform(get("/habits/{habitId}/streak", habitId)
					.with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentStreak").value(1));
	}

	@Test
	void publishesRequiredNullableAndEnvelopeExamplesInOpenApi() throws Exception {
		String document = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.Habit").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.HabitResponse.required", org.hamcrest.Matchers.hasItems("id", "userId", "name", "active", "version")))
				.andExpect(jsonPath("$.components.schemas.HabitResponse.properties.targetFrequency.type").value("string"))
				.andExpect(jsonPath("$.components.schemas.HabitResponse.properties.targetFrequency.enum[0]").value("DAILY"))
				.andExpect(jsonPath("$.components.schemas.DailyEntry").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.DailyEntryResponse.required", org.hamcrest.Matchers.hasItems("id", "date", "habits")))
				.andExpect(jsonPath("$.components.schemas.EntrySearchResult.required", org.hamcrest.Matchers.hasItems("entryId", "date", "highlights")))
				.andExpect(jsonPath("$.components.schemas.EntrySearchResult.properties.highlights.additionalProperties.items['$ref']")
						.value("#/components/schemas/HighlightFragment"))
				.andExpect(jsonPath("$.components.schemas.HighlightFragment.properties.ranges.items['$ref']")
						.value("#/components/schemas/HighlightRange"))
				.andExpect(jsonPath("$.components.examples.SuccessEnvelope").exists())
				.andExpect(jsonPath("$.components.examples.EmptyEnvelope").exists())
				.andExpect(jsonPath("$.components.examples.ErrorEnvelope").exists())
				.andReturn().getResponse().getContentAsString();

		JsonNode openApi = new tools.jackson.databind.ObjectMapper().readTree(document);
		JsonNode examples = openApi.path("components").path("examples");
		JsonNode badRequestExample = openApi.path("components").path("responses")
			.path("BadRequest").path("content").path("application/json")
			.path("examples").path("default").path("value");
		assertThat(badRequestExample.isObject()).isTrue();
		assertThat(badRequestExample.path("data").isNull()).isTrue();
		assertThat(badRequestExample.path("error").path("status").asInt()).isEqualTo(400);
		assertThat(badRequestExample.path("error").path("code").asString()).isEqualTo("VALIDATION_FAILED");
		assertThat(openApi.path("components").path("responses").path("AvatarUploadNotFound")
				.path("content").path("application/json").path("examples").path("default").path("value")
				.path("error").path("code").asString()).isEqualTo("AVATAR_UPLOAD_NOT_FOUND");
		assertThat(openApi.path("components").path("responses").path("AvatarServiceUnavailable")
				.path("content").path("application/json").path("examples").path("default").path("value")
				.path("error").path("status").asInt()).isEqualTo(503);
		assertThat(openApi.path("paths").path("/habits/{habitId}").path("patch").path("responses").path("409").path("$ref").asString())
				.isEqualTo("#/components/responses/Conflict");
		assertThat(openApi.path("paths").path("/habits/{habitId}").path("patch").path("responses").path("200").isMissingNode()).isFalse();
		assertThat(openApi.path("paths").path("/habits/{habitId}/archive").path("post").path("responses").path("409").path("$ref").asString())
				.isEqualTo("#/components/responses/Conflict");
		assertThat(openApi.path("paths").path("/habits/{habitId}/archive").path("post").path("responses").path("200").isMissingNode()).isFalse();
		assertThat(openApi.path("paths").path("/habits/{habitId}/restore").path("post").path("responses").path("409").path("$ref").asString())
				.isEqualTo("#/components/responses/Conflict");
		assertThat(openApi.path("paths").path("/habits/{habitId}/restore").path("post").path("responses").path("200").isMissingNode()).isFalse();
		assertThat(openApi.path("paths").path("/entries/today").path("patch").path("responses").path("409").path("$ref").asString())
				.isEqualTo("#/components/responses/HabitArchived");
		assertThat(examples.path("SuccessEnvelope").path("value").toString())
				.isEqualTo("{\"success\":true,\"data\":{\"id\":\"example-id\"},\"timestamp\":\"2026-09-12T10:00:00Z\"}");
		assertThat(examples.path("ErrorEnvelope").path("value").toString())
				.isEqualTo("{\"success\":false,\"error\":{\"status\":400,\"code\":\"INVALID_REQUEST\",\"message\":\"The request is invalid.\",\"path\":\"/example\",\"errors\":[],\"requestId\":\"2ea18f35-e92e-4ed0-a629-c3f2fbffc45d\"},\"timestamp\":\"2026-09-12T10:00:00Z\"}");
	}

	@Test
	void rejectsARequestWithoutAnAccessToken() throws Exception {
		mockMvc.perform(get("/habits").header("X-Request-ID", "unauthorized-test"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Request-ID", "unauthorized-test"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.error.requestId").value("unauthorized-test"));
		mockMvc.perform(get("/entries"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/entries/search").param("q", "mood"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/stats/mood-trend"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/stats/most-missed-habits"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/auth/profile"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsTheStandardUnauthorizedEnvelopeForAMalformedBearerToken() throws Exception {
		mockMvc.perform(get("/habits").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsTheStandardUnauthorizedEnvelopeForASignatureShapedBearerTokenRejectedByTheDecoder() throws Exception {
		String tamperedJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9."
				+ "eyJzdWIiOiJhdXRoMHx0ZXN0In0.invalid-signature";

		mockMvc.perform(get("/habits").header("Authorization", "Bearer " + tamperedJwt))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void isolatesMongoBackedHabitsAndEntriesBetweenJwtSubjects() throws Exception {
		String userA = "auth0|mongo-owner";
		String userB = "auth0|mongo-other";
		String created = mockMvc.perform(post("/habits").with(jwt().jwt(token -> token.subject(userA)))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Private A\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String privateHabitId = new tools.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asString();
		mockMvc.perform(patch("/entries/today").with(jwt().jwt(token -> token.subject(userA)))
					.contentType(MediaType.APPLICATION_JSON).content("{\"habitId\":\"" + privateHabitId + "\",\"done\":true}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/habits").with(jwt().jwt(token -> token.subject(userB))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get("/entries").with(jwt().jwt(token -> token.subject(userB)))
					.param("from", MoodlyTime.today(clock).toString()).param("to", MoodlyTime.today(clock).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
		mockMvc.perform(get("/dashboard").with(jwt().jwt(token -> token.subject(userB))))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.todayEntry").doesNotExist())
				.andExpect(jsonPath("$.data.activeHabits.length()").value(0))
				.andExpect(jsonPath("$.data.completedHabitCount").value(0))
				.andExpect(jsonPath("$.data.totalHabitCount").value(0))
				.andExpect(jsonPath("$.data.completionRatio").value(0.0))
				.andExpect(jsonPath("$.data.weeklyMood.averageScore").doesNotExist())
				.andExpect(jsonPath("$.data.weeklyMood.entryCount").value(0))
				.andExpect(jsonPath("$.data.bestCurrentStreak").value(0));
	}

	@Test
	void rejectsUnknownOrAnotherUsersHabitLogWithoutCreatingAnEntry() throws Exception {
		String otherUser = "auth0|another-habit-owner";
		String created = mockMvc.perform(post("/habits")
					.with(jwt().jwt(token -> token.subject(otherUser)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Private\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String otherHabitId = new tools.jackson.databind.ObjectMapper().readTree(created)
				.path("data").path("id").asString();

		for (String habitId : new String[]{"missing-habit", otherHabitId}) {
			mockMvc.perform(patch("/entries/today")
						.with(jwt().jwt(token -> token.subject(USER_ID)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"habitId\":\"" + habitId + "\",\"done\":true}"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("HABIT_NOT_FOUND"))
					.andExpect(jsonPath("$.error.message").value("Habit was not found."));
		}
		assertThat(dailyEntryRepository.findByUserId(USER_ID)).isEmpty();
	}

	@Test
	void rejectsNewLogForArchivedHabitWithoutCreatingAnEntry() throws Exception {
		String created = mockMvc.perform(post("/habits")
				.with(jwt().jwt(token -> token.subject(USER_ID)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Read\",\"targetFrequency\":\"DAILY\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode habit = new tools.jackson.databind.ObjectMapper().readTree(created).path("data");
		String habitId = habit.path("id").asString();
		mockMvc.perform(post("/habits/{habitId}/archive", habitId)
				.with(jwt().jwt(token -> token.subject(USER_ID)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":" + habit.path("version").asLong() + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/entries/today")
				.with(jwt().jwt(token -> token.subject(USER_ID)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"habitId\":\"" + habitId + "\",\"done\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("HABIT_ARCHIVED"))
				.andExpect(jsonPath("$.error.message").value("Archived habits cannot receive new logs."));
		assertThat(dailyEntryRepository.findByUserId(USER_ID)).isEmpty();
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
		LocalDate today = MoodlyTime.today(clock);

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", today.toString())
					.param("to", today.minusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ENTRY_INVALID_RANGE"));

		mockMvc.perform(get("/entries")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", today.plusDays(1).toString())
					.param("to", today.plusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ENTRY_INVALID_RANGE"));
	}

	@Test
	void rejectsUnsupportedMoodTrendPeriod() throws Exception {
		mockMvc.perform(get("/stats/mood-trend")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("period", "month"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("STATS_INVALID_PERIOD"))
				.andExpect(jsonPath("$.error.message").value("Unsupported mood trend period. Supported values: week."));
	}

	@Test
	void normalizesFrameworkRequestFailures() throws Exception {
		RequestPostProcessor jwtUser = jwt().jwt(token -> token.subject(USER_ID));

		mockMvc.perform(get("/entries").with(jwtUser).param("to", "2026-10-21"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/entries").with(jwt().jwt(token -> token.subject(USER_ID)))
					.param("from", "2026-10-01").param("to", "2026-10-21").param("page", "invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/entries/today").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));

		mockMvc.perform(post("/habits").with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.TEXT_PLAIN).content("invalid"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));

		mockMvc.perform(get("/entries/today").with(jwt().jwt(token -> token.subject(USER_ID)))
					.accept(MediaType.TEXT_PLAIN))
				.andExpect(status().isNotAcceptable())
				.andExpect(jsonPath("$.error.code").value("NOT_ACCEPTABLE"));

		mockMvc.perform(get("/missing-endpoint").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock fixedMoodlyClock() {
			return Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
		}
	}

}
