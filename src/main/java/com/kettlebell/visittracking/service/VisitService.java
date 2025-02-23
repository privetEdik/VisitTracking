package com.kettlebell.visittracking.service;

import com.kettlebell.visittracking.controller.record.VisitRequest;
import com.kettlebell.visittracking.controller.record.VisitResponse;
import com.kettlebell.visittracking.controller.dto.DoctorDto;
import com.kettlebell.visittracking.controller.dto.PatientDto;
import com.kettlebell.visittracking.controller.dto.RootDto;
import com.kettlebell.visittracking.controller.dto.VisitDto;
import com.kettlebell.visittracking.exception.BrookedTimeException;
import com.kettlebell.visittracking.exception.InvalidTimeFormatException;
import com.kettlebell.visittracking.exception.InvalidTimeRangeException;
import com.kettlebell.visittracking.exception.NotFoundException;
import com.kettlebell.visittracking.repository.DoctorRepository;
import com.kettlebell.visittracking.repository.PatientRepository;
import com.kettlebell.visittracking.repository.entity.Doctor;
import com.kettlebell.visittracking.repository.entity.Patient;
import com.kettlebell.visittracking.repository.entity.Visit;
import com.kettlebell.visittracking.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisitService {
    private final VisitRepository visitRepo;
    private final DoctorRepository doctorRepo;
    private final PatientRepository patientRepo;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public VisitResponse createVisit(VisitRequest request) {
        Doctor doctor = doctorRepo.findById(request.doctorId())
                .orElseThrow(() -> new NotFoundException("Doctor with " + request.doctorId() + " not found"));
        Patient patient = patientRepo.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Patient with " + request.patientId() + " not found"));

        // Часовой пояс врача
        ZoneId doctorZone = ZoneId.of(doctor.getTimezone());

        // Парсим строку в LocalDateTime
        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.start(), FORMATTER);
            endTime = LocalDateTime.parse(request.end(), FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidTimeFormatException("Invalid date format. Expected format: yyyy-MM-dd HH:mm:ss");
        }

        if (endTime.isBefore(startTime)) {
            throw new InvalidTimeRangeException("Start time must be before end time");
        }

        // Добавляем таймзону врача
        ZonedDateTime startZoned = startTime.atZone(doctorZone);
        ZonedDateTime endZoned = endTime.atZone(doctorZone);

        // Переводим в UTC
        Instant startUtc = startZoned.toInstant();
        Instant endUtc = endZoned.toInstant();

        // Проверяем пересечение с уже существующими визитами
        if (visitRepo.existsByDoctorAndTimeOverlap(doctor, startUtc, endUtc)) {
            throw new BrookedTimeException("Doctor is already booked at this time");
        }

        Visit visit = new Visit();
        visit.setStartDateTime(startUtc);
        visit.setEndDateTime(endUtc);
        visit.setPatient(patient);
        visit.setDoctor(doctor);

        return new VisitResponse(visitRepo.save(visit));
    }

    public RootDto findPatientsWithLastVisits(String search, Set<Integer> doctorIds, Pageable pageable) {
        Page<Object[]> results = visitRepo.findPatientsWithLastVisits(search, doctorIds, pageable);

        // Группируем визиты по пациентам
        Map<Integer, PatientDto> patientMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            Patient patient = (Patient) row[0];
            Instant startDateTime = (Instant) row[1];
            Instant endDateTime = (Instant) row[2];
            Doctor doctor = (Doctor) row[3];
            Long totalPatients = ((Number) row[4]).longValue();

            DoctorDto doctorDto = new DoctorDto(doctor.getFirstName(), doctor.getLastName(), totalPatients);

            VisitDto visitDto = new VisitDto(
                    formatDate(startDateTime, doctor.getTimezone()),
                    formatDate(endDateTime, doctor.getTimezone()),
                    doctorDto
            );

            patientMap.computeIfAbsent(patient.getId(), id ->
                    new PatientDto(patient.getFirstName(), patient.getLastName(), new ArrayList<>())
            ).getLastVisits().add(visitDto);
        }

        return new RootDto(new ArrayList<>(patientMap.values()));
    }
    private String formatDate(Instant instant, String timezone) {
        return FORMATTER.withZone(ZoneId.of(timezone)).format(instant);
    }
}
