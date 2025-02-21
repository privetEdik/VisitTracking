package com.kettlebell.visittracking.controller.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VisitRequest(
        @NotBlank(message = "Start time is required")
        String start,
        @NotBlank(message = "End time is required")
        String end,
        @NotNull(message = "Patient ID is required")
        @Positive(message = "Patient ID must be a positive number")
        Integer patientId,
        @NotNull(message = "Doctor ID is required")
        @Positive(message = "Doctor ID must be a positive number")
        Integer doctorId) {
}
