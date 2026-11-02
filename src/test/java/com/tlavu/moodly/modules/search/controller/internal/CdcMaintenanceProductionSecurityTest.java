package com.tlavu.moodly.modules.search.controller.internal;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.auth.security.config.SecurityConfiguration;
import com.tlavu.moodly.modules.search.service.indexing.CdcDeliveryService;
import com.tlavu.moodly.modules.search.service.indexing.DailyEntryReindexService;
import com.tlavu.moodly.modules.search.service.view.ReindexView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CdcMaintenanceController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
		"AUTH0_ISSUER_URI=https://example.test/",
		"AUTH0_AUDIENCE=https://api.example.test",
		"moodly.cdc.maintenance-key=test-key",
		"moodly.cdc.admin-authority=SCOPE_moodly:admin"
})
class CdcMaintenanceProductionSecurityTest {
	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private DailyEntryReindexService reindexService;
	@MockitoBean
	private CdcDeliveryService deliveryService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void requiresAdminAuthorityInAdditionToMaintenanceKey() throws Exception {
		mockMvc.perform(post("/internal/cdc/reindex").header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/cdc/reindex")
						.with(jwt().authorities(() -> "SCOPE_other"))
						.header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/cdc/reindex")
						.with(jwt().authorities(() -> "SCOPE_moodly:admin")))
				.andExpect(status().isForbidden());
		verifyNoInteractions(reindexService, deliveryService);
		when(reindexService.reindex()).thenReturn(new ReindexView(0));
		mockMvc.perform(post("/internal/cdc/reindex")
						.with(jwt().authorities(() -> "SCOPE_moodly:admin"))
						.header("X-Maintenance-Key", "test-key"))
				.andExpect(status().isOk());
	}
}
