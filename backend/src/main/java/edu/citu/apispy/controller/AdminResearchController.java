package edu.citu.apispy.controller;

import edu.citu.apispy.service.research.ResearchReportService;
import edu.citu.apispy.web.InputLimits;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Research console API. Guarded by SecurityConfig: {@code /api/admin/**} requires ROLE_ADMINISTRATOR
 * (401 when signed out, 403 for players). All endpoints are read-only.
 */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminResearchController {

	private final ResearchReportService reports;

	public AdminResearchController(ResearchReportService reports) {
		this.reports = reports;
	}

	@GetMapping("/reports/kpi")
	public Map<String, Object> kpi(
			@RequestParam(required = false) @Size(max = InputLimits.ID) String participant) {
		return reports.kpi(participant);
	}

	@GetMapping("/participants")
	public List<Map<String, Object>> participants() {
		return reports.participants();
	}

	@GetMapping("/export/events.csv")
	public ResponseEntity<String> exportEvents() {
		return csv("api-spy-research-events.csv", reports.eventsCsv());
	}

	@GetMapping("/export/first-attempts.csv")
	public ResponseEntity<String> exportFirstAttempts() {
		return csv("api-spy-first-attempts.csv", reports.firstAttemptsCsv());
	}

	private static ResponseEntity<String> csv(String filename, String body) {
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(body);
	}
}
