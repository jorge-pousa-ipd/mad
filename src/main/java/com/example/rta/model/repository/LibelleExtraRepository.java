package com.example.rta.model.repository;

import com.example.rta.model.entity.LibelleExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LibelleExtraRepository extends JpaRepository<LibelleExtra, Integer> {
}
