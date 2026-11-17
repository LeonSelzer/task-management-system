package taskmanagement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stage1;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.key-dir=build/test-keys"
})
@AutoConfigureMockMvc
class Stage1CheckTests {

    private static final String PW = "secret123";

    @Autowired
    private MockMvc mvc;

    private String user() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@mail.com";
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isOk());
        return email;
    }

    private String task(String owner) throws Exception {
        String body = mvc.perform(post("/api/tasks").with(httpBasic(owner, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"old title\",\"description\":\"old desc\"}")).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
    }

    private String comment(String taskId, String author) throws Exception {
        mvc.perform(post("/api/tasks/" + taskId + "/comments").with(httpBasic(author, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\"}")).andExpect(status().isOk());
        String body = mvc.perform(get("/api/tasks/" + taskId + "/comments").with(httpBasic(author, PW)))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("^\\[\\{\"id\":\"(\\d+)\".*", "$1");
    }

    private ResultActions patch(String url, String user, String json) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (user != null) req = req.with(httpBasic(user, PW));
        return mvc.perform(req);
    }

    private ResultActions del(String url, String user) throws Exception {
        var req = delete(url);
        if (user != null) req = req.with(httpBasic(user, PW));
        return mvc.perform(req);
    }

    // ---------- 1. PATCH task ----------

    @Test @DisplayName("1.1 PATCH task: nur title ändert nur title")
    void patchTitleOnly() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{\"title\":\"new\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("new")).andExpect(jsonPath("$.description").value("old desc"))
                .andExpect(jsonPath("$.id").value(t)).andExpect(jsonPath("$.author").value(o));
    }

    @Test @DisplayName("1.2 PATCH task: nur description ändert nur description")
    void patchDescOnly() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{\"description\":\"new\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("old title")).andExpect(jsonPath("$.description").value("new"));
    }

    @Test @DisplayName("1.3 PATCH task: beide Felder")
    void patchBoth() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{\"title\":\"a\",\"description\":\"b\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("a")).andExpect(jsonPath("$.description").value("b"));
    }

    @Test @DisplayName("1.4 PATCH task: Änderung ist persistent")
    void patchPersisted() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{\"title\":\"persisted\"}").andExpect(status().isOk());
        mvc.perform(get("/api/tasks").param("author", o).with(httpBasic(o, PW)))
                .andExpect(jsonPath("$[0].title").value("persisted"));
    }

    @Test @DisplayName("1.5 PATCH task: leerer Body {} -> 400")
    void patchEmpty() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("1.6 PATCH task: blank title -> 400")
    void patchBlankTitle() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, o, "{\"title\":\"  \"}").andExpect(status().isBadRequest());
        patch("/api/tasks/" + t, o, "{\"title\":\"ok\",\"description\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("1.7 PATCH task: fremder Nutzer -> 403")
    void patchForbidden() throws Exception {
        String o = user(); String other = user(); String t = task(o);
        patch("/api/tasks/" + t, other, "{\"title\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test @DisplayName("1.8 PATCH task: unbekannte Task -> 404")
    void patchNotFound() throws Exception {
        String o = user();
        patch("/api/tasks/999999", o, "{\"title\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test @DisplayName("1.9 PATCH task: ohne Login -> 401")
    void patchUnauthorized() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t, null, "{\"title\":\"x\"}").andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("1.10 PATCH task: Reihenfolge 400 vor 404")
    void patchOrder400Before404() throws Exception {
        String o = user();
        patch("/api/tasks/999999", o, "{}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("1.11 PATCH task: Reihenfolge 400 vor 403")
    void patchOrder400Before403() throws Exception {
        String o = user(); String other = user(); String t = task(o);
        patch("/api/tasks/" + t, other, "{\"title\":\"\"}").andExpect(status().isBadRequest());
    }

    // ---------- 2. DELETE task ----------

    @Test @DisplayName("2.1 DELETE task: Owner -> 204, Task weg")
    void deleteTask() throws Exception {
        String o = user(); String t = task(o);
        del("/api/tasks/" + t, o).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/tasks").param("author", o).with(httpBasic(o, PW))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test @DisplayName("2.2 DELETE task: mit Kommentaren -> 204, Kommentare weg")
    void deleteTaskWithComments() throws Exception {
        String o = user(); String t = task(o);
        comment(t, o); comment(t, o);
        del("/api/tasks/" + t, o).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/" + t + "/comments").with(httpBasic(o, PW))).andExpect(status().isNotFound());
    }

    @Test @DisplayName("2.3 DELETE task: fremder Nutzer -> 403, Task bleibt")
    void deleteTaskForbidden() throws Exception {
        String o = user(); String other = user(); String t = task(o);
        del("/api/tasks/" + t, other).andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks").param("author", o).with(httpBasic(o, PW))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test @DisplayName("2.4 DELETE task: Assignee darf nicht löschen -> 403")
    void deleteTaskAssigneeForbidden() throws Exception {
        String o = user(); String a = user(); String t = task(o);
        mvc.perform(put("/api/tasks/" + t + "/assign").with(httpBasic(o, PW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"" + a + "\"}")).andExpect(status().isOk());
        del("/api/tasks/" + t, a).andExpect(status().isForbidden());
    }

    @Test @DisplayName("2.5 DELETE task: unbekannt -> 404, ohne Login -> 401")
    void deleteTaskNotFoundAndUnauthorized() throws Exception {
        String o = user(); String t = task(o);
        del("/api/tasks/999999", o).andExpect(status().isNotFound());
        del("/api/tasks/" + t, null).andExpect(status().isUnauthorized());
    }

    // ---------- 3. PATCH comment ----------

    @Test @DisplayName("3.1 PATCH comment: Autor -> 200 mit neuem Text")
    void patchComment() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        patch("/api/tasks/" + t + "/comments/" + c, o, "{\"text\":\"changed\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("changed")).andExpect(jsonPath("$.id").value(c))
                .andExpect(jsonPath("$.task_id").value(t)).andExpect(jsonPath("$.author").value(o));
        mvc.perform(get("/api/tasks/" + t + "/comments").with(httpBasic(o, PW))).andExpect(jsonPath("$[0].text").value("changed"));
    }

    @Test @DisplayName("3.2 PATCH comment: leerer Text -> 400")
    void patchCommentBlank() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        patch("/api/tasks/" + t + "/comments/" + c, o, "{\"text\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("3.3 PATCH comment: text fehlt -> 400")
    void patchCommentMissing() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        patch("/api/tasks/" + t + "/comments/" + c, o, "{}").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("3.4 PATCH comment: anderer Nutzer (auch Task-Owner) -> 403")
    void patchCommentForbidden() throws Exception {
        String o = user(); String other = user(); String t = task(o); String c = comment(t, other);
        patch("/api/tasks/" + t + "/comments/" + c, o, "{\"text\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test @DisplayName("3.5 PATCH comment: Kommentar gehört zu anderer Task -> 404")
    void patchCommentWrongTask() throws Exception {
        String o = user(); String t1 = task(o); String t2 = task(o); String c = comment(t1, o);
        patch("/api/tasks/" + t2 + "/comments/" + c, o, "{\"text\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test @DisplayName("3.6 PATCH comment: Task existiert nicht -> 404")
    void patchCommentTaskMissing() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        patch("/api/tasks/999999/comments/" + c, o, "{\"text\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test @DisplayName("3.7 PATCH comment: Kommentar existiert nicht -> 404")
    void patchCommentMissingComment() throws Exception {
        String o = user(); String t = task(o);
        patch("/api/tasks/" + t + "/comments/999999", o, "{\"text\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test @DisplayName("3.8 PATCH comment: Reihenfolge 404 vor 403 (fremder Kommentar, falsche Task)")
    void patchCommentOrder404Before403() throws Exception {
        String o = user(); String other = user(); String t1 = task(o); String t2 = task(o); String c = comment(t1, other);
        patch("/api/tasks/" + t2 + "/comments/" + c, o, "{\"text\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test @DisplayName("3.9 PATCH comment: ohne Login -> 401")
    void patchCommentUnauthorized() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        patch("/api/tasks/" + t + "/comments/" + c, null, "{\"text\":\"x\"}").andExpect(status().isUnauthorized());
    }

    // ---------- 4. DELETE comment ----------

    @Test @DisplayName("4.1 DELETE comment: Autor -> 204")
    void deleteCommentByAuthor() throws Exception {
        String o = user(); String a = user(); String t = task(o); String c = comment(t, a);
        del("/api/tasks/" + t + "/comments/" + c, a).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/" + t + "/comments").with(httpBasic(o, PW))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test @DisplayName("4.2 DELETE comment: Task-Owner darf fremden Kommentar löschen -> 204")
    void deleteCommentByTaskOwner() throws Exception {
        String o = user(); String a = user(); String t = task(o); String c = comment(t, a);
        del("/api/tasks/" + t + "/comments/" + c, o).andExpect(status().isNoContent());
    }

    @Test @DisplayName("4.3 DELETE comment: Dritter -> 403")
    void deleteCommentForbidden() throws Exception {
        String o = user(); String a = user(); String x = user(); String t = task(o); String c = comment(t, a);
        del("/api/tasks/" + t + "/comments/" + c, x).andExpect(status().isForbidden());
    }

    @Test @DisplayName("4.4 DELETE comment: falsche Task / unbekannt -> 404")
    void deleteCommentNotFound() throws Exception {
        String o = user(); String t1 = task(o); String t2 = task(o); String c = comment(t1, o);
        del("/api/tasks/" + t2 + "/comments/" + c, o).andExpect(status().isNotFound());
        del("/api/tasks/" + t1 + "/comments/999999", o).andExpect(status().isNotFound());
        del("/api/tasks/999999/comments/" + c, o).andExpect(status().isNotFound());
    }

    @Test @DisplayName("4.5 DELETE comment: ohne Login -> 401")
    void deleteCommentUnauthorized() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o);
        del("/api/tasks/" + t + "/comments/" + c, null).andExpect(status().isUnauthorized());
    }

    // ---------- Regression ----------

    @Test @DisplayName("R.1 Kommentarzähler stimmt nach Löschen")
    void commentCountAfterDelete() throws Exception {
        String o = user(); String t = task(o); String c = comment(t, o); comment(t, o);
        del("/api/tasks/" + t + "/comments/" + c, o).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks").param("author", o).with(httpBasic(o, PW))).andExpect(jsonPath("$[0].total_comments").value(1));
    }
}