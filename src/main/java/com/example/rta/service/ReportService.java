package com.example.rta.service;

import com.example.rta.dto.NormalizedSentenceDto;
import com.example.rta.model.entity.BlocContenu;
import com.example.rta.model.entity.Libelle;
import com.example.rta.model.entity.NormalizedLibelle;
import com.example.rta.model.repository.BlocContenuRepository;
import com.example.rta.model.repository.LibelleRepository;
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
import java.util.*;
import java.util.concurrent.*;

import static util.Constants.*;
import static util.ParseXML.parseXml;

@Service
public class ReportService {
	private static final int MAX_CONCURRENT_TASKS = 25;

	private static final String BLOC_CONTENU_OUT = "bloc_contenu_out.csv";
	private static final String OCCURRENCES_OUT = "ocurrences_report.csv";

	private final BlocContenuRepository blocContenuRepository;
	private final LibelleRepository libelleRepository;
	private final NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository;
	private final NormalizedLibelleRepository normalizedLibelleRepository;

	ReportService(BlocContenuRepository blocContenuRepository, LibelleRepository libelleRepository,
				  NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository,
				  NormalizedLibelleRepository normalizedLibelleRepository) {
		this.blocContenuRepository = blocContenuRepository;
		this.libelleRepository = libelleRepository;
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
			// semaphore: limit tasks in flight to avoid unbounded in-memory queueing
			Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);

			writer.write("libelleId;occurrences;cont_editorial_ids");
			writer.newLine();

			int page = 0;
			Page<NormalizedLibelle> normalizedLibellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				normalizedLibellePage = normalizedLibelleRepository.findAll(pageable);

				List<Future<?>> futures = new ArrayList<>();

				for (NormalizedLibelle normalizedLibelle : normalizedLibellePage.getContent()) {
					semaphore.acquire();

					Future<?> f = exec.submit(() -> {
						try {
							String line = countSentenceOccurrences(normalizedLibelle, normalizedContEditorialSentenceList);

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
							semaphore.release();
						}
					}, exec);
					futures.add(f);
				}

				// wait for all futures of this page to complete before advancing
				for (Future<?> future : futures) {
					future.get();
				}

				page++;
				writer.flush();
			} while (normalizedLibellePage.hasNext());

			// shutdown and wait
			exec.shutdown();

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


	public void generateBlocContenuRelationships() throws Exception {
		Map<String, Integer> libelleMap = initializeLibelleMap();

		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of(BLOC_CONTENU_OUT), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING
		)) {
			int blocPageIndex = 0;
			Page<BlocContenu> blocPage;

			try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
				do {
					PageRequest pageable = PageRequest.of(blocPageIndex, SMALL_PAGE_SIZE, Sort.by("id"));
					blocPage = blocContenuRepository.findAll(pageable);

					for (BlocContenu b : blocPage.getContent()) {
						semaphore.acquire();
						executor.submit(() -> {
							try {
								List<String> parsedBlocXml = parseXml(b.getBlocxml());

								for (String phrase : parsedBlocXml) {
									Integer libelleId = libelleMap.get(phrase.trim());
									String line = b.getId() + SEPARATOR + Objects.requireNonNullElse(libelleId, phrase + " NULL");
									synchronized (writer) {
										writer.write(line);
										writer.newLine();
									}
								}
							} catch (Exception e) {
								e.printStackTrace();
							} finally {
								semaphore.release();
							}
						});
					}
					blocPageIndex++;

				} while (blocPage.hasNext());
			}
		}
	}

	Map<String, Integer> initializeLibelleMap() throws Exception {
		List<Libelle> list = libelleRepository.findAll();
		Map<String, Integer> map = new HashMap<>(list.size());
		for (Libelle libelle : list) {
			map.put(libelle.getLibelleOriginal().trim(), libelle.getId());
		}

		return map;
	}
}
