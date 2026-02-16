package com.cardwiz.userservice.repositories;

import com.cardwiz.userservice.models.UserCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {
    List<UserCard> findByUserId(Long userId);
}
