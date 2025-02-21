package com.kettlebell.visittracking.repository;

import com.kettlebell.visittracking.repository.entity.Doctor;
import com.kettlebell.visittracking.repository.entity.Visit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

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
                SELECT v,
                           (SELECT COUNT(DISTINCT v2.patient.id)
                            FROM Visit v2
                            WHERE v2.doctor.id = d.id) AS totalPatients
                FROM Visit v
                JOIN v.patient p
                JOIN v.doctor d
                WHERE (:search IS NULL OR :search = ''
                       OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
                AND (:doctorIds IS NULL OR d.id IN :doctorIds)
                AND v.startDateTime = (SELECT MAX(v3.startDateTime)
                                       FROM Visit v3
                                       WHERE v3.patient.id = p.id AND v3.doctor.id = d.id)
                ORDER BY p.lastName, p.firstName
            """)
    Page<Object[]> findPatientsWithLastVisits(
            @Param("search") String search,
            @Param("doctorIds") List<Integer> doctorIds,
            Pageable pageable
    );

}
