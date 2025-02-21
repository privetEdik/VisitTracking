package com.kettlebell.visittracking.repository;

import com.kettlebell.visittracking.repository.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, Integer> {
}
