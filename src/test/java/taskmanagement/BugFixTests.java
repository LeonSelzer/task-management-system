package taskmanagement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.key-dir=build/test-keys"
})
@AutoConfigureMockMvc
class BugFixTests {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository taskRepository;

    private String register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@mail.com";
        mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        return email;
    }

    private String createTask(String owner) throws Exception {
        String body = mvc.perform(post("/api/tasks").with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
    }

    @Test
    void ownerCanChangeStatusOfUnassignedTask() throws Exception {
        String owner = register();
        String id = createTask(owner);

        mvc.perform(put("/api/tasks/" + id + "/status").with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void otherUserCannotChangeStatusOfUnassignedTask() throws Exception {
        String owner = register();
        String other = register();
        String id = createTask(owner);

        mvc.perform(put("/api/tasks/" + id + "/status").with(httpBasic(other, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassigningStoresNullAndCanBeFiltered() throws Exception {
        String owner = register();
        String assignee = register();
        String id = createTask(owner);

        mvc.perform(put("/api/tasks/" + id + "/assign").with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"" + assignee + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(assignee));

        mvc.perform(put("/api/tasks/" + id + "/assign").with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"none\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("none"));

        assertThat(taskRepository.findById(Long.valueOf(id)).orElseThrow().getAssignee()).isNull();

        mvc.perform(get("/api/tasks").param("author", owner).param("assignee", "none")
                        .with(httpBasic(owner, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void shutdownEndpointIsNotPublic() throws Exception {
        mvc.perform(post("/actuator/shutdown")).andExpect(status().isUnauthorized());

        String user = register();
        mvc.perform(post("/actuator/shutdown").with(httpBasic(user, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rsaKeysAreReusedAcrossRestarts(@TempDir Path dir) {
        RsaKeysConfig config = new RsaKeysConfig();
        KeyPair first = config.rsaKeyPair(dir.toString());
        KeyPair second = config.rsaKeyPair(dir.toString());

        assertThat(second.getPublic().getEncoded()).isEqualTo(first.getPublic().getEncoded());
        assertThat(second.getPrivate().getEncoded()).isEqualTo(first.getPrivate().getEncoded());
    }
}
