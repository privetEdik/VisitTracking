package com.kettlebell.visittracking.repository;

import com.kettlebell.visittracking.repository.entity.Doctor;
import com.kettlebell.visittracking.repository.entity.Visit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Set;

public interface VisitRepository extends JpaRepository<Visit, Integer> {
    @Query("""
    SELECT COUNT(v) > 0 FROM Visit v
    WHERE v.doctor = :doctor
    AND (
        (v.startDateTime < :endUtc AND v.endDateTime > :startUtc)
    )
""")
    boolean existsByDoctorAndTimeOverlap(@Param("doctor") Doctor doctor,
                                         @Param("startUtc") Instant startUtc,
                                         @Param("endUtc") Instant endUtc);

    @Query("""
    SELECT p, v.startDateTime, v.endDateTime, d, COUNT(DISTINCT v2.patient.id) AS totalPatients
    FROM Visit v
    JOIN v.patient p
    JOIN v.doctor d
    LEFT JOIN Visit v2 ON v2.doctor.id = d.id 
    WHERE (:search IS NULL OR :search = ''
           OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
    AND (:doctorIds IS NULL OR d.id IN :doctorIds)
    AND v.startDateTime = (SELECT MAX(v3.startDateTime)
                           FROM Visit v3
                           WHERE v3.patient.id = p.id AND v3.doctor.id = d.id)
    GROUP BY p.id, v.id, d.id
    ORDER BY p.lastName, p.firstName, v.startDateTime DESC
""")
    Page<Object[]> findPatientsWithLastVisits(
            @Param("search") String search,
            @Param("doctorIds") Set<Integer> doctorIds,
            Pageable pageable
    );

}
