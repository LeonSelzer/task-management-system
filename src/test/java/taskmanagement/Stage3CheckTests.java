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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prüf-Tests für Stufe 3 (Pagination, Sortierung, Statusfilter).
 * Jeder Test legt einen eigenen Nutzer an und filtert mit ?author=..., damit sich
 * die Tests in der gemeinsamen Test-Datenbank nicht gegenseitig stören.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stage3;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.key-dir=build/test-keys"
})
@AutoConfigureMockMvc
class Stage3CheckTests {

    private static final String PW = "secret123";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mvc;

    // ---------- Helfer ----------

    private String user() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@mail.com";
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isOk());
        return email;
    }

    private String task(String owner, String title, String extraJson) throws Exception {
        String json = "{\"title\":\"" + title + "\",\"description\":\"d\"" + (extraJson.isEmpty() ? "" : "," + extraJson) + "}";
        String body = mvc.perform(post("/api/tasks").with(httpBasic(owner, PW)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
    }

    private String task(String owner, String title) throws Exception {
        return task(owner, title, "");
    }

    /** Legt n Tasks an und gibt die IDs in Erstellungsreihenfolge zurück (älteste zuerst). */
    private List<String> tasks(String owner, int n) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(task(owner, "t" + i));
        return ids;
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

    // ---------- 1. Pagination ----------

    @Test @DisplayName("1.1 Standard: Seite 0 mit 20 Einträgen, neueste zuerst")
    void defaultPage() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 21);
        list(o, "author", o).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].id").value(ids.get(20)))
                .andExpect(jsonPath("$[19].id").value(ids.get(1)));
    }

    @Test @DisplayName("1.2 page=1 liefert den Rest")
    void secondPage() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 21);
        list(o, "author", o, "page", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ids.get(0)));
    }

    @Test @DisplayName("1.3 size=2 teilt korrekt auf, Seiten überschneiden sich nicht")
    void customSize() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 5);
        list(o, "author", o, "size", "2", "page", "0").andExpect(jsonPath("$[*].id", contains(ids.get(4), ids.get(3))));
        list(o, "author", o, "size", "2", "page", "1").andExpect(jsonPath("$[*].id", contains(ids.get(2), ids.get(1))));
        list(o, "author", o, "size", "2", "page", "2").andExpect(jsonPath("$[*].id", contains(ids.get(0))));
    }

    @Test @DisplayName("1.4 Seite hinter dem Ende -> 200 mit []")
    void pageBeyondEnd() throws Exception {
        String o = user();
        tasks(o, 3);
        list(o, "author", o, "page", "50").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test @DisplayName("1.5 negative Seite -> 400")
    void negativePage() throws Exception {
        String o = user();
        list(o, "page", "-1").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("1.6 size=0 und size=101 -> 400")
    void sizeOutOfRange() throws Exception {
        String o = user();
        list(o, "size", "0").andExpect(status().isBadRequest());
        list(o, "size", "101").andExpect(status().isBadRequest());
        list(o, "size", "-5").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("1.7 size=1 und size=100 sind erlaubt")
    void sizeBoundaries() throws Exception {
        String o = user();
        tasks(o, 2);
        list(o, "author", o, "size", "1").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        list(o, "author", o, "size", "100").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test @DisplayName("1.8 page/size keine Zahl -> 400")
    void notANumber() throws Exception {
        String o = user();
        list(o, "page", "abc").andExpect(status().isBadRequest());
        list(o, "size", "zwei").andExpect(status().isBadRequest());
    }

    // ---------- 2. Header und Body-Format ----------

    @Test @DisplayName("2.1 Body bleibt ein JSON-Array")
    void bodyIsArray() throws Exception {
        String o = user();
        tasks(o, 1);
        list(o, "author", o).andExpect(jsonPath("$").isArray());
    }

    @Test @DisplayName("2.2 X-Total-Count und X-Total-Pages")
    void headers() throws Exception {
        String o = user();
        tasks(o, 5);
        list(o, "author", o, "size", "2")
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("X-Total-Pages", "3"));
    }

    @Test @DisplayName("2.3 Header gleich auf jeder Seite, auch hinter dem Ende")
    void headersOnEveryPage() throws Exception {
        String o = user();
        tasks(o, 5);
        list(o, "author", o, "size", "2", "page", "2")
                .andExpect(header().string("X-Total-Count", "5")).andExpect(header().string("X-Total-Pages", "3"));
        list(o, "author", o, "size", "2", "page", "9")
                .andExpect(header().string("X-Total-Count", "5")).andExpect(header().string("X-Total-Pages", "3"));
    }

    @Test @DisplayName("2.4 X-Total-Count berücksichtigt Filter")
    void headersRespectFilters() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 4);
        setStatus(o, ids.get(0), "COMPLETED");
        list(o, "author", o, "status", "COMPLETED")
                .andExpect(header().string("X-Total-Count", "1")).andExpect(header().string("X-Total-Pages", "1"));
    }

    @Test @DisplayName("2.5 Standardgröße 20 in X-Total-Pages")
    void headersDefaultSize() throws Exception {
        String o = user();
        tasks(o, 21);
        list(o, "author", o).andExpect(header().string("X-Total-Count", "21")).andExpect(header().string("X-Total-Pages", "2"));
    }

    // ---------- 3. Sortierung ----------

    @Test @DisplayName("3.1 sort=title (ohne Richtung = asc)")
    void sortTitleDefaultAsc() throws Exception {
        String o = user();
        String b = task(o, "Banane"); String a = task(o, "Apfel"); String c = task(o, "Citrone");
        list(o, "author", o, "sort", "title").andExpect(jsonPath("$[*].id", contains(a, b, c)));
    }

    @Test @DisplayName("3.2 sort=title,desc")
    void sortTitleDesc() throws Exception {
        String o = user();
        String b = task(o, "Banane"); String a = task(o, "Apfel"); String c = task(o, "Citrone");
        list(o, "author", o, "sort", "title,desc").andExpect(jsonPath("$[*].id", contains(c, b, a)));
    }

    @Test @DisplayName("3.3 sort=title,asc ausdrücklich")
    void sortTitleAsc() throws Exception {
        String o = user();
        String b = task(o, "Banane"); String a = task(o, "Apfel");
        list(o, "author", o, "sort", "title,asc").andExpect(jsonPath("$[*].id", contains(a, b)));
    }

    @Test @DisplayName("3.4 sort=due_date,asc und desc (API-Name, nicht dueDate)")
    void sortDueDate() throws Exception {
        String o = user();
        String later = task(o, "x", "\"due_date\":\"" + TODAY.plusDays(9) + "\"");
        String soon = task(o, "y", "\"due_date\":\"" + TODAY.plusDays(1) + "\"");
        String mid = task(o, "z", "\"due_date\":\"" + TODAY.plusDays(5) + "\"");
        list(o, "author", o, "sort", "due_date,asc").andExpect(jsonPath("$[*].id", contains(soon, mid, later)));
        list(o, "author", o, "sort", "due_date,desc").andExpect(jsonPath("$[*].id", contains(later, mid, soon)));
    }

    @Test @DisplayName("3.5 sort=id,asc")
    void sortIdAsc() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 3);
        list(o, "author", o, "sort", "id,asc").andExpect(jsonPath("$[*].id", contains(ids.get(0), ids.get(1), ids.get(2))));
    }

    @Test @DisplayName("3.6 Gleichstand: gleicher Titel -> id absteigend")
    void tieBreaker() throws Exception {
        String o = user();
        String z = task(o, "Zebra");
        String same1 = task(o, "Gleich"); String same2 = task(o, "Gleich"); String same3 = task(o, "Gleich");
        list(o, "author", o, "sort", "title,asc").andExpect(jsonPath("$[*].id", contains(same3, same2, same1, z)));
        list(o, "author", o, "sort", "title,desc").andExpect(jsonPath("$[*].id", contains(z, same3, same2, same1)));
    }

    @Test @DisplayName("3.7 Gleichstand bleibt über Seitengrenzen stabil")
    void tieBreakerAcrossPages() throws Exception {
        String o = user();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) ids.add(task(o, "Gleich"));
        list(o, "author", o, "sort", "title", "size", "2", "page", "0").andExpect(jsonPath("$[*].id", contains(ids.get(3), ids.get(2))));
        list(o, "author", o, "sort", "title", "size", "2", "page", "1").andExpect(jsonPath("$[*].id", contains(ids.get(1), ids.get(0))));
    }

    @Test @DisplayName("3.8 Sortierung gilt über alle Seiten, nicht nur innerhalb einer Seite")
    void sortBeforePaging() throws Exception {
        String o = user();
        String c = task(o, "C"); String a = task(o, "A"); String d = task(o, "D"); String b = task(o, "B");
        list(o, "author", o, "sort", "title", "size", "2", "page", "0").andExpect(jsonPath("$[*].id", contains(a, b)));
        list(o, "author", o, "sort", "title", "size", "2", "page", "1").andExpect(jsonPath("$[*].id", contains(c, d)));
    }

    @Test @DisplayName("3.9 unbekanntes Sortierfeld -> 400")
    void sortUnknownField() throws Exception {
        String o = user();
        list(o, "sort", "password").andExpect(status().isBadRequest());
        list(o, "sort", "owner").andExpect(status().isBadRequest());
        list(o, "sort", "dueDate").andExpect(status().isBadRequest());   // interner Name ist nicht erlaubt
    }

    @Test @DisplayName("3.10 falsche Richtung oder kaputtes Format -> 400")
    void sortBadDirection() throws Exception {
        String o = user();
        list(o, "sort", "title,up").andExpect(status().isBadRequest());
        list(o, "sort", "title,asc,desc").andExpect(status().isBadRequest());
        list(o, "sort", "").andExpect(status().isBadRequest());
    }

    // ---------- 4. Statusfilter ----------

    @Test @DisplayName("4.1 ?status=IN_PROGRESS filtert")
    void statusFilter() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 3);
        setStatus(o, ids.get(1), "IN_PROGRESS");
        list(o, "author", o, "status", "IN_PROGRESS").andExpect(jsonPath("$[*].id", contains(ids.get(1))));
        list(o, "author", o, "status", "CREATED").andExpect(jsonPath("$[*].id", contains(ids.get(2), ids.get(0))));
    }

    @Test @DisplayName("4.2 ungültiger Status -> 400")
    void statusInvalid() throws Exception {
        String o = user();
        list(o, "status", "DONE").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("4.3 status + priority + author + sort + size kombiniert")
    void everythingCombined() throws Exception {
        String o = user(); String other = user();
        String hit1 = task(o, "B", "\"priority\":\"HIGH\"");
        String hit2 = task(o, "A", "\"priority\":\"HIGH\"");
        String hit3 = task(o, "C", "\"priority\":\"HIGH\"");
        String wrongPrio = task(o, "A", "\"priority\":\"LOW\"");
        String wrongStatus = task(o, "A", "\"priority\":\"HIGH\"");
        String otherAuthor = task(other, "A", "\"priority\":\"HIGH\"");
        for (String id : List.of(hit1, hit2, hit3, wrongPrio)) setStatus(o, id, "IN_PROGRESS");
        setStatus(other, otherAuthor, "IN_PROGRESS");

        list(o, "author", o, "status", "IN_PROGRESS", "priority", "HIGH", "sort", "title", "size", "2")
                .andExpect(jsonPath("$[*].id", contains(hit2, hit1)))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(header().string("X-Total-Pages", "2"));
        list(o, "author", o, "status", "IN_PROGRESS", "priority", "HIGH", "sort", "title", "size", "2", "page", "1")
                .andExpect(jsonPath("$[*].id", contains(hit3)));
    }

    @Test @DisplayName("4.4 status kombiniert mit overdue=false")
    void statusWithOverdue() throws Exception {
        String o = user();
        List<String> ids = tasks(o, 2);
        setStatus(o, ids.get(0), "COMPLETED");
        list(o, "author", o, "status", "COMPLETED", "overdue", "false").andExpect(jsonPath("$[*].id", contains(ids.get(0))));
    }

    // ---------- Regression ----------

    @Test @DisplayName("R.1 ohne Login -> 401")
    void unauthorized() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("R.2 total_comments weiterhin in der Liste")
    void totalCommentsStillThere() throws Exception {
        String o = user();
        String id = task(o, "t");
        mvc.perform(post("/api/tasks/" + id + "/comments").with(httpBasic(o, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hi\"}")).andExpect(status().isOk());
        list(o, "author", o, "size", "1").andExpect(jsonPath("$[0].total_comments").value(1));
    }
}
