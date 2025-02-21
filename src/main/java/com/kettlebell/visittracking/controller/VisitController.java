package com.kettlebell.visittracking.controller;

import com.kettlebell.visittracking.controller.record.PatientsRequest;
import com.kettlebell.visittracking.controller.record.VisitResponse;
import com.kettlebell.visittracking.controller.record.VisitRequest;
import com.kettlebell.visittracking.controller.dto.RootDto;
import com.kettlebell.visittracking.service.VisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
class VisitController {
    private final VisitService visitService;

    @PostMapping()
    public VisitResponse createVisit(@Valid @RequestBody VisitRequest request) {
        return visitService.createVisit(request);
    }

    @GetMapping
    public ResponseEntity<RootDto> getPatients(@Valid PatientsRequest request) {
        Pageable pageable = PageRequest.of(
                request.page() != null ? request.page() : 0,
                request.size() != null ? request.size() : 10
        );
        return ResponseEntity.ok(visitService.findPatientsWithLastVisits(request.search(), request.doctorIds(), pageable));
    }
}
