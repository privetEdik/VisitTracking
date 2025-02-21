package com.kettlebell.visittracking.repository;

import com.kettlebell.visittracking.repository.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Integer> {
}
