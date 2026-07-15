package org.example.repository;

import org.example.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {
    Optional<Paper> findFirstByDoiIgnoreCase(String doi);
    Optional<Paper> findFirstByArxivIdIgnoreCase(String arxivId);
    Optional<Paper> findFirstByOpenalexId(String openalexId);
    Optional<Paper> findFirstByTitleIgnoreCase(String title);
}
