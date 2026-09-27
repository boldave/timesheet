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
