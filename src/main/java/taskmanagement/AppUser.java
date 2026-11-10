package taskmanagement;


import jakarta.persistence.*;

import java.util.List;


@Entity
@Table(name = "accounts")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "owner")
    private List<AppTask> tasks;


    protected AppUser() {
        // Für JPA
    }

    public AppUser(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public List<AppTask> getTasks() {
        return tasks;
    }
}
