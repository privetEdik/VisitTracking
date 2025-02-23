package com.kettlebell.visittracking.controller.record;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.Length;

import java.util.Set;

public record PatientsRequest(
        @Length(max = 20, message = "Search query must not exceed 20 characters.")
        String search,
        Set<Integer> doctorIds,
        @Min(value = 0, message = "Page number cannot be negative.")
        Integer page,
        @Min(value = 10, message = "Minimum page size is 10.")
        @Max(value = 100, message = "Maximum page size is 100.")
        Integer size
) {}
