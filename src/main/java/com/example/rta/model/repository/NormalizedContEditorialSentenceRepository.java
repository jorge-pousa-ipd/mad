package com.example.rta.model.repository;

import com.example.rta.dto.NormalizedSentenceDto;
import com.example.rta.model.entity.NormalizedContEditorialSentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NormalizedContEditorialSentenceRepository extends JpaRepository<NormalizedContEditorialSentence, Integer> {
	@Query("SELECT n.id, n.normalizedSentence FROM NormalizedContEditorialSentence n WHERE n.wordCount > :count")
	List<NormalizedSentenceDto> findIdAndSentenceWithWordCountGreaterThan(int count);

}
