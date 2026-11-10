package taskmanagement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class UserController {

    private final UserService service;


    public UserController(UserService service) {
        this.service = service;
    }


    @PostMapping("/api/accounts")
    public ResponseEntity<Void> setAccount(@Valid @RequestBody UserInfo userInfo) {
        service.register(userInfo);

        return ResponseEntity.ok().build();
    }



    public record UserInfo (@Email @NotBlank String email, @NotBlank @Size(min = 6) String password) {}

}
