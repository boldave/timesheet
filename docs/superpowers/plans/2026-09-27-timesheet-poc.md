# Timesheet PoC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Działający PoC: w kalendarzu zaznaczam dzień i godziny, wybieram projekt, zapisuję — wpis zostaje w PostgreSQL po odświeżeniu strony i restarcie backendu.

**Architecture:** Jedno repozytorium, dwa katalogi. `backend/` to Spring Boot 4 z REST API (`/api/projects`, `/api/entries`), JPA i Liquibase na PostgreSQL uruchamianym automatycznie z `compose.yaml` przez `spring-boot-docker-compose`. `frontend/` to React + Vite + TypeScript z FullCalendar; serwer deweloperski Vite przekierowuje `/api` na backend. Reguły (godziny w jednym dniu, brak nakładek) żyją w backendzie; frontend tylko pokazuje komunikaty.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Gradle (Groovy DSL, wrapper ze start.spring.io), PostgreSQL 17, Liquibase (changelogi SQL), JUnit 5 + AssertJ + MockMvc + Testcontainers; Node 24, React 19, Vite 8, TypeScript 6, FullCalendar 6.1.21.

**Spec:** `/home/dawid/projects/pomysly/ideas/timesheet-poc.md`

## Global Constraints

- Katalog projektu: `/home/dawid/projects/pomysly/poc/timesheet` (repozytorium git, gałąź `main`). Wszystkie ścieżki niżej są względne do niego.
- Pakiet bazowy backendu: `pl.dawid.timesheet`.
- **Backend na porcie 8081** (`server.port=8081`) — port 8080 zajmuje lokalny kontener `gluetun`. Proxy Vite wskazuje `http://localhost:8081`.
- Frontend: `http://localhost:5173`.
- PostgreSQL: obraz `postgres:17` — ten sam w `compose.yaml` i w Testcontainers.
- FullCalendar przypięty do **6.1.21** (`--save-exact`); wersja 7 ma inne API.
- Czas lokalny bez strefy: `LocalDate` + `LocalTime` w backendzie; na froncie daty składane z pól lokalnych, **nigdy** przez `toISOString()` (przesuwa na UTC).
- Format w API: data `YYYY-MM-DD`, godziny `HH:mm`.
- UI i komunikaty błędów po polsku. Tekst z serwera wstawiany tylko jako tekst (React), nigdy jako HTML.
- Poza zakresem (nie dodawać): logowanie, zarządzanie projektami, przeciąganie/rozciąganie bloków, opisy wpisów, raporty, eksport, wpisy przez północ, multi-tenancy, agent AI.
- **Każdy task kończy się jednym commitem.** Komunikat po angielsku w stylu conventional commits (`feat:`, `chore:`, `docs:`). **Bez** linii `Co-Authored-By` i bez żadnej wzmianki o Claude.
- Testy backendu wymagają działającego Dockera (Testcontainers); uruchomienie `bootRun` też (docker compose).

## Review Focus

Frontend nie ma testów automatycznych (decyzja ze specyfikacji), więc te przypadki pokrywają kroki ręcznej weryfikacji w Task 7:

1. **Zaznaczenie w tygodniu aż do północy** (np. 22:00–24:00) — FullCalendar zwraca koniec jako 00:00 następnego dnia; okienko ma pokazać „Do: 23:59", a zapis ma się udać (nie 400). Task 7, krok 4 (punkt f).
2. **Backend wyłączony** — proxy Vite zwraca 502 bez JSON-a; UI ma pokazać „Nie można połączyć się z serwerem.", a nie pusty kalendarz albo surowy błąd. Task 7, krok 4 (punkt h).
3. **Wpis tuż po północy** (00:15–01:00) — przy przeliczaniu na UTC trafiłby na poprzedni dzień; ma się pokazać w tym dniu, w którym go zaznaczono. Task 7, krok 4 (punkt g).
4. **Kliknięcie dnia w widoku miesiąca** — zaznaczenie całodniowe; okienko ma otworzyć się z domyślnymi 09:00–17:00, a zaznaczenie kilku dni ma być zablokowane. Task 7, krok 4 (punkt e).
5. **Podwójne kliknięcie „Zapisz"** — przyciski blokują się na czas zapisu; nie powstają dwa wpisy (a gdyby powstał drugi, backend i tak odrzuci go jako nakładkę 409). Task 7, krok 4 (punkt b).

---

### Task 1: Szkielet backendu z PostgreSQL i Liquibase

