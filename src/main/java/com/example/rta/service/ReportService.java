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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static util.Constants.PAGE_SIZE;
import static util.Constants.SEPARATOR;

@Service
public class ReportService {
	private static final int MAX_CONCURRENT_TASKS = 20;

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
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			// header (semicolon-separated) - added word_count column
			writer.write("libelleId;occurrences;cont_editorial_ids");
			writer.newLine();

			// create a fixed pool of virtual threads
			ExecutorService exec =Executors.newVirtualThreadPerTaskExecutor();
			// semaphore: limit tasks in flight to avoid unbounded in-memory queueing
			Semaphore sem = new Semaphore(MAX_CONCURRENT_TASKS);

			int page = 0;
			Page<NormalizedLibelle> normalizedLibellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				normalizedLibellePage = normalizedLibelleRepository.findAll(pageable);

				// collect futures for submitted tasks (minimal change: use CompletableFuture)
				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (NormalizedLibelle normalizedLibelle : normalizedLibellePage.getContent()) {
					sem.acquire();

					CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
						try {
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
							String line = normalizedLibelle.getId() + SEPARATOR + count + SEPARATOR + ids;

							// synchronized write to the single writer
							// TODO: try with an exclusive thread for writing
							synchronized (writer) {
								try {
									writer.write(line);
									writer.newLine();
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
						} finally {
							// release permit so main thread can submit more tasks
							sem.release();
						}
					}, exec);
					futures.add(f);
				}

				// wait for all futures of this page to complete before advancing
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				page++;
				writer.flush();
			} while (normalizedLibellePage.hasNext());

			// shutdown and wait
			exec.shutdown();


		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Libelle file", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while counting occurrences", e);
		}
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
}
