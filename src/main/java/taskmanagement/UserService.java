package taskmanagement;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Locale;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }
    public void register(UserController.UserInfo userInfo) {
        String email = normalizeEmail(userInfo.email());

        if (repository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        String passwordHash = passwordEncoder.encode(userInfo.password());

        AppUser appUser = new AppUser(email, passwordHash);
        repository.save(appUser);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = normalizeEmail(username);

        AppUser appUser = repository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Benutzer nicht gefunden"
                        )
                );

        return User.withUsername(appUser.getEmail())
                .password(appUser.getPassword())
                .authorities(Collections.emptyList())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException emailAlreadyExists() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "E-Mail-Adresse bereits registriert"
        );
    }
}
