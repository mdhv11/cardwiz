package com.cardwiz.userservice.repositories;

import com.cardwiz.userservice.models.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {
    List<UploadedDocument> findByUserIdOrderByUploadedAtDesc(Long userId);
    Optional<UploadedDocument> findByIdAndUserId(Long id, Long userId);
}
