package org.example.repository;

import org.example.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {
    List<SearchQuery> findByProjectIdOrderByIdAsc(Long projectId);
    @Transactional
    void deleteByProjectId(Long projectId);
}
