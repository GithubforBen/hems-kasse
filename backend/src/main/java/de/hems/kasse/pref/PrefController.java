package de.hems.kasse.pref;

import de.hems.kasse.auth.KassePrincipal;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/me/pref")
public class PrefController {

    private final UserPrefRepository repo;

    public PrefController(UserPrefRepository repo) {
        this.repo = repo;
    }

    public record PrefDto(String theme) {}
    public record PutPref(@NotBlank @Pattern(regexp = "default|farm") String theme) {}

    @GetMapping
    public PrefDto get(@AuthenticationPrincipal KassePrincipal p) {
        return repo.findById(p.subjectKey())
                .map(x -> new PrefDto(x.getTheme()))
                .orElse(new PrefDto("default"));
    }

    @PutMapping
    @Transactional
    public PrefDto put(@AuthenticationPrincipal KassePrincipal p, @RequestBody @Valid PutPref body) {
        UserPref pref = repo.findById(p.subjectKey()).orElseGet(() ->
                UserPref.builder().subjectKey(p.subjectKey()).theme("default").updatedAt(Instant.now()).build());
        pref.setTheme(body.theme());
        pref.setUpdatedAt(Instant.now());
        repo.save(pref);
        return new PrefDto(pref.getTheme());
    }
}
