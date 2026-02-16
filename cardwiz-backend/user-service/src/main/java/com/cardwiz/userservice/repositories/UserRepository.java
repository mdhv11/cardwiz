package com.cardwiz.userservice.repositories;

import com.cardwiz.userservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    java.util.Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
