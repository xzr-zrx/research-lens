package org.example.repository;

import org.example.entity.ResearchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ResearchProfileRepository extends JpaRepository<ResearchProfile, Long> {
    Optional<ResearchProfile> findByProjectId(Long projectId);
    @Transactional
    void deleteByProjectId(Long projectId);
}
