package com.tlavu.moodly.modules.cdc.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.shared.presentation.advice.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CdcMaintenanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "moodly.cdc.maintenance-key=test-key")
class CdcMaintenanceControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private DailyEntryReindexService reindexService;
	@MockitoBean
	private CdcDeadLetterRepository deadLetterRepository;
	@MockitoBean
	private CdcDeliveryService deliveryService;

	@Test
	void rejectsMissingOrWrongMaintenanceKey() throws Exception {
		mockMvc.perform(post("/internal/cdc/reindex"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mockMvc.perform(post("/internal/cdc/reindex").header("X-Maintenance-Key", "wrong-key"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(reindexService);
	}

	@Test
	void reindexesWithAValidMaintenanceKey() throws Exception {
		when(reindexService.reindex()).thenReturn(new DailyEntryReindexService.ReindexResult(4));

		mockMvc.perform(post("/internal/cdc/reindex").header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.indexedEntries").value(4));

		verify(reindexService).reindex();
	}

	@Test
	void replaysAnExistingDeadLetterAndReturnsNotFoundOtherwise() throws Exception {
		var deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
		when(deadLetterRepository.findById("missing")).thenReturn(Optional.empty());

		mockMvc.perform(post("/internal/cdc/dead-letters/dead-letter-1/replay").header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/internal/cdc/dead-letters/missing/replay").header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isNotFound());

		verify(deliveryService).replay(deadLetter);
	}
}
