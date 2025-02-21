# VisitTracking

##  Запуск проекта

1. **Импортируй проект** в свою IDE.
2. **Запусти контейнер MySQL** в терминале:
   ```sh
   docker-compose up -d
   ```
   
3. Запусти тесты в Maven.
4. Запусти приложение – оно будет доступно на порту 8080.

5. Тестовые запросы для Postman:

### Получение списка посещений

GET http://localhost:8080/api/visits?search=Sophia1&doctorIds=1,2,3&page=0&size=10

### Добавление встречи
```
POST http://localhost:8080/api/visits
```
 - Content-Type: application/json

````
{
    "start": "2024-02-14 01:00:00",
    "end": "2024-02-14 02:30:00",
    "patientId": 5,
    "doctorId": 4
}
````