**Files:**
- Create: `compose.yaml`
- Create: `backend/` (wygenerowany ze start.spring.io: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`, `.gitattributes`, `src/main/java/pl/dawid/timesheet/TimesheetApplication.java`, `src/test/java/pl/dawid/timesheet/TimesheetApplicationTests.java`, `src/test/java/pl/dawid/timesheet/TestTimesheetApplication.java`)
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/java/pl/dawid/timesheet/TestcontainersConfiguration.java`
- Create: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `backend/src/main/resources/db/changelog/schema/.gitkeep`

**Interfaces:**
- Produces: klasa testowa `pl.dawid.timesheet.TestcontainersConfiguration` (**public**), importowana przez testy API w podpakietach: `@Import(TestcontainersConfiguration.class)`. Katalog `db/changelog/schema/` — każdy plik `NNN-nazwa.sql` w nim jest automatycznie dołączany (`includeAll`) w kolejności nazw.

- [x] **Step 1: Wygeneruj projekt Spring Boot**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
curl -sS -o /tmp/timesheet-backend.zip "https://start.spring.io/starter.zip?type=gradle-project&language=java&bootVersion=4.1.1&groupId=pl.dawid&artifactId=timesheet&name=timesheet&packageName=pl.dawid.timesheet&javaVersion=25&dependencies=web,data-jpa,validation,liquibase,postgresql,docker-compose,testcontainers"
mkdir backend && unzip -q /tmp/timesheet-backend.zip -d backend && rm /tmp/timesheet-backend.zip
rm backend/HELP.md backend/compose.yaml
chmod +x backend/gradlew
```

Sprawdź, że `backend/build.gradle` zawiera m.in.: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-liquibase`, `spring-boot-starter-validation`, `developmentOnly 'org.springframework.boot:spring-boot-docker-compose'`, `runtimeOnly 'org.postgresql:postgresql'`, `spring-boot-testcontainers`, `testcontainers-postgresql`.

- [x] **Step 2: Utwórz `compose.yaml` w katalogu głównym repo**

```yaml
services:
  postgres:
    image: 'postgres:17'
    environment:
      - 'POSTGRES_DB=timesheet'
      - 'POSTGRES_PASSWORD=timesheet'
      - 'POSTGRES_USER=timesheet'
    ports:
      - '5432'
    volumes:
      - 'timesheet-data:/var/lib/postgresql/data'

volumes:
  timesheet-data:
```

Nazwany wolumen trzyma dane między restartami. Port hosta jest losowy — Spring Boot sam go odczytuje.

- [x] **Step 3: Zastąp `TestcontainersConfiguration` wersją publiczną z `postgres:17`**

`backend/src/test/java/pl/dawid/timesheet/TestcontainersConfiguration.java`:

```java
package pl.dawid.timesheet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
```

- [x] **Step 4: Uruchom test i potwierdź, że pada**

Run: `cd backend && ./gradlew test`
Expected: FAIL — `TimesheetApplicationTests > contextLoads()`, przyczyna w raporcie: `ChangeLogParseException: classpath:/db/changelog/db.changelog-master.yaml does not exist`.

- [x] **Step 5: Dodaj konfigurację aplikacji i changelog Liquibase**

`backend/src/main/resources/application.properties` (zastąp całą zawartość):

```properties
spring.application.name=timesheet
server.port=8081
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.docker.compose.file=../compose.yaml
```

`backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/schema/
```

Utwórz pusty plik `backend/src/main/resources/db/changelog/schema/.gitkeep`, żeby katalog trafił do repo.

- [x] **Step 6: Uruchom testy i potwierdź, że przechodzą**

Run: `cd backend && ./gradlew test`
Expected: PASS — `BUILD SUCCESSFUL`, 1 test (`contextLoads`).

- [x] **Step 7: Sprawdź start aplikacji z docker compose**

Run: `cd backend && ./gradlew bootRun`
Expected: w logu `Started TimesheetApplication` i `Tomcat started on port 8081`; `docker ps` pokazuje kontener `timesheet-postgres-1`. Zatrzymaj Ctrl+C.

- [x] **Step 8: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add compose.yaml backend
git status --short   # upewnij się, że nie ma backend/build ani backend/.gradle
git commit -m "chore: Spring Boot backend skeleton with PostgreSQL and Liquibase"
```

---

### Task 2: Projekty wpisane na sztywno i `GET /api/projects`

**Files:**
- Create: `backend/src/main/resources/db/changelog/schema/001-project.sql`
- Create: `backend/src/main/java/pl/dawid/timesheet/project/Project.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/project/ProjectRepository.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/project/ProjectController.java`
- Test: `backend/src/test/java/pl/dawid/timesheet/project/ProjectApiTest.java`

**Interfaces:**
- Consumes: `TestcontainersConfiguration` (Task 1).
- Produces: `public interface ProjectRepository extends JpaRepository<Project, Long>` (używane w Task 4 przez `existsById(Long)`). Projekty z seeda mają id 1 = „Basketo", 2 = „Hotelero", 3 = „Wewnętrzne" (tabela świeża, `generated always as identity`). JSON: `[{ "id": 1, "name": "Basketo", "color": "#2563eb" }, ...]` posortowane po `id`.

- [x] **Step 1: Napisz test, który pada**

`backend/src/test/java/pl/dawid/timesheet/project/ProjectApiTest.java`:

```java
package pl.dawid.timesheet.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import pl.dawid.timesheet.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsTheSeededProjectsInOrder() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Basketo"))
                .andExpect(jsonPath("$[0].color").value("#2563eb"))
                .andExpect(jsonPath("$[2].name").value("Wewnętrzne"));
    }
}
```

Uwaga: w Spring Boot 4 `AutoConfigureMockMvc` jest w pakiecie `org.springframework.boot.webmvc.test.autoconfigure`.

- [x] **Step 2: Uruchom test i potwierdź, że pada**

Run: `cd backend && ./gradlew test --tests '*ProjectApiTest'`
Expected: FAIL — `Status expected:<200> but was:<404>`.

- [x] **Step 3: Dodaj migrację z seedem**

`backend/src/main/resources/db/changelog/schema/001-project.sql`:

```sql
--liquibase formatted sql

--changeset timesheet:001-project
create table project (
    id    bigint generated always as identity primary key,
    name  varchar(100) not null unique,
    color varchar(7)   not null check (color ~ '^#[0-9a-fA-F]{6}$')
);

insert into project (name, color) values
    ('Basketo',    '#2563eb'),
    ('Hotelero',   '#16a34a'),
    ('Wewnętrzne', '#9333ea');
```

- [x] **Step 4: Dodaj encję, repozytorium i kontroler**

`backend/src/main/java/pl/dawid/timesheet/project/Project.java`:

```java
package pl.dawid.timesheet.project;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String color;

    protected Project() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
```

`backend/src/main/java/pl/dawid/timesheet/project/ProjectRepository.java`:

```java
package pl.dawid.timesheet.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
```

`backend/src/main/java/pl/dawid/timesheet/project/ProjectController.java`:

```java
package pl.dawid.timesheet.project;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectRepository projects;

    ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @GetMapping
    List<ProjectView> list() {
        return projects.findAll(Sort.by("id")).stream()
                .map(p -> new ProjectView(p.getId(), p.getName(), p.getColor()))
                .toList();
    }

    record ProjectView(Long id, String name, String color) {
    }
}
```

- [x] **Step 5: Uruchom wszystkie testy i potwierdź, że przechodzą**

Run: `cd backend && ./gradlew test`
Expected: PASS — 2 testy (`contextLoads`, `listsTheSeededProjectsInOrder`).

- [x] **Step 6: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add backend/src
git commit -m "feat: seeded projects and GET /api/projects"
```

---

### Task 3: `TimeSlot` — godziny w jednym dniu i reguła nakładania

