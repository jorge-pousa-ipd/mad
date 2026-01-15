package com.example.rta.model.repository;

import com.example.rta.model.NormalizedLibelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NormalizedLibelleRepository extends JpaRepository<NormalizedLibelle, Integer> {

}
