package com.kettlebell.visittracking.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@Table(name = "visits")
@AllArgsConstructor
@NoArgsConstructor
public class Visit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    private Instant startDateTime;
    @Column(nullable = false)
    private Instant endDateTime;

    @ManyToOne
    private Patient patient;

    @ManyToOne
    private Doctor doctor;
}
