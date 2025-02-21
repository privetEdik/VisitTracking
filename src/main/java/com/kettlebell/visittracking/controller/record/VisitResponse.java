package com.kettlebell.visittracking.controller.record;

import com.kettlebell.visittracking.repository.entity.Visit;
import lombok.Getter;

@Getter
public class VisitResponse {
    private final Integer id;
    public VisitResponse(Visit visit) {
        this.id = visit.getId();
    }
}
