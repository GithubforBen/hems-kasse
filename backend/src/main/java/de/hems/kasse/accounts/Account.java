package de.hems.kasse.accounts;

import de.hems.kasse.auth.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A login the admins manage themselves: either a Gruppe (role VERKAUF, the name is the group
 * name shown on receipts) or an admin user. The password is stored encrypted, see
 * {@link SecretBox}.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "password_enc", nullable = false, length = 500)
    private String passwordEnc;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
