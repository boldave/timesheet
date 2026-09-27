# Timesheet PoC

Logowanie godzin pracy w kalendarzu: zaznaczasz dzień i godziny, wybierasz projekt, zapisujesz.
Specyfikacja: `/home/dawid/projects/pomysly/ideas/timesheet-poc.md`.

## Wymagania

- Java 25, Node 24, działający Docker.

## Uruchomienie

```bash
# terminal 1 — backend na http://localhost:8081 (sam uruchamia PostgreSQL z compose.yaml)
cd backend && ./gradlew bootRun

# terminal 2 — frontend na http://localhost:5173
cd frontend && npm install && npm run dev
```

Otwórz http://localhost:5173.

Backend jest na porcie 8081, bo 8080 zajmuje lokalny kontener `gluetun`.
Dane trzyma wolumen Dockera `timesheet_timesheet-data`; `docker compose down -v` w katalogu głównym je kasuje.

## Testy

```bash
cd backend && ./gradlew test   # wymaga Dockera (Testcontainers)
cd frontend && npm run build   # sprawdzenie typów
```
