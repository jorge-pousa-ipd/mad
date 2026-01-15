package com.example.rta.service;

import com.example.rta.model.ContentEditorialSentenceRepository;
import com.example.rta.model.LibelleRepository;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
	private final ContentEditorialSentenceRepository contentEditorialSentenceRepository;
	private final LibelleRepository libelleRepository;

	ReportService(ContentEditorialSentenceRepository contentEditorialSentenceRepository,
							LibelleRepository libelleRepository) {
		this.contentEditorialSentenceRepository = contentEditorialSentenceRepository;
		this.libelleRepository = libelleRepository;
	}

	public void countOccurrences() {
		//List<String> libelles = libelleRepository.findAll();
	}
}
