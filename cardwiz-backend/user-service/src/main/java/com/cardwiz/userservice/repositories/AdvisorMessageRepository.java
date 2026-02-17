package com.cardwiz.userservice.repositories;

import com.cardwiz.userservice.models.AdvisorMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvisorMessageRepository extends JpaRepository<AdvisorMessage, Long> {
    List<AdvisorMessage> findByUserIdOrderByCreatedAtAsc(Long userId);
    void deleteByUserId(Long userId);
}
