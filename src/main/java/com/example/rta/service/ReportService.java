package com.example.rta.service;

import com.example.rta.dto.NormalizedSentenceDto;
import com.example.rta.model.entity.NormalizedLibelle;
import com.example.rta.model.repository.NormalizedContEditorialSentenceRepository;
import com.example.rta.model.repository.NormalizedLibelleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static util.Constants.PAGE_SIZE;
import static util.Constants.SEPARATOR;

@Service
public class ReportService {
	private static final int MAX_CONCURRENT_TASKS = 50;

	private static final String OCCURRENCES_OUT = "ocurrences_report.csv";

	private final NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository;
	private final NormalizedLibelleRepository normalizedLibelleRepository;

	ReportService(NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository,
				  NormalizedLibelleRepository normalizedLibelleRepository) {
		this.normalizedContEditorialSentenceRepository = normalizedContEditorialSentenceRepository;
		this.normalizedLibelleRepository = normalizedLibelleRepository;
	}

	public void countOccurrences() {
		List<NormalizedSentenceDto> normalizedContEditorialSentenceList = normalizedContEditorialSentenceRepository.findIdAndSentenceWithWordCountGreaterThan(4);

		Path out = Paths.get(OCCURRENCES_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			 ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()
		) {
			Semaphore limiter = new Semaphore(MAX_CONCURRENT_TASKS);

			writer.write("libelleId;occurrences;cont_editorial_ids");
			writer.newLine();

			int page = 0;
			Page<NormalizedLibelle> normalizedLibellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				normalizedLibellePage = normalizedLibelleRepository.findAll(pageable);

				List<Future<String>> tasks = new ArrayList<>();

				for (NormalizedLibelle normalizedLibelle : normalizedLibellePage.getContent()) {
					limiter.acquire();

					tasks.add(exec.submit(() -> {
						try {
							return countSentenceOccurrences(normalizedLibelle, normalizedContEditorialSentenceList);
						} finally {
							limiter.release();
						}
					}));
				}

				// waits for the futures and writes the output
				writeOccurrencesOutput(tasks, writer);

			} while (normalizedLibellePage.hasNext());

		} catch (Exception e) {
			throw new RuntimeException("Error counting occurrences", e);
		}
	}

	private String countSentenceOccurrences(NormalizedLibelle normalizedLibelle,
											List<NormalizedSentenceDto> normalizedContEditorialSentenceList) {
		String normalizedLibelleSentence = normalizedLibelle.getNormalizedLibelle();
		if (normalizedLibelleSentence == null) normalizedLibelleSentence = "";

		List<NormalizedSentenceDto> matches = new ArrayList<>();
		for (NormalizedSentenceDto s : normalizedContEditorialSentenceList) {
			if (normalizedLibelleSentence.contains(s.normalizedSentence())) {
				matches.add(s);
			}
		}

		String ids = removeDuplicateMatchesAndGetIds(matches);
		int count = ids.isEmpty() ? 0 : ids.split(",").length;
		return normalizedLibelle.getId() + SEPARATOR + count + SEPARATOR + ids;
	}

	private String removeDuplicateMatchesAndGetIds(List<NormalizedSentenceDto> matches) {
		if (matches == null || matches.isEmpty()) return "";

		if (matches.size() == 1) {
			Integer id = matches.getFirst().id();
			return id == null ? "" : id.toString();
		}

		// keep longest-first so that shorter sentences contained in longer ones are excluded
		matches.sort((a, b) -> Integer.compare(b.normalizedSentence().length(), a.normalizedSentence().length()));

		List<NormalizedSentenceDto> filtered = new ArrayList<>();
		filtered.add(matches.getFirst()); // add the longest one

		for (int i = 1; i < matches.size(); i++) {
			boolean contained = false;
			NormalizedSentenceDto candidate = matches.get(i);

			for (int j = 0; j < i; j++) {
				if (matches.get(j).normalizedSentence().contains(candidate.normalizedSentence())) {
					contained = true;
					break;
				}
			}
			if (!contained) filtered.add(candidate);
		}

		StringBuilder sb = new StringBuilder();
		for (NormalizedSentenceDto dto : filtered) {
			if (!sb.isEmpty()) sb.append(",");
			sb.append(dto.id());
		}

		return sb.toString();
	}

	private void writeOccurrencesOutput(List<Future<String>> tasks, BufferedWriter writer)
			throws ExecutionException, InterruptedException, IOException {
		StringBuilder batch = new StringBuilder(64_000);

		for (Future<String> f : tasks) {
			batch.append(f.get()).append('\n');
		}

		writer.write(batch.toString());
		writer.flush();
	}
}