**Files:**
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/TimeSlot.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/InvalidTimeSlotException.java`
- Test: `backend/src/test/java/pl/dawid/timesheet/entry/TimeSlotTest.java`

**Interfaces:**
- Produces:
  - `public record TimeSlot(LocalTime start, LocalTime end)` — konstruktor rzuca `InvalidTimeSlotException`, gdy któraś godzina jest `null` albo `end` nie jest po `start`; `public boolean overlaps(TimeSlot other)` — `true` tylko przy części wspólnej dłuższej niż zero (stykające się sloty nie nachodzą).
  - `public class InvalidTimeSlotException extends RuntimeException` z konstruktorem `(String message)`; komunikaty po polsku, trafiają do UI przez 400 (Task 4).

- [x] **Step 1: Napisz testy, które padają**

`backend/src/test/java/pl/dawid/timesheet/entry/TimeSlotTest.java`:

```java
package pl.dawid.timesheet.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class TimeSlotTest {

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> slot("13:00", "09:00"))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("późniejsza");
    }

    @Test
    void rejectsEmptySlot() {
        assertThatThrownBy(() -> slot("09:00", "09:00"))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void rejectsMissingTime() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.NOON))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void overlappingSlotsOverlap() {
        assertThat(slot("09:00", "13:00").overlaps(slot("12:00", "14:00"))).isTrue();
        assertThat(slot("12:00", "14:00").overlaps(slot("09:00", "13:00"))).isTrue();
    }

    @Test
    void slotInsideAnotherOverlaps() {
        assertThat(slot("09:00", "17:00").overlaps(slot("10:00", "11:00"))).isTrue();
        assertThat(slot("10:00", "11:00").overlaps(slot("09:00", "17:00"))).isTrue();
    }

    @Test
    void touchingSlotsDoNotOverlap() {
        assertThat(slot("09:00", "13:00").overlaps(slot("13:00", "14:00"))).isFalse();
        assertThat(slot("13:00", "14:00").overlaps(slot("09:00", "13:00"))).isFalse();
    }

    @Test
    void separateSlotsDoNotOverlap() {
        assertThat(slot("09:00", "10:00").overlaps(slot("15:00", "16:00"))).isFalse();
    }
}
```

- [x] **Step 2: Uruchom testy i potwierdź, że padają**

Run: `cd backend && ./gradlew test --tests '*TimeSlotTest'`
Expected: FAIL — błąd kompilacji `cannot find symbol: class TimeSlot`.

- [x] **Step 3: Zaimplementuj `TimeSlot` i wyjątek**

`backend/src/main/java/pl/dawid/timesheet/entry/InvalidTimeSlotException.java`:

```java
package pl.dawid.timesheet.entry;

public class InvalidTimeSlotException extends RuntimeException {

    public InvalidTimeSlotException(String message) {
        super(message);
    }
}
```

`backend/src/main/java/pl/dawid/timesheet/entry/TimeSlot.java`:

```java
package pl.dawid.timesheet.entry;

import java.time.LocalTime;

/** Godziny pracy w obrębie jednego dnia: od {@code start} (włącznie) do {@code end} (wyłącznie). */
public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new InvalidTimeSlotException("Podaj godzinę rozpoczęcia i zakończenia.");
        }
        if (!end.isAfter(start)) {
            throw new InvalidTimeSlotException("Godzina zakończenia musi być późniejsza niż rozpoczęcia.");
        }
    }

    /** Sloty stykające się (jeden kończy się o 13:00, drugi o 13:00 zaczyna) nie nachodzą na siebie. */
    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
```

- [x] **Step 4: Uruchom testy i potwierdź, że przechodzą**

Run: `cd backend && ./gradlew test --tests '*TimeSlotTest'`
Expected: PASS — 7 testów.

- [x] **Step 5: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add backend/src
git commit -m "feat: TimeSlot with same-day and overlap rules"
```

---

### Task 4: Wpisy — dodawanie i lista (`POST` / `GET /api/entries`)

