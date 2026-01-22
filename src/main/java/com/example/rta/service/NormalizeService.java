package com.example.rta.service;

import com.example.rta.model.entity.ContEditorialSentence;
import com.example.rta.model.entity.LibelleExtra;
import com.example.rta.model.repository.ContentEditorialSentenceRepository;
import com.example.rta.model.entity.Libelle;
import com.example.rta.model.repository.LibelleExtraRepository;
import com.example.rta.model.repository.LibelleRepository;
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
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.function.Function;

import static util.Constants.PAGE_SIZE;
import static util.Constants.SEPARATOR;

@Service
public class NormalizeService {
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
	private static final String COLUMN_HEADER = "id;normalized_libelle;word_count";

	private static final String LIBELLE_OUT = "normalized_libelle.csv";
	private static final String LIBELLE_EXTRA_OUT = "normalized_libelle_extra.csv";
	private static final String CONT_EDITORIAL_OUT = "normalized_cont_editorial_sentence.csv";

	private final Object lock = new Object();

	private final ContentEditorialSentenceRepository contentEditorialSentenceRepository;
	private final LibelleRepository libelleRepository;
	private final LibelleExtraRepository libelleExtraRepository;

	public NormalizeService(ContentEditorialSentenceRepository contentEditorialSentenceRepository,
							LibelleRepository libelleRepository, LibelleExtraRepository libelleExtraRepository) {
		this.contentEditorialSentenceRepository = contentEditorialSentenceRepository;
		this.libelleRepository = libelleRepository;
		this.libelleExtraRepository = libelleExtraRepository;
	}


	/**
	 * Normalize entries from the libelle table and write the normalized sentences to a CSV file.
	 * csv file format: id;normalized_libelle;word_count
	 */
	public void normalizeLibelle() {
		normalizeEntities(libelleRepository::findAll, Libelle::getLibelleOriginal, Libelle::getId, LIBELLE_OUT);
	}


	/**
	 * Normalize entries from the libelle extra table and write the normalized sentences to a CSV file.
	 * csv file format: id;normalized_libelle;word_count
	 */
	public void normalizeLibelleExtra() {
		normalizeEntities(libelleExtraRepository::findAll, LibelleExtra::getLibelleOriginal, LibelleExtra::getId, LIBELLE_EXTRA_OUT);
	}

	/**
	 * Normalize entries from the content editorial table and write the normalized sentences to a CSV file.
	 * csv file format: id;normalized_sentence;word_count
	 */
	public void normalizeContentEditorial() {
		normalizeEntities(contentEditorialSentenceRepository::findAll, ContEditorialSentence::getSentence,
				ContEditorialSentence::getId, CONT_EDITORIAL_OUT);
	}

	// Generic pager + writer for any entity type that exposes a sentence/string and id
	private <T> void normalizeEntities(Function<PageRequest, Page<T>> pageFetcher, Function<T, String> sentenceGetter,
									   Function<T, Integer> idGetter, String outPath) {
		Path out = Paths.get(outPath);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writeCsvHeader(writer);

			int page = 0;
			Page<T> pageResp;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				pageResp = pageFetcher.apply(pageable);

				for (T e : pageResp.getContent()) {
					writeCsvLine(writer, sentenceGetter.apply(e), idGetter.apply(e));
				}

				page++;
			} while (pageResp.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized file: " + outPath, e);
		}
	}

	private void writeCsvHeader(BufferedWriter writer) throws IOException {
		writer.write(COLUMN_HEADER);
		writer.newLine();
	}

	private void writeCsvLine(BufferedWriter writer, String originalSentence, Integer id) throws IOException {
		String normalized = normalizeSentence(originalSentence);

		// count words: number of whitespace-separated tokens (0 if blank)
		int wordCount = normalized.split("\\s+").length;
		String line = id + SEPARATOR + normalized + SEPARATOR + wordCount;

		synchronized (lock) {
			writer.write(line);
			writer.newLine();
		}
	}

	private String normalizeSentence(String libelle) {
		// Normalize Unicode and remove diacritical marks (accents)
		String result = Normalizer.normalize(libelle, Normalizer.Form.NFD);
		result = DIACRITICS.matcher(result).replaceAll("");

		// Replace specified punctuation by spaces: ; : ? ! , « » " \r \n
		result = result.replaceAll("[\\r\\n;:?!«»\"]", " ");

		// Replace by spaces periods or commas that are NOT between two digits (preserve 1,234 and 3.14)
		result = result.replaceAll("(?<!\\d)[.,]|[.,](?!\\d)", " ");

		// Collapse multiple whitespaces into a single space and trim
		result = result.replaceAll("\\s+", " ").trim();

		// (), [] and {} are not replaced because they will be used to detect potential placeholders
		// ' is another special character in French, but it won't be replaced for a blank space because it would produce extra words

		return result.toLowerCase();
	}
}
