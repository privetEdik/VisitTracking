package com.kettlebell.visittracking.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RootDto {
    private List<PatientDto> data;
    private Integer count;

    public RootDto(List<PatientDto> data) {
        this.data = data;
        this.count = data.size();
    }
}
