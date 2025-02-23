CREATE INDEX idx_visit_patient_doctor_start ON visits (patient_id, doctor_id, start_date_time DESC);
