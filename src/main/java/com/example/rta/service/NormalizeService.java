package com.example.rta.service;

import com.example.rta.model.entity.ContentEditorialSentence;
import com.example.rta.model.repository.ContentEditorialSentenceRepository;
import com.example.rta.model.entity.Libelle;
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

@Service
public class NormalizeService {
	private static final Integer PAGE_SIZE = 25000;
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private static final String SEPARATOR = ";";
	private static final String LIBELLE_OUT = "normalized_libelles.csv";
	private static final String CONT_EDITORIAL_OUT = "normalized_cont_editorial.csv";

	private final ContentEditorialSentenceRepository contentEditorialSentenceRepository;
	private final LibelleRepository libelleRepository;

	public NormalizeService(ContentEditorialSentenceRepository contentEditorialSentenceRepository, LibelleRepository libelleRepository) {
		this.contentEditorialSentenceRepository = contentEditorialSentenceRepository;
		this.libelleRepository = libelleRepository;
	}


	public void normalizeLibelle() {
		Path out = Paths.get(LIBELLE_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			// header (semicolon-separated) - added word_count column
			writer.write("id;normalized_libelle;word_count");
			writer.newLine();

			int page = 0;
			Page<Libelle> libellePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				libellePage = libelleRepository.findAll(pageable);

				for (Libelle libelle : libellePage.getContent()) {
					String normalized = normalizeSentence(libelle.getLibelleOriginal());

					// count words: number of whitespace-separated tokens (0 if blank)
					int wordCount = normalized.split("\\s+").length;

					// write CSV line separated by semicolons; no sanitization as requested
					String line = libelle.getId() + SEPARATOR + normalized + SEPARATOR + wordCount;
					writer.write(line);
					writer.newLine();
				}

				page++;
			} while (libellePage.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Libelle file", e);
		}
	}

	public void normalizeContentEditorial() {
		Path out = Paths.get(CONT_EDITORIAL_OUT);

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			// header (semicolon-separated) - added word_count column
			writer.write("id;normalized_sentence;word_count");
			writer.newLine();

			int page = 0;
			Page<ContentEditorialSentence> contentEditorialServicePage;

			do {
				PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id"));
				contentEditorialServicePage = contentEditorialSentenceRepository.findAll(pageable);

				for (ContentEditorialSentence contentEditorialSentence : contentEditorialServicePage.getContent()) {
					String normalized = normalizeSentence(contentEditorialSentence.getSentence());

					// count words
					int wordCount = normalized.isBlank() ? 0 : normalized.split("\\s+").length;

					// write CSV line separated by semicolons; no sanitization as requested
					String line = contentEditorialSentence.getId() + SEPARATOR + normalized + SEPARATOR + wordCount;
					writer.write(line);
					writer.newLine();
				}

				page++;
			} while (contentEditorialServicePage.hasNext());

			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write normalized Content Editorial file", e);
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

		// Collapse multiple whitespace into single space and trim
		result = result.replaceAll("\\s+", " ").trim();

		// { and } are special cases; they indicate user typos
		// ' is another special character in French, won't be replaces for a blank space

		return result;
	}
}