**Files:**
- Create: `backend/src/main/resources/db/changelog/schema/002-time-entry.sql`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntry.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryRepository.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/NotFoundException.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/OverlapException.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryService.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryController.java`
- Create: `backend/src/main/java/pl/dawid/timesheet/web/ApiExceptionHandler.java`
- Test: `backend/src/test/java/pl/dawid/timesheet/entry/TimeEntryApiTest.java`

**Interfaces:**
- Consumes: `TimeSlot`, `InvalidTimeSlotException` (Task 3); `ProjectRepository.existsById(Long)` (Task 2); `TestcontainersConfiguration` (Task 1).
- Produces:
  - `TimeEntry(Long projectId, LocalDate workDate, TimeSlot slot)`, `void change(Long projectId, LocalDate workDate, TimeSlot slot)`, `TimeSlot slot()`, `Long getId()`, `Long getProjectId()`, `LocalDate getWorkDate()`.
  - `TimeEntryRepository`: `List<TimeEntry> findByWorkDate(LocalDate)`, `List<TimeEntry> findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(LocalDate from, LocalDate to)`.
  - `TimeEntryService`: `List<TimeEntry> list(LocalDate from, LocalDate to)`, `TimeEntry create(Long projectId, LocalDate date, TimeSlot slot)`; prywatne `requireProject(Long)`, `requireNoOverlap(LocalDate, TimeSlot, Long ignoredEntryId)` — Task 5 dopisze `update` i `delete`.
  - `NotFoundException(String)` → 404, `OverlapException(String)` → 409, `InvalidTimeSlotException` → 400, błędy formatu/walidacji → 400. Ciało błędu: `{"message": "..."}`.
  - JSON wpisu: `{"id": 1, "projectId": 1, "date": "2026-09-29", "start": "09:00", "end": "13:00"}`; ciało żądania bez `id`.

- [x] **Step 1: Napisz testy, które padają**

`backend/src/test/java/pl/dawid/timesheet/entry/TimeEntryApiTest.java`:

```java
package pl.dawid.timesheet.entry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import pl.dawid.timesheet.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryApiTest {

    /** Id projektów z seeda w 001-project.sql. */
    private static final long BASKETO = 1;
    private static final long HOTELERO = 2;

    @Autowired
    MockMvc mvc;

    @Autowired
    TimeEntryRepository entries;

    @BeforeEach
    void cleanUp() {
        entries.deleteAll();
    }

    private static String body(long projectId, String date, String start, String end) {
        return """
                {"projectId": %d, "date": "%s", "start": "%s", "end": "%s"}
                """.formatted(projectId, date, start, end);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/entries").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(String json) throws Exception {
        String response = create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void createdEntryIsListed() throws Exception {
        long id = createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));

        mvc.perform(get("/api/entries").param("from", "2026-09-28").param("to", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].projectId").value(BASKETO))
                .andExpect(jsonPath("$[0].date").value("2026-09-29"))
                .andExpect(jsonPath("$[0].start").value("09:00"))
                .andExpect(jsonPath("$[0].end").value("13:00"));
    }

    @Test
    void listReturnsOnlyDaysFromIncludedToExcluded() throws Exception {
        createdId(body(BASKETO, "2026-09-27", "09:00", "10:00"));
        createdId(body(BASKETO, "2026-09-28", "09:00", "10:00"));
        createdId(body(BASKETO, "2026-10-04", "09:00", "10:00"));
        createdId(body(BASKETO, "2026-10-05", "09:00", "10:00"));

        mvc.perform(get("/api/entries").param("from", "2026-09-28").param("to", "2026-10-05"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-09-28"))
                .andExpect(jsonPath("$[1].date").value("2026-10-04"));
    }

    @Test
    void overlappingEntryIsRefused() throws Exception {
        createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));

        create(body(HOTELERO, "2026-09-29", "12:00", "14:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Te godziny nachodzą na inny wpis tego dnia."));
    }

    @Test
    void touchingEntriesAndOtherDaysAreAllowed() throws Exception {
        createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));

        create(body(HOTELERO, "2026-09-29", "13:00", "14:00")).andExpect(status().isCreated());
        create(body(HOTELERO, "2026-09-30", "09:00", "13:00")).andExpect(status().isCreated());
    }

    @Test
    void unknownProjectIsNotFound() throws Exception {
        create(body(999, "2026-09-29", "09:00", "13:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Nie ma takiego projektu."));
    }

    @Test
    void endNotAfterStartIsBadRequest() throws Exception {
        create(body(BASKETO, "2026-09-29", "13:00", "09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Godzina zakończenia musi być późniejsza niż rozpoczęcia."));
        create(body(BASKETO, "2026-09-29", "09:00", "09:00")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedInputIsBadRequest() throws Exception {
        create(body(BASKETO, "2026-09-29", "9:00", "13:00")).andExpect(status().isBadRequest());
        create(body(BASKETO, "2026-09-29", "09:00", "24:00")).andExpect(status().isBadRequest());
        create(body(BASKETO, "2026-02-30", "09:00", "13:00")).andExpect(status().isBadRequest());
        create("""
                {"date": "2026-09-29", "start": "09:00", "end": "13:00"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        create("not json").andExpect(status().isBadRequest());
    }

    @Test
    void listNeedsAValidRange() throws Exception {
        mvc.perform(get("/api/entries").param("from", "2026-10-05").param("to", "2026-09-28"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/entries").param("from", "2026-09-28")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/entries").param("from", "wczoraj").param("to", "2026-09-28"))
                .andExpect(status().isBadRequest());
    }
}
```

- [x] **Step 2: Uruchom testy i potwierdź, że padają**

Run: `cd backend && ./gradlew test --tests '*TimeEntryApiTest'`
Expected: FAIL — błąd kompilacji `cannot find symbol: class TimeEntryRepository`.

- [x] **Step 3: Dodaj migrację tabeli wpisów**

`backend/src/main/resources/db/changelog/schema/002-time-entry.sql`:

```sql
--liquibase formatted sql

--changeset timesheet:002-time-entry
create table time_entry (
    id         bigint generated always as identity primary key,
    project_id bigint not null references project (id),
    work_date  date   not null,
    start_time time   not null,
    end_time   time   not null,
    check (end_time > start_time)
);

create index time_entry_work_date_idx on time_entry (work_date);
```

- [x] **Step 4: Dodaj encję, repozytorium i wyjątki**

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntry.java`:

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    protected TimeEntry() {
    }

    public TimeEntry(Long projectId, LocalDate workDate, TimeSlot slot) {
        change(projectId, workDate, slot);
    }

    public void change(Long projectId, LocalDate workDate, TimeSlot slot) {
        this.projectId = projectId;
        this.workDate = workDate;
        this.startTime = slot.start();
        this.endTime = slot.end();
    }

    public TimeSlot slot() {
        return new TimeSlot(startTime, endTime);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }
}
```

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryRepository.java`:

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByWorkDate(LocalDate workDate);

    /** Wpisy z dni w przedziale [from, to). */
    List<TimeEntry> findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(
            LocalDate from, LocalDate to);
}
```

`backend/src/main/java/pl/dawid/timesheet/entry/NotFoundException.java`:

```java
package pl.dawid.timesheet.entry;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
```

`backend/src/main/java/pl/dawid/timesheet/entry/OverlapException.java`:

```java
package pl.dawid.timesheet.entry;

public class OverlapException extends RuntimeException {

    public OverlapException(String message) {
        super(message);
    }
}
```

- [x] **Step 5: Dodaj serwis (dodawanie i lista)**

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryService.java`:

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.dawid.timesheet.project.ProjectRepository;

@Service
public class TimeEntryService {

    private final TimeEntryRepository entries;
    private final ProjectRepository projects;

    public TimeEntryService(TimeEntryRepository entries, ProjectRepository projects) {
        this.entries = entries;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> list(LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            throw new InvalidTimeSlotException("Data „od” musi być wcześniejsza niż data „do”.");
        }
        return entries.findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(from, to);
    }

    @Transactional
    public TimeEntry create(Long projectId, LocalDate date, TimeSlot slot) {
        requireProject(projectId);
        requireNoOverlap(date, slot, null);
        return entries.save(new TimeEntry(projectId, date, slot));
    }

    private void requireProject(Long projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Nie ma takiego projektu.");
        }
    }

    private void requireNoOverlap(LocalDate date, TimeSlot slot, Long ignoredEntryId) {
        boolean overlaps = entries.findByWorkDate(date).stream()
                .filter(other -> !other.getId().equals(ignoredEntryId))
                .anyMatch(other -> other.slot().overlaps(slot));
        if (overlaps) {
            throw new OverlapException("Te godziny nachodzą na inny wpis tego dnia.");
        }
    }
}
```

- [x] **Step 6: Dodaj kontroler i mapowanie błędów**

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryController.java`:

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entries")
class TimeEntryController {

    private final TimeEntryService service;

    TimeEntryController(TimeEntryService service) {
        this.service = service;
    }

    @GetMapping
    List<EntryView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to).stream().map(EntryView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EntryView create(@Valid @RequestBody EntryRequest request) {
        return EntryView.of(service.create(request.projectId(), request.date(), request.slot()));
    }

    record EntryRequest(
            @NotNull Long projectId,
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime start,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime end) {

        TimeSlot slot() {
            return new TimeSlot(start, end);
        }
    }

    record EntryView(
            Long id,
            Long projectId,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime start,
            @JsonFormat(pattern = "HH:mm") LocalTime end) {

        static EntryView of(TimeEntry entry) {
            TimeSlot slot = entry.slot();
            return new EntryView(entry.getId(), entry.getProjectId(), entry.getWorkDate(), slot.start(), slot.end());
        }
    }
}
```

Uwaga: Spring Boot 4 używa Jacksona 3, ale adnotacje (`JsonFormat`) nadal są w pakiecie `com.fasterxml.jackson.annotation`.

`backend/src/main/java/pl/dawid/timesheet/web/ApiExceptionHandler.java`:

```java
package pl.dawid.timesheet.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pl.dawid.timesheet.entry.InvalidTimeSlotException;
import pl.dawid.timesheet.entry.NotFoundException;
import pl.dawid.timesheet.entry.OverlapException;

@RestControllerAdvice
class ApiExceptionHandler {

    record ErrorBody(String message) {
    }

    @ExceptionHandler(InvalidTimeSlotException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorBody invalidSlot(InvalidTimeSlotException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorBody malformed(Exception e) {
        return new ErrorBody("Niepoprawne dane. Sprawdź projekt, datę i godziny (format HH:mm).");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorBody notFound(NotFoundException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(OverlapException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorBody overlap(OverlapException e) {
        return new ErrorBody(e.getMessage());
    }
}
```

- [x] **Step 7: Uruchom wszystkie testy i potwierdź, że przechodzą**

Run: `cd backend && ./gradlew test`
Expected: PASS — 17 testów (1 context + 1 projekty + 7 TimeSlot + 8 TimeEntryApi).

- [x] **Step 8: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add backend/src
git commit -m "feat: create and list time entries with overlap check"
```

---

### Task 5: Wpisy — zmiana i usuwanie (`PUT` / `DELETE /api/entries/{id}`)

**Files:**
- Modify: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryService.java` (cały plik niżej)
- Modify: `backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryController.java` (cały plik niżej)
- Test: `backend/src/test/java/pl/dawid/timesheet/entry/TimeEntryApiTest.java` (nowe importy i 4 testy)

**Interfaces:**
- Consumes: wszystko z Task 4.
- Produces: `TimeEntry update(Long id, Long projectId, LocalDate date, TimeSlot slot)`, `void delete(Long id)` w `TimeEntryService`; `PUT /api/entries/{id}` → 200 + wpis, `DELETE /api/entries/{id}` → 204; nieznany wpis → 404 „Nie ma takiego wpisu.". Frontend (Task 6–7) korzysta z pełnego API.

- [x] **Step 1: Dopisz testy, które padają**

W `TimeEntryApiTest.java` dodaj importy obok istniejących:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
```

i dopisz w klasie cztery testy:

```java
    @Test
    void entryGoesThroughItsWholeLife() throws Exception {
        long id = createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));

        mvc.perform(put("/api/entries/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(HOTELERO, "2026-09-30", "10:15", "12:45")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(HOTELERO))
                .andExpect(jsonPath("$.date").value("2026-09-30"))
                .andExpect(jsonPath("$.start").value("10:15"))
                .andExpect(jsonPath("$.end").value("12:45"));

        mvc.perform(get("/api/entries").param("from", "2026-09-28").param("to", "2026-10-05"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-09-30"));

        mvc.perform(delete("/api/entries/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/entries").param("from", "2026-09-28").param("to", "2026-10-05"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void entryMayBeChangedOverItsOwnHours() throws Exception {
        long id = createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));

        mvc.perform(put("/api/entries/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(BASKETO, "2026-09-29", "10:00", "14:00")))
                .andExpect(status().isOk());
    }

    @Test
    void changingAnEntryOntoAnotherIsRefused() throws Exception {
        createdId(body(BASKETO, "2026-09-29", "09:00", "13:00"));
        long second = createdId(body(BASKETO, "2026-09-29", "14:00", "15:00"));

        mvc.perform(put("/api/entries/{id}", second).contentType(MediaType.APPLICATION_JSON)
                        .content(body(BASKETO, "2026-09-29", "12:30", "15:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownEntryIsNotFound() throws Exception {
        mvc.perform(put("/api/entries/{id}", 999).contentType(MediaType.APPLICATION_JSON)
                        .content(body(BASKETO, "2026-09-29", "09:00", "13:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Nie ma takiego wpisu."));
        mvc.perform(delete("/api/entries/{id}", 999)).andExpect(status().isNotFound());
    }
```

- [x] **Step 2: Uruchom testy i potwierdź, że padają**

Run: `cd backend && ./gradlew test --tests '*TimeEntryApiTest'`
Expected: FAIL — 4 nowe testy padają (brak obsługi `PUT`/`DELETE`, więc zły status albo brak `$.message` w odpowiedzi); 8 testów z Task 4 przechodzi.

- [x] **Step 3: Dopisz `update` i `delete` w serwisie**

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryService.java` (cały plik):

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.dawid.timesheet.project.ProjectRepository;

@Service
public class TimeEntryService {

    private final TimeEntryRepository entries;
    private final ProjectRepository projects;

    public TimeEntryService(TimeEntryRepository entries, ProjectRepository projects) {
        this.entries = entries;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> list(LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            throw new InvalidTimeSlotException("Data „od” musi być wcześniejsza niż data „do”.");
        }
        return entries.findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(from, to);
    }

    @Transactional
    public TimeEntry create(Long projectId, LocalDate date, TimeSlot slot) {
        requireProject(projectId);
        requireNoOverlap(date, slot, null);
        return entries.save(new TimeEntry(projectId, date, slot));
    }

    @Transactional
    public TimeEntry update(Long id, Long projectId, LocalDate date, TimeSlot slot) {
        TimeEntry entry = entries.findById(id)
                .orElseThrow(() -> new NotFoundException("Nie ma takiego wpisu."));
        requireProject(projectId);
        requireNoOverlap(date, slot, id);
        entry.change(projectId, date, slot);
        return entry;
    }

    @Transactional
    public void delete(Long id) {
        TimeEntry entry = entries.findById(id)
                .orElseThrow(() -> new NotFoundException("Nie ma takiego wpisu."));
        entries.delete(entry);
    }

    private void requireProject(Long projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Nie ma takiego projektu.");
        }
    }

    private void requireNoOverlap(LocalDate date, TimeSlot slot, Long ignoredEntryId) {
        boolean overlaps = entries.findByWorkDate(date).stream()
                .filter(other -> !other.getId().equals(ignoredEntryId))
                .anyMatch(other -> other.slot().overlaps(slot));
        if (overlaps) {
            throw new OverlapException("Te godziny nachodzą na inny wpis tego dnia.");
        }
    }
}
```

- [x] **Step 4: Dopisz endpointy w kontrolerze**

`backend/src/main/java/pl/dawid/timesheet/entry/TimeEntryController.java` (cały plik):

```java
package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entries")
class TimeEntryController {

    private final TimeEntryService service;

    TimeEntryController(TimeEntryService service) {
        this.service = service;
    }

    @GetMapping
    List<EntryView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to).stream().map(EntryView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EntryView create(@Valid @RequestBody EntryRequest request) {
        return EntryView.of(service.create(request.projectId(), request.date(), request.slot()));
    }

    @PutMapping("/{id}")
    EntryView update(@PathVariable Long id, @Valid @RequestBody EntryRequest request) {
        return EntryView.of(service.update(id, request.projectId(), request.date(), request.slot()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    record EntryRequest(
            @NotNull Long projectId,
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime start,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime end) {

        TimeSlot slot() {
            return new TimeSlot(start, end);
        }
    }

    record EntryView(
            Long id,
            Long projectId,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime start,
            @JsonFormat(pattern = "HH:mm") LocalTime end) {

        static EntryView of(TimeEntry entry) {
            TimeSlot slot = entry.slot();
            return new EntryView(entry.getId(), entry.getProjectId(), entry.getWorkDate(), slot.start(), slot.end());
        }
    }
}
```

- [x] **Step 5: Uruchom wszystkie testy i potwierdź, że przechodzą**

Run: `cd backend && ./gradlew test`
Expected: PASS — 21 testów (1 context + 1 projekty + 7 TimeSlot + 12 TimeEntryApi).

- [x] **Step 6: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add backend/src
git commit -m "feat: update and delete time entries"
```

---

### Task 6: Frontend — kalendarz tygodniowy pokazujący wpisy z backendu

**Files:**
- Create: `frontend/` (szkielet `create-vite`, szablon `react-ts`)
- Delete: `frontend/public/`, `frontend/src/assets/`, `frontend/src/App.css`, `frontend/src/index.css`, `frontend/README.md`
- Modify: `frontend/package.json` (nazwa + FullCalendar), `frontend/index.html`, `frontend/vite.config.ts`, `frontend/src/main.tsx`, `frontend/src/App.tsx`
- Create: `frontend/src/api.ts`, `frontend/src/time.ts`, `frontend/src/Timesheet.tsx`, `frontend/src/styles.css`
- Create: `README.md` (katalog główny repo)

**Interfaces:**
- Consumes: API z Task 2–5 przez proxy `/api` → `http://localhost:8081`.
- Produces (Task 7 z tego korzysta):
  - `api.ts`: typy `Project { id: number; name: string; color: string }`, `Entry { id: number; projectId: number; date: string; start: string; end: string }`, `EntryInput = Omit<Entry, 'id'>`; obiekt `api` z metodami `projects()`, `entries(from, to)`, `createEntry(input)`, `updateEntry(id, input)`, `deleteEntry(id)` — każda rzuca `Error` z komunikatem gotowym do pokazania.
  - `time.ts`: `toIsoDate(d: Date): string`, `toHHmm(d: Date): string`, `withinOneDay(start: Date, end: Date): boolean`.
  - `Timesheet.tsx`: `export function Timesheet({ projects }: { projects: Project[] })`.

- [ ] **Step 1: Wygeneruj szkielet Vite i usuń demo**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
npm create vite@latest frontend -- --template react-ts --no-interactive
rm -rf frontend/public frontend/src/assets frontend/src/App.css frontend/src/index.css frontend/README.md
cd frontend
npm pkg set name=timesheet-frontend
npm install
npm install --save-exact @fullcalendar/core@6.1.21 @fullcalendar/react@6.1.21 @fullcalendar/daygrid@6.1.21 @fullcalendar/timegrid@6.1.21 @fullcalendar/interaction@6.1.21
```

- [ ] **Step 2: Ustaw HTML, proxy i punkt wejścia**

`frontend/index.html` (cały plik):

```html
<!doctype html>
<html lang="pl">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Timesheet</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

`frontend/vite.config.ts` (cały plik):

```ts
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
```

`frontend/src/main.tsx` (cały plik):

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- [ ] **Step 3: Dodaj klienta API i pomocnicze funkcje czasu**

`frontend/src/api.ts`:

```ts
export type Project = { id: number; name: string; color: string }

/** Wpis czasu pracy; `date` jako YYYY-MM-DD, `start`/`end` jako HH:mm. */
export type Entry = { id: number; projectId: number; date: string; start: string; end: string }

export type EntryInput = Omit<Entry, 'id'>

const CONNECTION_ERROR = 'Nie można połączyć się z serwerem.'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json' } })
  } catch {
    throw new Error(CONNECTION_ERROR)
  }
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string' && body.message) {
      return body.message
    }
  } catch {
    // brak JSON-a w odpowiedzi, np. gdy serwer deweloperski Vite nie może dosięgnąć backendu
  }
  return response.status >= 500 ? CONNECTION_ERROR : `Błąd (${response.status}).`
}

export const api = {
  projects: () => request<Project[]>('/api/projects'),
  entries: (from: string, to: string) =>
    request<Entry[]>(`/api/entries?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createEntry: (input: EntryInput) =>
    request<Entry>('/api/entries', { method: 'POST', body: JSON.stringify(input) }),
  updateEntry: (id: number, input: EntryInput) =>
    request<Entry>(`/api/entries/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  deleteEntry: (id: number) => request<void>(`/api/entries/${id}`, { method: 'DELETE' }),
}
```

`frontend/src/time.ts`:

```ts
const pad = (n: number) => String(n).padStart(2, '0')

/** Data lokalna jako YYYY-MM-DD (bez przeliczania na UTC). */
export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Godzina lokalna jako HH:mm. */
export function toHHmm(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Czy zaznaczenie [start, end) mieści się w jednym dniu kalendarzowym. */
export function withinOneDay(start: Date, end: Date): boolean {
  return toIsoDate(start) === toIsoDate(new Date(end.getTime() - 1))
}
```

- [ ] **Step 4: Dodaj kalendarz (na razie tylko wyświetlanie) i `App`**

`frontend/src/Timesheet.tsx`:

```tsx
import { useCallback, useMemo, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import plLocale from '@fullcalendar/core/locales/pl'
import type { EventInput, EventSourceFuncArg } from '@fullcalendar/core'
import { api, type Project } from './api'
import { toIsoDate } from './time'

export function Timesheet({ projects }: { projects: Project[] }) {
  const [error, setError] = useState<string | null>(null)

  const projectById = useMemo(() => new Map(projects.map((p) => [p.id, p])), [projects])

  // Stabilna referencja: nowa funkcja przy każdym renderze kazałaby FullCalendar pobierać wpisy od nowa.
  const loadEvents = useCallback(
    (info: EventSourceFuncArg, success: (events: EventInput[]) => void, failure: (error: Error) => void) => {
      api.entries(toIsoDate(info.start), toIsoDate(info.end)).then(
        (entries) => {
          setError(null)
          success(
            entries.map((entry) => {
              const project = projectById.get(entry.projectId)
              return {
                id: String(entry.id),
                title: project?.name ?? `Projekt ${entry.projectId}`,
                start: `${entry.date}T${entry.start}`,
                end: `${entry.date}T${entry.end}`,
                backgroundColor: project?.color,
                borderColor: project?.color,
                extendedProps: { entry },
              }
            }),
          )
        },
        (e: Error) => {
          setError(e.message)
          failure(e)
        },
      )
    },
    [projectById],
  )

  return (
    <>
      {error && <p className="error">{error}</p>}
      <FullCalendar
        plugins={[timeGridPlugin, dayGridPlugin, interactionPlugin]}
        locale={plLocale}
        initialView="timeGridWeek"
        headerToolbar={{ left: 'prev,next today', center: 'title', right: 'timeGridWeek,dayGridMonth' }}
        firstDay={1}
        allDaySlot={false}
        slotDuration="00:15:00"
        slotLabelInterval="01:00"
        slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        scrollTime="08:00:00"
        height="auto"
        events={loadEvents}
      />
    </>
  )
}
```

`frontend/src/App.tsx` (cały plik):

```tsx
import { useEffect, useState } from 'react'
import { api, type Project } from './api'
import { Timesheet } from './Timesheet'

export default function App() {
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.projects().then(setProjects, (e: Error) => setError(e.message))
  }, [])

  return (
    <div className="page">
      <h1>Timesheet</h1>
      {error && <p className="error">{error}</p>}
      {!projects && !error && <p>Ładowanie…</p>}
      {projects && projects.length === 0 && <p className="error">Brak projektów w bazie.</p>}
      {projects && projects.length > 0 && <Timesheet projects={projects} />}
    </div>
  )
}
```

`frontend/src/styles.css`:

```css
:root {
  font-family: system-ui, sans-serif;
  color: #1f2937;
  background: #f9fafb;
}

body {
  margin: 0;
}

.page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

h1 {
  margin: 0 0 12px;
  font-size: 1.5rem;
}

.error {
  color: #b91c1c;
}
```

- [ ] **Step 5: Sprawdź typy i build**

Run: `cd frontend && npm run build`
Expected: `tsc -b` bez błędów, `✓ built in ...`.

- [ ] **Step 6: Dodaj README z instrukcją uruchomienia**

`README.md` (katalog główny repo):

````markdown
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
````

- [ ] **Step 7: Sprawdź ręcznie wyświetlanie wpisu**

1. Terminal 1: `cd backend && ./gradlew bootRun` (czekaj na `Started TimesheetApplication`).
2. Terminal 2: `curl -s -XPOST localhost:8081/api/entries -H 'Content-Type: application/json' -d '{"projectId":1,"date":"2026-09-29","start":"09:00","end":"13:00"}'`
   Expected: `{"id":...,"projectId":1,"date":"2026-09-29","start":"09:00","end":"13:00"}` (albo 409, jeśli wpis już jest — też OK).
3. Terminal 2: `cd frontend && npm run dev`, otwórz http://localhost:5173, przejdź do tygodnia 28.09–4.10.2026.
   Expected: polskie nazwy dni, tydzień od poniedziałku, godziny 24h; we wtorek 29.09 niebieski blok „Basketo" 09:00–13:00.
4. Zatrzymaj oba procesy (Ctrl+C).

- [ ] **Step 8: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add README.md frontend
git status --short   # upewnij się, że nie ma frontend/node_modules ani frontend/dist
git commit -m "feat: weekly calendar showing time entries"
```

---

### Task 7: Frontend — dodawanie, edycja i usuwanie wpisów w okienku

**Files:**
- Create: `frontend/src/EntryDialog.tsx`
- Modify: `frontend/src/Timesheet.tsx` (cały plik niżej)
- Modify: `frontend/src/styles.css` (dopisanie stylów okienka i przycisków)

**Interfaces:**
- Consumes: `api`, `Entry`, `EntryInput`, `Project` (`api.ts`), `toIsoDate`, `toHHmm`, `withinOneDay` (`time.ts`) — Task 6.
- Produces: `EntryDialog` z propsami `{ projects: Project[]; initial: EntryInput; onDelete?: () => Promise<void>; onSave: (input: EntryInput) => Promise<void>; onClose: () => void }`. Jeśli `onSave`/`onDelete` rzuci błąd, okienko pokazuje jego komunikat i zostaje otwarte.

- [ ] **Step 1: Dodaj okienko wpisu**

`frontend/src/EntryDialog.tsx`:

```tsx
import { useState, type FormEvent } from 'react'
import type { EntryInput, Project } from './api'

type Props = {
  projects: Project[]
  initial: EntryInput
  /** Obecny przy edycji istniejącego wpisu; włącza przycisk „Usuń”. */
  onDelete?: () => Promise<void>
  onSave: (input: EntryInput) => Promise<void>
  onClose: () => void
}

export function EntryDialog({ projects, initial, onDelete, onSave, onClose }: Props) {
  const [projectId, setProjectId] = useState(initial.projectId)
  const [start, setStart] = useState(initial.start)
  const [end, setEnd] = useState(initial.end)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void run(() => onSave({ projectId, date: initial.date, start, end }))
  }

  return (
    <div className="backdrop">
      <form className="dialog" onSubmit={submit}>
        <h2>{onDelete ? 'Edytuj wpis' : 'Nowy wpis'} — {initial.date}</h2>
        <label>
          Projekt
          <select value={projectId} onChange={(e) => setProjectId(Number(e.target.value))}>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <div className="row">
          <label>
            Od
            <input type="time" required value={start} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label>
            Do
            <input type="time" required value={end} onChange={(e) => setEnd(e.target.value)} />
          </label>
        </div>
        {error && <p className="error">{error}</p>}
        <div className="actions">
          {onDelete && (
            <button type="button" className="danger" disabled={busy} onClick={() => void run(onDelete)}>
              Usuń
            </button>
          )}
          <span className="spacer" />
          <button type="button" disabled={busy} onClick={onClose}>
            Anuluj
          </button>
          <button type="submit" className="primary" disabled={busy}>
            Zapisz
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 2: Podłącz zaznaczanie i klikanie w kalendarzu**

`frontend/src/Timesheet.tsx` (cały plik):

```tsx
import { useCallback, useMemo, useRef, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import plLocale from '@fullcalendar/core/locales/pl'
import type { DateSelectArg, EventClickArg, EventInput, EventSourceFuncArg } from '@fullcalendar/core'
import { api, type Entry, type EntryInput, type Project } from './api'
import { EntryDialog } from './EntryDialog'
import { toHHmm, toIsoDate, withinOneDay } from './time'

type Editing = { entryId?: number; initial: EntryInput }

export function Timesheet({ projects }: { projects: Project[] }) {
  const calendarRef = useRef<FullCalendar>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)

  const projectById = useMemo(() => new Map(projects.map((p) => [p.id, p])), [projects])

  // Stabilna referencja: nowa funkcja przy każdym renderze kazałaby FullCalendar pobierać wpisy od nowa.
  const loadEvents = useCallback(
    (info: EventSourceFuncArg, success: (events: EventInput[]) => void, failure: (error: Error) => void) => {
      api.entries(toIsoDate(info.start), toIsoDate(info.end)).then(
        (entries) => {
          setError(null)
          success(
            entries.map((entry) => {
              const project = projectById.get(entry.projectId)
              return {
                id: String(entry.id),
                title: project?.name ?? `Projekt ${entry.projectId}`,
                start: `${entry.date}T${entry.start}`,
                end: `${entry.date}T${entry.end}`,
                backgroundColor: project?.color,
                borderColor: project?.color,
                extendedProps: { entry },
              }
            }),
          )
        },
        (e: Error) => {
          setError(e.message)
          failure(e)
        },
      )
    },
    [projectById],
  )

  function onSelect(sel: DateSelectArg) {
    const date = toIsoDate(sel.start)
    const start = sel.allDay ? '09:00' : toHHmm(sel.start)
    // Zaznaczenie do północy kończy się o 00:00 następnego dnia; wpis musi zmieścić się w jednym dniu.
    const end = sel.allDay ? '17:00' : toIsoDate(sel.end) === date ? toHHmm(sel.end) : '23:59'
    setEditing({ initial: { projectId: projects[0].id, date, start, end } })
  }

  function onEventClick(click: EventClickArg) {
    const entry = click.event.extendedProps.entry as Entry
    setEditing({
      entryId: entry.id,
      initial: { projectId: entry.projectId, date: entry.date, start: entry.start, end: entry.end },
    })
  }

  function close() {
    setEditing(null)
    calendarRef.current?.getApi().unselect()
  }

  function afterChange() {
    close()
    calendarRef.current?.getApi().refetchEvents()
  }

  const entryId = editing?.entryId

  return (
    <>
      {error && <p className="error">{error}</p>}
      <FullCalendar
        ref={calendarRef}
        plugins={[timeGridPlugin, dayGridPlugin, interactionPlugin]}
        locale={plLocale}
        initialView="timeGridWeek"
        headerToolbar={{ left: 'prev,next today', center: 'title', right: 'timeGridWeek,dayGridMonth' }}
        firstDay={1}
        allDaySlot={false}
        slotDuration="00:15:00"
        slotLabelInterval="01:00"
        slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        scrollTime="08:00:00"
        height="auto"
        selectable
        selectMirror
        selectAllow={(span) => withinOneDay(span.start, span.end)}
        select={onSelect}
        eventClick={onEventClick}
        events={loadEvents}
      />
      {editing && (
        <EntryDialog
          projects={projects}
          initial={editing.initial}
          onClose={close}
          onSave={async (input) => {
            if (entryId === undefined) await api.createEntry(input)
            else await api.updateEntry(entryId, input)
            afterChange()
          }}
          onDelete={
            entryId === undefined
              ? undefined
              : async () => {
                  await api.deleteEntry(entryId)
                  afterChange()
                }
          }
        />
      )}
    </>
  )
}
```

Dopisz na końcu `frontend/src/styles.css`:

```css
.fc-timegrid-event,
.fc-daygrid-event {
  cursor: pointer;
}

.backdrop {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.dialog {
  background: white;
  border-radius: 8px;
  padding: 20px;
  width: min(360px, calc(100vw - 32px));
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 10px 30px rgb(0 0 0 / 0.2);
}

.dialog h2 {
  margin: 0;
  font-size: 1.1rem;
}

.dialog label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.9rem;
  flex: 1;
}

.dialog select,
.dialog input {
  font: inherit;
  padding: 6px 8px;
}

.row {
  display: flex;
  gap: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}

.spacer {
  flex: 1;
}

button {
  font: inherit;
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: white;
  cursor: pointer;
}

button.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: white;
}

button.danger {
  color: #b91c1c;
  border-color: #fca5a5;
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}
```

- [ ] **Step 3: Sprawdź typy i build**

Run: `cd frontend && npm run build`
Expected: `tsc -b` bez błędów, `✓ built in ...`.

- [ ] **Step 4: Weryfikacja ręczna (kryterium ukończenia ze specyfikacji + Review Focus)**

Uruchom backend (`cd backend && ./gradlew bootRun`) i frontend (`cd frontend && npm run dev`), otwórz http://localhost:5173.

a. **Dodanie:** zaznacz myszką wtorek 9:00–13:00 → okienko „Nowy wpis — RRRR-MM-DD", projekt „Basketo" wybrany, Od 09:00, Do 13:00 → „Zapisz". Expected: okienko się zamyka, niebieski blok „Basketo" 09:00–13:00.
b. **Podwójne kliknięcie:** zaznacz środę 10:00–11:00, w okienku kliknij „Zapisz" dwa razy szybko. Expected: przyciski szarzeją na czas zapisu, powstaje jeden blok.
c. **Edycja:** kliknij blok z punktu a → „Edytuj wpis", zmień projekt na „Hotelero" i Do na 12:00 → „Zapisz". Expected: blok zielony „Hotelero" 09:00–12:00.
d. **Nakładka:** zaznacz ten sam wtorek 11:00–14:00 → „Zapisz". Expected: czerwony komunikat „Te godziny nachodzą na inny wpis tego dnia." w okienku, okienko otwarte; „Anuluj" zamyka bez zmian. Zaznaczenie 12:00–14:00 (styka się z 12:00) zapisuje się poprawnie.
e. **Widok miesiąca:** przełącz na „Miesiąc", kliknij jeden dzień → okienko z Od 09:00, Do 17:00; „Anuluj". Spróbuj przeciągnąć zaznaczenie przez kilka dni. Expected: zaznaczenie kilku dni się nie da. Wróć do „Tydzień".
f. **Do północy:** zaznacz dowolny dzień 22:00 do samego dołu siatki (24:00). Expected: okienko pokazuje Do 23:59; „Zapisz" tworzy blok 22:00–23:59.
g. **Po północy:** zaznacz czwartek 00:15–01:00 → „Zapisz". Expected: blok w czwartek (nie w środę) 00:15–01:00.
h. **Backend wyłączony:** zatrzymaj backend (Ctrl+C), kliknij „następny tydzień" w kalendarzu. Expected: czerwony komunikat „Nie można połączyć się z serwerem.". Uruchom backend ponownie, kliknij „dzisiaj" — komunikat znika.
i. **Trwałość:** odśwież stronę (F5) i zrestartuj backend. Expected: wszystkie zapisane bloki nadal są.
j. **Usuwanie:** kliknij dowolny blok → „Usuń". Expected: blok znika.

- [ ] **Step 5: Uruchom testy backendu na koniec**

Run: `cd backend && ./gradlew test`
Expected: PASS — 21 testów.

- [ ] **Step 6: Commit**

```bash
cd /home/dawid/projects/pomysly/poc/timesheet
git add frontend/src
git commit -m "feat: add, edit and delete entries from the calendar"
```
