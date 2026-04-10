package com.lin.spring.ticketrace.simulator.controller;

import com.lin.spring.ticketrace.common.dto.SimulationRequest;
import com.lin.spring.ticketrace.common.dto.SimulationResponse;
import com.lin.spring.ticketrace.simulator.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulationService simulationService;

    @PostMapping("/run")
    public SimulationResponse run(@Valid @RequestBody SimulationRequest request) {
        return simulationService.run(request);
    }
}
