package com.tlavu.moodly.modules.dashboard.controller;

import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.modules.dashboard.dto.response.DashboardResponse;
import com.tlavu.moodly.modules.dashboard.service.implementation.DashboardService;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.time.MoodlyTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Authenticated user's current dashboard summary")
@RequiredArgsConstructor
public class DashboardController {
	private final DashboardService dashboardService;
	private final CurrentUser currentUser;
	private final Clock clock;

	@GetMapping
	@Operation(
			summary = "Get dashboard summary",
			description = "Returns today's entry, active habits, completion ratio, current-week mood summary, and the best current streak. The response is user-specific and is not cacheable."
	)
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
		LocalDate today = MoodlyTime.today(clock);
		ApiResponse<DashboardResponse> response = ApiResponse.success(
				DashboardResponse.from(
						dashboardService.getDashboard(currentUser.id(), today)
				)
		);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(response);
	}

}
