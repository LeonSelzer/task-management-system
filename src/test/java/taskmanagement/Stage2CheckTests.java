package taskmanagement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prüf-Tests für Stufe 2 (Fälligkeitsdatum und Priorität).
 * Überfällige Tasks werden erzeugt, indem das Datum direkt über das Repository
 * in die Vergangenheit gesetzt wird (über die API ist das ja verboten).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stage2;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.key-dir=build/test-keys"
})
@AutoConfigureMockMvc
class Stage2CheckTests {

    private static final String PW = "secret123";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mvc;
    @Autowired private TaskRepository taskRepository;

    // ---------- Helfer ----------

    private String user() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@mail.com";
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isOk());
        return email;
    }

    private ResultActions create(String owner, String extraJson) throws Exception {
        String json = "{\"title\":\"t\",\"description\":\"d\"" + (extraJson.isEmpty() ? "" : "," + extraJson) + "}";
        return mvc.perform(post("/api/tasks").with(httpBasic(owner, PW)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String task(String owner, String extraJson) throws Exception {
        String body = create(owner, extraJson).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
    }

    private ResultActions patchTask(String owner, String id, String json) throws Exception {
        return mvc.perform(patch("/api/tasks/" + id).with(httpBasic(owner, PW)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void setDueDateInDb(String id, LocalDate date) {
        AppTask t = taskRepository.findById(Long.valueOf(id)).orElseThrow();
        t.setDueDate(date);
        taskRepository.saveAndFlush(t);
    }

    private void setStatus(String owner, String id, String status) throws Exception {
        mvc.perform(put("/api/tasks/" + id + "/status").with(httpBasic(owner, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }

    private ResultActions list(String user, String... params) throws Exception {
        var req = get("/api/tasks").with(httpBasic(user, PW));
        for (int i = 0; i < params.length; i += 2) req = req.param(params[i], params[i + 1]);
        return mvc.perform(req);
    }

    // ---------- 1+2. Anlegen ----------

    @Test @DisplayName("2.1 POST ohne neue Felder: priority MEDIUM, due_date null, overdue false")
    void createDefaults() throws Exception {
        String o = user();
        create(o, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.due_date").value(nullValue()))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test @DisplayName("2.2 POST mit due_date und priority")
    void createWithFields() throws Exception {
        String o = user();
        String d = TODAY.plusDays(10).toString();
        create(o, "\"due_date\":\"" + d + "\",\"priority\":\"HIGH\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.due_date").value(d))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test @DisplayName("2.3 POST due_date heute ist erlaubt")
    void createToday() throws Exception {
        String o = user();
        create(o, "\"due_date\":\"" + TODAY + "\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test @DisplayName("2.4 POST due_date gestern -> 400")
    void createYesterday() throws Exception {
        String o = user();
        create(o, "\"due_date\":\"" + TODAY.minusDays(1) + "\"").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("2.5 POST ungültiges Datum -> 400")
    void createInvalidDate() throws Exception {
        String o = user();
        create(o, "\"due_date\":\"24.12.2026\"").andExpect(status().isBadRequest());
        create(o, "\"due_date\":\"2026-13-01\"").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("2.6 POST ungültige Priorität -> 400")
    void createInvalidPriority() throws Exception {
        String o = user();
        create(o, "\"priority\":\"URGENT\"").andExpect(status().isBadRequest());
    }

    // ---------- 3. PATCH ----------

    @Test @DisplayName("3.1 PATCH nur due_date (gilt als Änderung)")
    void patchDueDateOnly() throws Exception {
        String o = user(); String id = task(o, "");
        String d = TODAY.plusDays(3).toString();
        patchTask(o, id, "{\"due_date\":\"" + d + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.due_date").value(d))
                .andExpect(jsonPath("$.title").value("t"));
    }

    @Test @DisplayName("3.2 PATCH nur priority")
    void patchPriorityOnly() throws Exception {
        String o = user(); String id = task(o, "");
        patchTask(o, id, "{\"priority\":\"LOW\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test @DisplayName("3.3 PATCH due_date gestern -> 400")
    void patchYesterday() throws Exception {
        String o = user(); String id = task(o, "");
        patchTask(o, id, "{\"due_date\":\"" + TODAY.minusDays(1) + "\"}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("3.4 PATCH ungültige Priorität / ungültiges Datum -> 400")
    void patchInvalid() throws Exception {
        String o = user(); String id = task(o, "");
        patchTask(o, id, "{\"priority\":\"URGENT\"}").andExpect(status().isBadRequest());
        patchTask(o, id, "{\"due_date\":\"morgen\"}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("3.5 PATCH ohne Felder weiterhin 400, fremder Nutzer weiterhin 403")
    void patchRegression() throws Exception {
        String o = user(); String other = user(); String id = task(o, "");
        patchTask(o, id, "{}").andExpect(status().isBadRequest());
        patchTask(other, id, "{\"priority\":\"LOW\"}").andExpect(status().isForbidden());
    }

    // ---------- 4. overdue in der Antwort ----------

    @Test @DisplayName("4.1 overdue true bei Datum in der Vergangenheit und nicht COMPLETED")
    void overdueTrue() throws Exception {
        String o = user(); String id = task(o, "");
        setDueDateInDb(id, TODAY.minusDays(2));
        list(o, "author", o).andExpect(jsonPath("$[0].overdue").value(true));
        setStatus(o, id, "IN_PROGRESS");
        list(o, "author", o).andExpect(jsonPath("$[0].overdue").value(true));
    }

    @Test @DisplayName("4.2 overdue false, wenn COMPLETED")
    void overdueCompleted() throws Exception {
        String o = user(); String id = task(o, "");
        setDueDateInDb(id, TODAY.minusDays(2));
        setStatus(o, id, "COMPLETED");
        list(o, "author", o).andExpect(jsonPath("$[0].overdue").value(false));
    }

    @Test @DisplayName("4.3 Liste mit Tasks ohne Datum funktioniert (kein 500)")
    void listWithoutDates() throws Exception {
        String o = user(); task(o, ""); task(o, "");
        list(o, "author", o).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].overdue", everyItem(is(false))));
    }

    // ---------- 5. Filter ----------

    @Test @DisplayName("5.1 ?priority=HIGH filtert")
    void filterPriority() throws Exception {
        String o = user();
        String high = task(o, "\"priority\":\"HIGH\"");
        task(o, "\"priority\":\"LOW\"");
        task(o, "");
        list(o, "author", o, "priority", "HIGH").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(high));
    }

    @Test @DisplayName("5.2 ?priority=MEDIUM findet auch Tasks ohne explizite Priorität")
    void filterPriorityDefault() throws Exception {
        String o = user();
        task(o, ""); task(o, "\"priority\":\"MEDIUM\""); task(o, "\"priority\":\"HIGH\"");
        list(o, "author", o, "priority", "MEDIUM").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test @DisplayName("5.3 ?priority=URGENT -> 400")
    void filterPriorityInvalid() throws Exception {
        String o = user();
        list(o, "priority", "URGENT").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("5.4 ?overdue=true: nur überfällige, nicht COMPLETED, nicht ohne Datum")
    void filterOverdue() throws Exception {
        String o = user();
        String over = task(o, "");
        String done = task(o, "");
        task(o, "");                                            // ohne Datum
        task(o, "\"due_date\":\"" + TODAY.plusDays(5) + "\"");  // Zukunft
        task(o, "\"due_date\":\"" + TODAY + "\"");              // heute = nicht überfällig
        setDueDateInDb(over, TODAY.minusDays(1));
        setDueDateInDb(done, TODAY.minusDays(1));
        setStatus(o, done, "COMPLETED");
        list(o, "author", o, "overdue", "true").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(over));
    }

    @Test @DisplayName("5.5 ?overdue=false filtert nicht auf überfällig")
    void filterOverdueFalse() throws Exception {
        String o = user();
        String over = task(o, ""); task(o, "");
        setDueDateInDb(over, TODAY.minusDays(1));
        list(o, "author", o, "overdue", "false").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].overdue", not(hasItem(true))));
    }

    @Test @DisplayName("5.6 Kombination assignee + priority + overdue")
    void filterCombined() throws Exception {
        String o = user(); String a = user();
        String match = task(o, "\"priority\":\"HIGH\"");
        String wrongPrio = task(o, "\"priority\":\"LOW\"");
        String notOverdue = task(o, "\"priority\":\"HIGH\"");
        String notAssigned = task(o, "\"priority\":\"HIGH\"");
        for (String id : new String[]{match, wrongPrio, notOverdue}) {
            mvc.perform(put("/api/tasks/" + id + "/assign").with(httpBasic(o, PW)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assignee\":\"" + a + "\"}")).andExpect(status().isOk());
        }
        setDueDateInDb(match, TODAY.minusDays(1));
        setDueDateInDb(wrongPrio, TODAY.minusDays(1));
        setDueDateInDb(notAssigned, TODAY.minusDays(1));
        list(o, "assignee", a, "priority", "HIGH", "overdue", "true")
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(match));
    }

    @Test @DisplayName("5.7 Kombination author + priority")
    void filterAuthorPriority() throws Exception {
        String o1 = user(); String o2 = user();
        String mine = task(o1, "\"priority\":\"LOW\"");
        task(o2, "\"priority\":\"LOW\"");
        task(o1, "\"priority\":\"HIGH\"");
        list(o1, "author", o1, "priority", "LOW").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(mine));
    }

    @Test @DisplayName("5.8 ?assignee=none funktioniert weiterhin")
    void filterAssigneeNone() throws Exception {
        String o = user(); String a = user();
        String free = task(o, "");
        String assigned = task(o, "");
        mvc.perform(put("/api/tasks/" + assigned + "/assign").with(httpBasic(o, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"" + a + "\"}")).andExpect(status().isOk());
        list(o, "author", o, "assignee", "none").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(free));
    }

    @Test @DisplayName("5.9 ohne Filter: alle Tasks, neueste zuerst")
    void noFilterSorted() throws Exception {
        String o = user();
        String first = task(o, ""); String second = task(o, "");
        list(o).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second));
        list(o, "author", o).andExpect(jsonPath("$[0].id").value(second)).andExpect(jsonPath("$[1].id").value(first));
    }
}
