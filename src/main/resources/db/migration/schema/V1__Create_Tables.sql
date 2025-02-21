CREATE TABLE doctors (
                         id INT AUTO_INCREMENT PRIMARY KEY,
                         first_name VARCHAR(100) NOT NULL,
                         last_name VARCHAR(100) NOT NULL,
                         timezone VARCHAR(50) NOT NULL
);

CREATE TABLE patients (
                          id INT AUTO_INCREMENT PRIMARY KEY,
                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL
);

CREATE TABLE visits (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        start_date_time DATETIME NOT NULL,
                        end_date_time DATETIME NOT NULL,
                        patient_id INT NOT NULL,
                        doctor_id INT NOT NULL,
                        CONSTRAINT fk_visit_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
                        CONSTRAINT fk_visit_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
                        CONSTRAINT chk_visit_time CHECK (start_date_time < end_date_time)
);
