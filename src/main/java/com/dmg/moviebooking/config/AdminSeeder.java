package com.dmg.moviebooking.config;

import com.dmg.moviebooking.user.entity.Role;
import com.dmg.moviebooking.user.entity.User;
import com.dmg.moviebooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Public signup (/auth/register) only ever creates CUSTOMER accounts, so without this seeder a
 * fresh clone would have no way to reach any admin-only endpoint. Idempotent: skips seeding if an
 * ADMIN already exists. Credentials are documented in README.md.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        User admin = User.builder()
                .name("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();
        userRepository.save(admin);
        log.info("Seeded default admin account: {}", adminEmail);
    }
}
