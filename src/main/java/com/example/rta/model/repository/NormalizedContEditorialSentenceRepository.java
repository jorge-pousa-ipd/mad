package com.example.rta.model.repository;

import com.example.rta.model.NormalizedContEditorialSentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NormalizedContEditorialSentenceRepository extends JpaRepository<NormalizedContEditorialSentence, Integer> {

}
