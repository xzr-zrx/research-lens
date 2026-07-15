package org.example.repository;

import org.example.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findByProjectPaperId(Long projectPaperId);
    @Transactional
    void deleteByProjectPaperId(Long projectPaperId);
}
