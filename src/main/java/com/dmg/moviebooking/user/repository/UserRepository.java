package com.dmg.moviebooking.user.repository;

import com.dmg.moviebooking.user.entity.Role;
import com.dmg.moviebooking.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
