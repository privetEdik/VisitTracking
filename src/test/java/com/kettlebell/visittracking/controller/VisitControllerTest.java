package com.kettlebell.visittracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kettlebell.visittracking.controller.record.VisitRequest;
import com.kettlebell.visittracking.repository.DoctorRepository;
import com.kettlebell.visittracking.repository.PatientRepository;
import com.kettlebell.visittracking.repository.VisitRepository;
import com.kettlebell.visittracking.repository.entity.Doctor;
import com.kettlebell.visittracking.repository.entity.Patient;
import com.kettlebell.visittracking.repository.entity.Visit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class VisitControllerTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.url", mysql::getJdbcUrl);  // Для Flyway
        registry.add("spring.flyway.user", mysql::getUsername);
        registry.add("spring.flyway.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Doctor doctor;
    private Patient patient;

    void setup(){

        patient = patientRepository.save(new Patient(null, "Test", "Patient"));
        doctor = doctorRepository.save(new Doctor(null, "Test", "Doctor", "Europe/London", null));

        final String baseStartTime = "2025-06-15 12:00:00";
        LocalDateTime startLocal = LocalDateTime.parse(baseStartTime, FORMATTER);
        ZonedDateTime startZoned = startLocal.atZone(ZoneId.of("Europe/London"));
        Instant startUtc = startZoned.toInstant();

        Visit visit = new Visit(null, startUtc, startUtc.plus(Duration.ofMinutes(30)), patient, doctor);
        visitRepository.save(visit);
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void shouldReturnBadRequestWhenPageIsNegative() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .param("page", "-1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsZero() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .param("page", "0")
                        .param("size", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsNegative() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .param("page", "0")
                        .param("size", "-10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenDoctorIdsContainInvalidValues() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .param("doctorIds", "1,abc,3") // "abc" - некорректное значение
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSearchQueryIsTooLong() throws Exception {
        String longSearchQuery = "a".repeat(300); // Очень длинный поисковый запрос
        mockMvc.perform(get("/api/visits")
                        .param("search", longSearchQuery)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsTooLarge() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .param("page", "0")
                        .param("size", "10000") // Очень большое значение
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }


    @Test
    @Transactional
    @DisplayName("Тест: Получение списка пациентов с визитами: 1  пациент - 1 встреча - 1 доктор")
    void testGetPatientsWithLastVisits1() throws Exception {
        // 1. Создаём тестовых пациента1 и доктора1
        Patient patient1 = new Patient(null, "Merry", "Wilson");
        patient1 = patientRepository.save(patient1);
        Doctor doctor1 = new Doctor(null, "Jack", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        String paramDoctorId = doctor1.getId().toString();

        // 2. Создаём пока 1 визит и добавляем в БД
        String datetimeForParse1_1_1 = "2024-02-12T06:00:00Z";
        String datetimeForCheck1_1_1 = datetimeForParse1_1_1.replace("T", " ")
                .replace("Z", "");

        Visit visit1 = new Visit(null, Instant.parse(datetimeForParse1_1_1),
                Instant.parse("2024-02-12T06:30:00Z"),
                patient1, doctor1);
        visitRepository.save(visit1);

        // 2.1 Проверим вывод в общем виде. Задам пока 0-евую страницу(page) и количество
        // записей 10 (size) чтобы выводить и видеть все.
        // Одна запись, Один пациент ("Merry"), Один доктор("Jack")
        mockMvc.perform(get("/api/visits")
                        .param("search", "Merry")
                        .param("doctorIds", paramDoctorId)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Merry"))
                .andExpect(jsonPath("$.data[0].lastVisits[0].doctor.firstName").value("Jack"))
                .andExpect(jsonPath("$.data[0].lastVisits[0].start").value(datetimeForCheck1_1_1))
                .andExpect(jsonPath("$.data[0].lastVisits[0].doctor.totalPatients").value(1));

    }

    @Test
    @Transactional
    @DisplayName("Тест: Получение списка пациентов с визитами: 1  пациент - 2 встречи - 2 доктора(по 1 на каждого)")
    void testGetPatientsWithLastVisits2() throws Exception {
        // Добавляем доктора2 и встречу с ним, но id не добавим в запрос.
        // Чтоб показать зависимость от введенных данных - списка id докторов
        Patient patient1 = new Patient(null, "Sarah", "Wilson");
        patient1 = patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Peter", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        String paramIdDoctor1 = doctor1.getId().toString();

        // Исходное время приема в часовом поясе доктора 1
        String datetimeForParse1_1_1 = "2024-02-12 06:00:00"; // Время в часовом поясе врача
        LocalDateTime startLocal1_1_1 = LocalDateTime.parse(datetimeForParse1_1_1, FORMATTER);
        ZonedDateTime startZoned1_1_1 = startLocal1_1_1.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_1 = startZoned1_1_1.toInstant();

        Visit visit1 = new Visit(null, startUtc1_1_1, startUtc1_1_1.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit1);

        // Доктор 2 (Нью-Йорк)
        Doctor doctor2 = new Doctor(null, "Nata", "Smith", "America/New_York", null);
        doctorRepository.save(doctor2);
        String paramIdDoctor2 = doctor2.getId().toString();

        // Исходное время приема в часовом поясе доктора 2
        String datetimeForParse1_2_2 = "2024-02-12 06:00:00"; // Время в часовом поясе врача
        LocalDateTime startLocal1_2_2 = LocalDateTime.parse(datetimeForParse1_2_2, FORMATTER);
        ZonedDateTime startZoned1_2_2 = startLocal1_2_2.atZone(ZoneId.of("America/New_York"));
        Instant startUtc1_2_2 = startZoned1_2_2.toInstant();

        Visit visit2 = new Visit(null, startUtc1_2_2, startUtc1_2_2.plus(Duration.ofMinutes(30)), patient1, doctor2);
        visitRepository.save(visit2);

        // Ожидаемые значения в часовом поясе врачей
        String expectedStart1_1_1 = FORMATTER.format(startZoned1_1_1);
        String expectedStart1_2_2 = FORMATTER.format(startZoned1_2_2);

        // Тестируем запрос с 1 доктором
        mockMvc.perform(get("/api/visits")
                        .param("search", "Sarah")
                        .param("doctorIds", paramIdDoctor1) // Только доктор 1
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Sarah"))
                .andExpect(jsonPath("$.data[0].lastVisits[0].start").value(expectedStart1_1_1))
                .andExpect(jsonPath("$.data[0].lastVisits[0].doctor.firstName").value("Peter"));

        // Тестируем запрос с обоими докторами
        mockMvc.perform(get("/api/visits")
                        .param("search", "Sarah")
                        .param("doctorIds", paramIdDoctor1 + "," + paramIdDoctor2) // Оба доктора
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Sarah"))
                // Проверяем последнюю встречу с doctor1 (John)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Peter')].start")
                        .value(expectedStart1_1_1))
                // Проверяем последнюю встречу с doctor2 (Alice)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Nata')].start")
                        .value(expectedStart1_2_2));
    }

    @Test
    @Transactional
    @DisplayName("Тест: Получение списка пациентов с визитами:" +
            " 1  пациент - 4 встречи - 3 доктора(1,4в-1д  2в-2д 3в-3д)" +
            " замещение 1 встречи на 4 тех же персонажей")
    void testGetPatientsWithLastVisits3() throws Exception {
        // Создаем пациента
        Patient patient1 = new Patient(null, "Amelia", "Wilson");
        patient1 = patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Arthur", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        String paramIdDoctor1 = doctor1.getId().toString();

        // Доктор 2 (Нью-Йорк)
        Doctor doctor2 = new Doctor(null, "Ivy", "Smith", "America/New_York", null);
        doctorRepository.save(doctor2);
        String paramIdDoctor2 = doctor2.getId().toString();

        // Доктор 3 (Берлин)
        Doctor doctor3 = new Doctor(null, "Archie", "Brown", "Europe/Berlin", null);
        doctorRepository.save(doctor3);
        String paramIdDoctor3 = doctor3.getId().toString();

        // Встреча 1: пациент1 с доктором1 (06:00 London time)
        String datetimeForParse1_1_1 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_1_1 = LocalDateTime.parse(datetimeForParse1_1_1, FORMATTER);
        ZonedDateTime startZoned1_1_1 = startLocal1_1_1.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_1 = startZoned1_1_1.toInstant();

        Visit visit1 = new Visit(null, startUtc1_1_1, startUtc1_1_1.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit1);

        // Встреча 2: пациент1 с доктором2 (06:00 New York time)
        String datetimeForParse1_2_2 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_2_2 = LocalDateTime.parse(datetimeForParse1_2_2, FORMATTER);
        ZonedDateTime startZoned1_2_2 = startLocal1_2_2.atZone(ZoneId.of("America/New_York"));
        Instant startUtc1_2_2 = startZoned1_2_2.toInstant();

        Visit visit2 = new Visit(null, startUtc1_2_2, startUtc1_2_2.plus(Duration.ofMinutes(30)), patient1, doctor2);
        visitRepository.save(visit2);

        // Встреча 3: пациент1 с доктором3 (06:00 Berlin time)
        String datetimeForParse1_3_3 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_3_3 = LocalDateTime.parse(datetimeForParse1_3_3, FORMATTER);
        ZonedDateTime startZoned1_3_3 = startLocal1_3_3.atZone(ZoneId.of("Europe/Berlin"));
        Instant startUtc1_3_3 = startZoned1_3_3.toInstant();

        Visit visit3 = new Visit(null, startUtc1_3_3, startUtc1_3_3.plus(Duration.ofMinutes(30)), patient1, doctor3);
        visitRepository.save(visit3);

        // Встреча 4: пациент1 с доктором1 (08:00 London time) – позже встречи 1
        String datetimeForParse1_1_4 = "2024-02-12 08:00:00";
        LocalDateTime startLocal1_1_4 = LocalDateTime.parse(datetimeForParse1_1_4, FORMATTER);
        ZonedDateTime startZoned1_1_4 = startLocal1_1_4.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_4 = startZoned1_1_4.toInstant();

        Visit visit4 = new Visit(null, startUtc1_1_4, startUtc1_1_4.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit4);

        // Ожидаемые значения в часовом поясе врачей
        String expectedStart1_1_4 = FORMATTER.format(startZoned1_1_4); // Последняя встреча пациента1 с doctor1
        String expectedStart1_2_2 = FORMATTER.format(startZoned1_2_2);
        String expectedStart1_3_3 = FORMATTER.format(startZoned1_3_3);

        // Запрос со всеми врачами
        mockMvc.perform(get("/api/visits")
                        .param("search", "Amelia")
                        .param("doctorIds", paramIdDoctor1 + "," + paramIdDoctor2 + "," + paramIdDoctor3) // Все три доктора
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Amelia"))
                // Проверяем последнюю встречу с doctor1 (John)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Arthur')].start")
                        .value(expectedStart1_1_4))
                // Проверяем последнюю встречу с doctor2 (Alice)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Ivy')].start")
                        .value(expectedStart1_2_2))
                // Проверяем последнюю встречу с doctor3 (Michael)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Archie')].start")
                        .value(expectedStart1_3_3));
    }

    @Test
    @Transactional
    @DisplayName("Тест: Получение списка пациентов с визитами:" +
            " 1  пациент - 5 встречи - 3 доктора(1,4в-1д  2,5в-2д 3в-3д)" +
            " 2 встреча не замещается 5-ой тех же персонажей, 5 раньше")
    void testGetPatientsWithLastVisits4() throws Exception {
        // Создаем пациента
        Patient patient1 = new Patient(null, "Isla", "Wilson");
        patient1 = patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Henry", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        String paramIdDoctor1 = doctor1.getId().toString();

        // Доктор 2 (Нью-Йорк)
        Doctor doctor2 = new Doctor(null, "Lily", "Smith", "America/New_York", null);
        doctorRepository.save(doctor2);
        String paramIdDoctor2 = doctor2.getId().toString();

        // Доктор 3 (Берлин)
        Doctor doctor3 = new Doctor(null, "Muhammad", "Brown", "Europe/Berlin", null);
        doctorRepository.save(doctor3);
        String paramIdDoctor3 = doctor3.getId().toString();

        // Встреча 1: пациент1 с доктором1 (06:00 London time)
        String datetimeForParse1_1_1 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_1_1 = LocalDateTime.parse(datetimeForParse1_1_1, FORMATTER);
        ZonedDateTime startZoned1_1_1 = startLocal1_1_1.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_1 = startZoned1_1_1.toInstant();
        Visit visit1 = new Visit(null, startUtc1_1_1, startUtc1_1_1.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit1);

        // Встреча 2: пациент1 с доктором2 (06:00 New York time) - позже встречи 5
        String datetimeForParse1_2_2 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_2_2 = LocalDateTime.parse(datetimeForParse1_2_2, FORMATTER);
        ZonedDateTime startZoned1_2_2 = startLocal1_2_2.atZone(ZoneId.of("America/New_York"));
        Instant startUtc1_2_2 = startZoned1_2_2.toInstant();
        Visit visit2 = new Visit(null, startUtc1_2_2, startUtc1_2_2.plus(Duration.ofMinutes(30)), patient1, doctor2);
        visitRepository.save(visit2);

        // Встреча 3: пациент1 с доктором3 (06:00 Berlin time)
        String datetimeForParse1_3_3 = "2024-02-12 06:00:00";
        LocalDateTime startLocal1_3_3 = LocalDateTime.parse(datetimeForParse1_3_3, FORMATTER);
        ZonedDateTime startZoned1_3_3 = startLocal1_3_3.atZone(ZoneId.of("Europe/Berlin"));
        Instant startUtc1_3_3 = startZoned1_3_3.toInstant();
        Visit visit3 = new Visit(null, startUtc1_3_3, startUtc1_3_3.plus(Duration.ofMinutes(30)), patient1, doctor3);
        visitRepository.save(visit3);

        // Встреча 4: пациент1 с доктором1 (08:00 London time) – позже встречи 1
        String datetimeForParse1_1_4 = "2024-02-12 08:00:00";
        LocalDateTime startLocal1_1_4 = LocalDateTime.parse(datetimeForParse1_1_4, FORMATTER);
        ZonedDateTime startZoned1_1_4 = startLocal1_1_4.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_4 = startZoned1_1_4.toInstant();
        Visit visit4 = new Visit(null, startUtc1_1_4, startUtc1_1_4.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit4);

        // Встреча 5: пациент1 с доктором2 (05:00 New York time) – раньше встречи 2
        String datetimeForParse1_2_5 = "2024-02-12 05:00:00";
        LocalDateTime startLocal1_2_5 = LocalDateTime.parse(datetimeForParse1_2_5, FORMATTER);
        ZonedDateTime startZoned1_2_5 = startLocal1_2_5.atZone(ZoneId.of("America/New_York"));
        Instant startUtc1_2_5 = startZoned1_2_5.toInstant();
        Visit visit5 = new Visit(null, startUtc1_2_5, startUtc1_2_5.plus(Duration.ofMinutes(30)), patient1, doctor2);
        visitRepository.save(visit5);

        // Ожидаемые значения в часовом поясе врачей
        String expectedStart1_1_4 = FORMATTER.format(startZoned1_1_4); // Последняя встреча пациента1 с doctor1
        String expectedStart1_2_2 = FORMATTER.format(startZoned1_2_2); // Последняя встреча пациента1 с doctor2
        String expectedStart1_3_3 = FORMATTER.format(startZoned1_3_3); // Последняя встреча пациента1 с doctor3

        // Запрос со всеми врачами
        mockMvc.perform(get("/api/visits")
                        .param("search", "Isla")
                        .param("doctorIds", paramIdDoctor1 + "," + paramIdDoctor2 + "," + paramIdDoctor3) // Все три доктора
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Isla"))
                // Проверяем последнюю встречу с doctor1 (John)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Henry')].start")
                        .value(expectedStart1_1_4))
                // Проверяем последнюю встречу с doctor2 (Alice)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Lily')].start")
                        .value(expectedStart1_2_2))
                // Проверяем последнюю встречу с doctor3 (Michael)
                .andExpect(jsonPath("$.data[0].lastVisits[?(@.doctor.firstName == 'Muhammad')].start")
                        .value(expectedStart1_3_3));
    }

    @Test
    @Transactional
    @DisplayName("Тест: Получение списка пациентов с визитами: 2 пациента и 4 врача ")
    void testGetPatientsWithLastVisits5() throws Exception {

        // Создаем пациента 1
        Patient patient1 = new Patient(null, "Sophia1", "Wilson");
        patient1 = patientRepository.save(patient1);

        // Создаем пациента 2
        Patient patient2 = new Patient(null, "Liam1", "Taylor");
        patient2 = patientRepository.save(patient2);

        // Создаем доктора 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "John", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        String paramIdDoctor1 = doctor1.getId().toString();

        // Создаем доктора 2 (Нью-Йорк)
        Doctor doctor2 = new Doctor(null, "Alice", "Smith", "America/New_York", null);
        doctorRepository.save(doctor2);
        String paramIdDoctor2 = doctor2.getId().toString();

        // Создаем доктора 3 (Берлин)
        Doctor doctor3 = new Doctor(null, "Michael", "Brown", "Europe/Berlin", null);
        doctorRepository.save(doctor3);
        String paramIdDoctor3 = doctor3.getId().toString();

        // Создаем доктора 4 (Париж)
        Doctor doctor4 = new Doctor(null, "Emma", "Johnson", "Europe/Paris", null);
        doctorRepository.save(doctor4);
        String paramIdDoctor4 = doctor4.getId().toString();
        // Встречи пациента 1
        Visit visit1_1 = createVisit(patient1, doctor1, "2024-02-12 06:00:00", "Europe/London");
        Visit visit1_2 = createVisit(patient1, doctor2, "2024-02-12 06:00:00", "America/New_York");
        Visit visit1_3 = createVisit(patient1, doctor3, "2024-02-12 06:00:00", "Europe/Berlin");
        Visit visit1_4 = createVisit(patient1, doctor1, "2024-02-12 08:00:00", "Europe/London");

        // Встречи пациента 2
        Visit visit2_1 = createVisit(patient2, doctor1, "2024-02-12 07:00:00", "Europe/London");
        Visit visit2_2 = createVisit(patient2, doctor2, "2024-02-12 08:00:00", "America/New_York");
        Visit visit2_3 = createVisit(patient2, doctor3, "2024-02-12 09:00:00", "Europe/Berlin");
        Visit visit2_4 = createVisit(patient2, doctor4, "2024-02-12 10:00:00", "Europe/Paris");

        // Сохранение всех встреч
        visitRepository.saveAll(List.of(visit1_1, visit1_2, visit1_3, visit1_4, visit2_1, visit2_2, visit2_3, visit2_4));

        // Ожидаемые значения в часовом поясе врачей
        String expectedStart1_1_4 = formatDateTime("2024-02-12 08:00:00", "Europe/London"); // Последняя встреча пациента1 с doctor1
        String expectedStart1_2_2 = formatDateTime("2024-02-12 06:00:00", "America/New_York"); // Последняя встреча пациента1 с doctor2
        String expectedStart1_3_3 = formatDateTime("2024-02-12 06:00:00", "Europe/Berlin"); // Последняя встреча пациента1 с doctor3
        String expectedStart2_1 = formatDateTime("2024-02-12 07:00:00", "Europe/London"); // Последняя встреча пациента2 с doctor1
        String expectedStart2_2 = formatDateTime("2024-02-12 08:00:00", "America/New_York"); // Последняя встреча пациента2 с doctor2
        String expectedStart2_3 = formatDateTime("2024-02-12 09:00:00", "Europe/Berlin"); // Последняя встреча пациента2 с doctor3
        String expectedStart2_4 = formatDateTime("2024-02-12 10:00:00", "Europe/Paris"); // Последняя встреча пациента2 с doctor4


        // Запрос с фильтром "1" (должны попасть Sophia1 и Liam1)
        mockMvc.perform(get("/api/visits")
                        .param("search", "1") // Фильтр по "1"
                        .param("doctorIds", paramIdDoctor1 + "," + paramIdDoctor2 + "," + paramIdDoctor3 + "," + paramIdDoctor4) // Все четыре доктора
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2)) // Два пациента
                // Проверяем первого пациента (Sophia1)
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'John')].start")
                        .value(expectedStart1_1_4))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'John')].doctor.totalPatients")
                        .value(2)) // Проверяем totalPatients
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'Alice')].start")
                        .value(expectedStart1_2_2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'Alice')].doctor.totalPatients")
                        .value(2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'Michael')].start")
                        .value(expectedStart1_3_3))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Sophia1')].lastVisits[?(@.doctor.firstName == 'Michael')].doctor.totalPatients")
                        .value(2))
                // Проверяем второго пациента (Liam1)
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'John')].start")
                        .value("2024-02-12 07:00:00"))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'John')].doctor.totalPatients")
                        .value(2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Alice')].start")
                        .value("2024-02-12 08:00:00"))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Alice')].doctor.totalPatients")
                        .value(2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Michael')].start")
                        .value("2024-02-12 09:00:00"))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Michael')].doctor.totalPatients")
                        .value(2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Emma')].start")
                        .value("2024-02-12 10:00:00"))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Emma')].doctor.totalPatients")
                        .value(1))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'John')].start")
                        .value(expectedStart2_1))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Alice')].start")
                        .value(expectedStart2_2))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Michael')].start")
                        .value(expectedStart2_3))
                .andExpect(jsonPath("$.data[?(@.firstName == 'Liam1')].lastVisits[?(@.doctor.firstName == 'Emma')].start")
                        .value(expectedStart2_4));

    }

    private Visit createVisit(Patient patient, Doctor doctor, String dateTime, String timeZone) {
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, FORMATTER);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of(timeZone));
        Instant startUtc = zonedDateTime.toInstant();
        return new Visit(null, startUtc, startUtc.plus(Duration.ofMinutes(30)), patient, doctor);
    }

    // Метод для форматирования даты в часовом поясе
    private String formatDateTime(String dateTime, String timeZone) {
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, FORMATTER);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of(timeZone));
        return FORMATTER.format(zonedDateTime);
    }


    @Test
    void shouldReturnBadRequestWhenStartTimeIsInvalid() throws Exception {
        Patient patient1 = new Patient(null, "Jira", "Wilson");
        patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Victor", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);

        VisitRequest request = new VisitRequest("invalid-date", "2025-06-15 12:00:00", patient1.getId(), doctor1.getId());

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid date format. Expected format: yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void shouldReturnBadRequestWhenEndTimeIsBeforeStartTime() throws Exception {
        Patient patient1 = new Patient(null, "Casper", "Wilson");
        patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Rahel", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);

        VisitRequest request = new VisitRequest("2025-06-15 12:00:00", "2025-06-15 11:00:00", patient1.getId(), doctor1.getId());

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start time must be before end time"));
    }

    @Test
    void shouldReturnBadRequestWhenDoctorIsAlreadyBooked() throws Exception {
        Patient patient1 = new Patient(null, "Willow", "Wilson");
        patient1 = patientRepository.save(patient1);

        // Доктор 1 (Лондон)
        Doctor doctor1 = new Doctor(null, "Noah", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);

        // Исходное время приема в часовом поясе доктора 1
        String datetimeForParse1_1_1 = "2024-02-12 06:00:00"; // Время в часовом поясе врача
        LocalDateTime startLocal1_1_1 = LocalDateTime.parse(datetimeForParse1_1_1, FORMATTER);
        ZonedDateTime startZoned1_1_1 = startLocal1_1_1.atZone(ZoneId.of("Europe/London"));
        Instant startUtc1_1_1 = startZoned1_1_1.toInstant();

        Visit visit1 = new Visit(null, startUtc1_1_1, startUtc1_1_1.plus(Duration.ofMinutes(30)), patient1, doctor1);
        visitRepository.save(visit1);

        // Добавляем запись с занятым временем
        VisitRequest request = new VisitRequest("2024-02-12 06:00:00", "2024-02-12 06:30:00", patient1.getId(), doctor1.getId());

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Doctor is already booked at this time"));
    }

    @Test
    void shouldReturnNotFoundWhenDoctorDoesNotExist() throws Exception {
        VisitRequest request = new VisitRequest("2025-06-15 10:00:00", "2025-06-15 11:00:00", 1, 9999);

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor with 9999 not found"));
    }

    @Test
    void shouldReturnNotFoundWhenPatientDoesNotExist() throws Exception {
        Doctor doctor1 = new Doctor(null, "Zoran", "Doe", "Europe/London", null);
        doctorRepository.save(doctor1);
        VisitRequest request = new VisitRequest("2025-06-15 10:00:00", "2025-06-15 11:00:00", 9999, doctor1.getId());

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient with 9999 not found"));
    }

    //--------------------------------------------------------------------
        @Test
        void shouldAllowNonOverlappingLaterVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 13:00:00", "2025-06-15 13:30:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldRejectOverlappingLaterVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 12:20:00", "2025-06-15 12:50:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Doctor is already booked at this time"));
        }

        @Test
        void shouldRejectFullyContainedVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 12:10:00", "2025-06-15 12:20:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Doctor is already booked at this time"));
        }

        @Test
        void shouldRejectOverlappingEarlierVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 11:50:00", "2025-06-15 12:10:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Doctor is already booked at this time"));
        }

        @Test
        void shouldAllowNonOverlappingEarlierVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 11:00:00", "2025-06-15 11:30:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldRejectFullyOverlappingVisit() throws Exception {
            setup();
            VisitRequest request = new VisitRequest("2025-06-15 11:50:00", "2025-06-15 12:40:00", patient.getId(), doctor.getId());

            mockMvc.perform(post("/api/visits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Doctor is already booked at this time"));
        }


}