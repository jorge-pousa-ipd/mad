package com.example.rta.model.repository;

import com.example.rta.model.entity.NormalizedLibelleExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NormalizedLibelleExtraRepository extends JpaRepository<NormalizedLibelleExtra, Integer> {
}
