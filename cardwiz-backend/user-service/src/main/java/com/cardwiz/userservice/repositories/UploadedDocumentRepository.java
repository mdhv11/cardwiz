package com.cardwiz.userservice.repositories;

import com.cardwiz.userservice.models.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {
    List<UploadedDocument> findByUserIdOrderByUploadedAtDesc(Long userId);
}
