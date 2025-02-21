package com.kettlebell.visittracking.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private String firstName;
    private String lastName;
    private List<VisitDto> lastVisits;
}
