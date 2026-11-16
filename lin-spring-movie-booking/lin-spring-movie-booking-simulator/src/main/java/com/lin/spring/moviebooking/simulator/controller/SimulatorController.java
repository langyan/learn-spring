package com.lin.spring.moviebooking.simulator.controller;

import com.lin.spring.moviebooking.common.dto.SimulationRequest;
import com.lin.spring.moviebooking.common.dto.SimulationResponse;
import com.lin.spring.moviebooking.simulator.service.RaceSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final RaceSimulationService simulationService;

    /**
     * Fire N concurrent holds at the same seat set. Expect exactly one
     * success per seat group and oversellDetected == 0 in the summary.
     */
    @PostMapping("/race")
    public SimulationResponse race(@Valid @RequestBody SimulationRequest request) throws InterruptedException {
        return simulationService.simulate(request);
    }
}
