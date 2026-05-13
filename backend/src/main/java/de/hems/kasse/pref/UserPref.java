package de.hems.kasse.pref;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_pref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPref {

    @Id
    @Column(name = "subject_key", length = 200)
    private String subjectKey;

    @Column(nullable = false, length = 20)
    private String theme;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
