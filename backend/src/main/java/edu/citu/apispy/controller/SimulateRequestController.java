package edu.citu.apispy.controller;

import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.RequestSimulationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class SimulateRequestController {

	private final RequestSimulationService requestSimulationService;

	public SimulateRequestController(RequestSimulationService requestSimulationService) {
		this.requestSimulationService = requestSimulationService;
	}

	@PostMapping("/simulate")
	public ResponseEntity<SimulateResponseDto> simulate(@Valid @RequestBody SimulateRequestDto request) {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return ResponseEntity.ok(requestSimulationService.simulate(playerId, request));
	}
}
