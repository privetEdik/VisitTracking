package com.kettlebell.visittracking.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private String firstName;
    private String lastName;
    private Long totalPatients;
}
