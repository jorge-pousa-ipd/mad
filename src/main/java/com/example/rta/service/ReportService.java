package com.example.rta.service;

import com.example.rta.dto.NormalizedSentenceDto;
import com.example.rta.model.entity.BlocContenu;
import com.example.rta.model.entity.NormalizedLibelle;
import com.example.rta.model.entity.NormalizedLibelleExtra;
import com.example.rta.model.repository.*;
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
import java.util.function.Function;

import static util.Constants.*;
import static util.ParseXML.parseXml;
import static util.ParseXML.trimAndLowerCaseAndRemoveLineBreaks;

@Service
public class ReportService {
	private static final int MAX_CONCURRENT_TASKS = 25;

	private static final String BLOC_CONTENU_OUT = "bloc_contenu_out.csv";
	private static final String BLOC_CONTENU_NOT_FOUND = "bloc_contenu_not_found.csv";
	private static final String LIBELLE_MATCHES_OUT = "libelle_matches.csv";
	private static final String LIBELLE_EXTRA_MATCHES_OUT = "libelle_matches_extra.csv";

	private final Object blocContenuLock = new Object();
	private final Object blocContenuNotFoundLock = new Object();
	private final Object sentenceRelationshipLock = new Object();

	private final BlocContenuRepository blocContenuRepository;
	private final NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository;
	private final NormalizedLibelleRepository normalizedLibelleRepository;
	private final NormalizedLibelleExtraRepository normalizedLibelleExtraRepository;

	ReportService(BlocContenuRepository blocContenuRepository,
				  NormalizedContEditorialSentenceRepository normalizedContEditorialSentenceRepository,
				  NormalizedLibelleRepository normalizedLibelleRepository,
				  NormalizedLibelleExtraRepository normalizedLibelleExtraRepository) {
		this.blocContenuRepository = blocContenuRepository;
		this.normalizedContEditorialSentenceRepository = normalizedContEditorialSentenceRepository;
		this.normalizedLibelleRepository = normalizedLibelleRepository;
		this.normalizedLibelleExtraRepository = normalizedLibelleExtraRepository;
	}


