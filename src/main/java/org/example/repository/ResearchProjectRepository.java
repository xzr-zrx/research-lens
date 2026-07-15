package org.example.repository;

import org.example.entity.ResearchProject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {
}
