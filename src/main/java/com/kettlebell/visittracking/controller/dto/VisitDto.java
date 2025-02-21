package com.kettlebell.visittracking.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitDto {
    private String start;
    private String end;
    private DoctorDto doctor;
}
