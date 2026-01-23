package com.example.rta.model.repository;

import com.example.rta.model.entity.NormalizedLibelleExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface NormalizedLibelleExtraRepository extends JpaRepository<NormalizedLibelleExtra, Integer> {
	Page<NormalizedLibelleExtra> findAllByWordCountGreaterThanEqual(Integer wordCount, Pageable pageable);
}
