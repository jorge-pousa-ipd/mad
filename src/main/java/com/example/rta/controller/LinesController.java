package com.example.rta.controller;

import com.example.rta.service.NormalizeService;
import com.example.rta.service.ReportService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LinesController {
	private final ReportService reportService;
	private final NormalizeService normalizeService;

	public LinesController(ReportService reportService,  NormalizeService normalizeService) {
		this.normalizeService = normalizeService;
		this.reportService = reportService;
	}

	@PutMapping("/countOccurrences")
	public Integer countOccurrences() {
		reportService.countOccurrences();

		return 0;
	}

	@PutMapping("/normalizeLibelle")
	public Integer normalizeLibelle() {
		normalizeService.normalizeLibelle();

		return 0;
	}

	@PutMapping("/normalizeContentEditorial")
	public Integer normalizeContentEditorial() {
		normalizeService.normalizeContentEditorial();

		return 0;
	}

}
