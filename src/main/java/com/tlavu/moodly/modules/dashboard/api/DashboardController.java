package com.tlavu.moodly.modules.dashboard.api;

import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.modules.dashboard.application.DashboardService;
import com.tlavu.moodly.modules.stats.api.MoodTrendPeriod;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Authenticated user's current dashboard summary")
public class DashboardController {

	private final DashboardService dashboardService;
	private final CurrentUser currentUser;

	public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
		this.dashboardService = dashboardService;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(
			summary = "Get dashboard summary",
			description = "Returns today's entry, active habits, completion ratio, current-week mood summary, and the best current streak. The response is user-specific and is not cacheable."
	)
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
		var today = LocalDate.now(MoodTrendPeriod.TIME_ZONE);
		var response = ApiResponse.success(dashboardService.getDashboard(currentUser.id(), today));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

}