	public void generateSentencesRelationships(int wordCount) {
		List<NormalizedSentenceDto> normalizedContEditorialSentenceList =
				normalizedContEditorialSentenceRepository.findIdAndSentenceWithWordCountGreaterThanEqual(wordCount);

		Path libelleOut = Paths.get(LIBELLE_MATCHES_OUT);
		Path libelleExtraOut = Paths.get(LIBELLE_EXTRA_MATCHES_OUT);

		try (BufferedWriter writerLibelle = Files.newBufferedWriter(libelleOut, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			 BufferedWriter writerLibelleExtra = Files.newBufferedWriter(libelleExtraOut, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			 ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()
		) {
			// semaphore: limit tasks in flight to avoid unbounded in-memory queueing
			Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);

			writeHeader(writerLibelle, writerLibelleExtra);

			// process NormalizedLibelleExtra
			processEntities(pageable -> normalizedLibelleExtraRepository.findAllByWordCountGreaterThanEqual(wordCount, pageable),
					NormalizedLibelleExtra::getNormalizedLibelle, NormalizedLibelleExtra::getId,
					writerLibelleExtra, normalizedContEditorialSentenceList, exec, semaphore);

			// process NormalizedLibelle
			processEntities(pageable -> normalizedLibelleRepository.findAllByWordCountGreaterThanEqual(wordCount, pageable),
					NormalizedLibelle::getNormalizedLibelle, NormalizedLibelle::getId,
					writerLibelle, normalizedContEditorialSentenceList, exec, semaphore);

			// shutdown executor
			exec.shutdown();

		} catch (Exception e) {
			throw new RuntimeException("Error counting occurrences", e);
		}
	}

	private void writeHeader(BufferedWriter writerLibelle, BufferedWriter writerLibelleExtra) throws IOException {
		writerLibelle.write("libelleId;cont_editorial_id");
		writerLibelle.newLine();
		writerLibelleExtra.write("libelleId;cont_editorial_id");
		writerLibelleExtra.newLine();
	}

	// Generic processor for paged entities that expose a normalized libelle string and an id
	private <T> void processEntities(Function<PageRequest, Page<T>> pageFetcher,
									 Function<T, String> normalizedGetter,
									 Function<T, Integer> idGetter,
									 BufferedWriter writer,
									 List<NormalizedSentenceDto> sentencesList,
									 ExecutorService exec,
									 Semaphore semaphore) throws Exception {
		int page = 0;
		Page<T> pageResp;

		do {
			PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
			pageResp = pageFetcher.apply(pageable);

			List<Future<?>> futures = new ArrayList<>();

			for (T entity : pageResp.getContent()) {
				semaphore.acquire();

				Future<?> f = exec.submit(() -> {
					try {
						String normalized = normalizedGetter.apply(entity);
						Integer libelleId = idGetter.apply(entity);
						List<Integer> matchingIds = findSentenceMatches(normalized, sentencesList);

						if (matchingIds != null) {
							printSentenceRelationships(libelleId, matchingIds, writer);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
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
		} while (pageResp.hasNext());
	}

	private List<Integer> findSentenceMatches(String normalizedLibelleSentence,
											  List<NormalizedSentenceDto> normalizedContEditorialSentenceList) {
		if (normalizedLibelleSentence == null) return null;

		List<NormalizedSentenceDto> matches = new ArrayList<>();
		for (NormalizedSentenceDto s : normalizedContEditorialSentenceList) {
			if (normalizedLibelleSentence.contains(s.normalizedSentence())) {
				matches.add(s);
			}
		}

		return removeDuplicatedMatchesAndGetIds(matches);
	}

	private List<Integer> removeDuplicatedMatchesAndGetIds(List<NormalizedSentenceDto> matches) {
		if (matches == null || matches.isEmpty()) return null;

		if (matches.size() == 1) {
			return List.of(matches.getFirst().id());
		}

		// keep longest-first so that shorter sentences contained in longer ones are excluded
		matches.sort((a, b) -> Integer.compare(b.normalizedSentence().length(), a.normalizedSentence().length()));

		List<Integer> filtered = new ArrayList<>();
		filtered.add(matches.getFirst().id()); // add the longest one

		for (int i = 1; i < matches.size(); i++) {
			boolean contained = false;
			NormalizedSentenceDto candidate = matches.get(i);

			for (int j = 0; j < i; j++) {
				if (matches.get(j).normalizedSentence().contains(candidate.normalizedSentence())) {
					contained = true;
					break;
				}
			}
			if (!contained) filtered.add(candidate.id());
		}

		return filtered;
	}

	private void printSentenceRelationships(Integer libelleId, List<Integer> matchingIds, BufferedWriter writer) throws IOException {
		for (Integer matchingId : matchingIds) {
			synchronized (sentenceRelationshipLock) {
				writer.write(libelleId + SEPARATOR + matchingId);
				writer.newLine();
			}
		}
	}


	/**
	 * Generate relationships between BlocContenu entries and normalized libelle / normalized libelle extra entries,
	 * exporting the relationships result to a csv file.
	 * csv file format: blocXmlId;libelleId;libelleExtraId
	 */
	public void generateBlocContenuRelationships() throws Exception {
		Map<String, Integer> libelleMap = initializeLibelleMap();
		Map<String, Integer> libelleExtraMap = initializeLibelleExtraMap();

		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of(BLOC_CONTENU_OUT), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
			 BufferedWriter writerNotFound = Files.newBufferedWriter(Path.of(BLOC_CONTENU_NOT_FOUND), StandardOpenOption.CREATE,
					 StandardOpenOption.TRUNCATE_EXISTING)) {
			int blocPageIndex = 0;
			Page<BlocContenu> blocPage;

			writer.write("blocXmlId;libelleId;libelleExtraId");
			writer.newLine();

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
									writeBlocContenuRelationship(b.getId(), phrase, writer, writerNotFound, libelleMap, libelleExtraMap);
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

	private Map<String, Integer> initializeLibelleMap() {
		List<NormalizedLibelle> list = normalizedLibelleRepository.findAll();
		Map<String, Integer> map = new HashMap<>(list.size());
		for (NormalizedLibelle libelle : list) {
			map.put(trimAndLowerCaseAndRemoveLineBreaks(libelle.getOriginalLibelle()), libelle.getId());
		}

		return map;
	}

	private Map<String, Integer> initializeLibelleExtraMap() {
		List<NormalizedLibelleExtra> list = normalizedLibelleExtraRepository.findAll();
		Map<String, Integer> map = new HashMap<>(list.size());
		for (NormalizedLibelleExtra libelle : list) {
			map.put(trimAndLowerCaseAndRemoveLineBreaks(libelle.getOriginalLibelle()), libelle.getId());
		}

		return map;
	}

	private void writeBlocContenuRelationship(Integer blocXmlId, String phrase, BufferedWriter writer, BufferedWriter writerNotFound,
											  Map<String, Integer> libelleMap, Map<String, Integer> libelleExtraMap) throws Exception {
		phrase = trimAndLowerCaseAndRemoveLineBreaks(phrase);

		Integer libelleId = libelleMap.get(phrase);
		if (libelleId != null) {
			synchronized (blocContenuLock) {
				writer.write(blocXmlId + SEPARATOR + libelleId + SEPARATOR);
				writer.newLine();
			}
			return;
		}

		libelleId = libelleExtraMap.get(phrase);
		if (libelleId != null) {
			synchronized (blocContenuLock) {
				writer.write(blocXmlId + SEPARATOR + SEPARATOR + libelleId);
				writer.newLine();
			}
			return;
		}

		// not found in either
		synchronized (blocContenuNotFoundLock) {
			writerNotFound.write(phrase);
			writerNotFound.newLine();
		}
	}
}

