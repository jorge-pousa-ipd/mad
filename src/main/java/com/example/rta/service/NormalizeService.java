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

import static util.Constants.PAGE_SIZE;
import static util.Constants.SEPARATOR;

@Service
public class NormalizeService {
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

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
		Path out = Paths.get(LIBELLE_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writeCsvHeader(writer, "id;normalized_libelle;word_count");

			int page = 0;
			Page<Libelle> libellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				libellePage = libelleRepository.findAll(pageable);

				for (Libelle libelle : libellePage.getContent()) {
					writeCsvLine(writer, libelle.getLibelleOriginal(), libelle.getId());
				}

				page++;
			} while (libellePage.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Libelle file", e);
		}
	}


	/**
	 * Normalize entries from the libelle extra table and write the normalized sentences to a CSV file.
	 * csv file format: id;normalized_libelle;word_count
	 */
	public void normalizeLibelleExtra() {
		Path out = Paths.get(LIBELLE_EXTRA_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			writeCsvHeader(writer, "id;normalized_libelle;word_count");

			int page = 0;
			Page<LibelleExtra> libellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				libellePage = libelleExtraRepository.findAll(pageable);

				for (LibelleExtra libelle : libellePage.getContent()) {
					writeCsvLine(writer, libelle.getLibelleOriginal(), libelle.getId());
				}

				page++;
			} while (libellePage.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Libelle Extra file", e);
		}
	}

	/**
	 * Normalize entries from the content editorial table and write the normalized sentences to a CSV file.
	 * csv file format: id;normalized_sentence;word_count
	 */
	public void normalizeContentEditorial() {
		Path out = Paths.get(CONT_EDITORIAL_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			writeCsvHeader(writer, "id;normalized_sentence;word_count");

			int page = 0;
			Page<ContEditorialSentence> contentEditorialServicePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				contentEditorialServicePage = contentEditorialSentenceRepository.findAll(pageable);

				for (ContEditorialSentence contEditorialSentence : contentEditorialServicePage.getContent()) {
					writeCsvLine(writer, contEditorialSentence.getSentence(), contEditorialSentence.getId());
				}

				page++;
			} while (contentEditorialServicePage.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Content Editorial file", e);
		}
	}


	private void writeCsvHeader(BufferedWriter writer, String header) throws IOException {
		writer.write(header);
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
