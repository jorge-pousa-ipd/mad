package com.example.rta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class NormalizedContEditorialSentence {

	@Id
	@Column(nullable = false)
	private Integer id;

	private String normalizedSentence;

	private Integer wordCount;

	private String originalSentence;

	public NormalizedContEditorialSentence() {
	}

	public Integer getId() {
		return id;
	}

	protected void setId(Integer id) {
		this.id = id;
	}

	public String getNormalizedSentence() {
		return normalizedSentence;
	}

	public void setNormalizedSentence(String normalizedSentence) {
		this.normalizedSentence = normalizedSentence;
	}

	public Integer getWordCount() {
		return wordCount;
	}

	public void setWordCount(Integer wordCount) {
		this.wordCount = wordCount;
	}

	public String getOriginalSentence() {
		return originalSentence;
	}

	public void setOriginalSentence(String originalSentence) {
		this.originalSentence = originalSentence;
	}
}

