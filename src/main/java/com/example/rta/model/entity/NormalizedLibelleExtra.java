package com.example.rta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class NormalizedLibelleExtra {

	@Id
	@Column(name = "id", nullable = false)
	private Integer id;

	private String normalizedLibelle;

	private Integer wordCount;

	private String originalLibelle;

	protected NormalizedLibelleExtra() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getNormalizedLibelle() {
		return normalizedLibelle;
	}

	public void setNormalizedLibelle(String normalizedLibelle) {
		this.normalizedLibelle = normalizedLibelle;
	}

	public Integer getWordCount() {
		return wordCount;
	}

	public void setWordCount(Integer wordCount) {
		this.wordCount = wordCount;
	}

	public String getOriginalLibelle() {
		return originalLibelle;
	}

	public void setOriginalLibelle(String originalLibelle) {
		this.originalLibelle = originalLibelle;
	}
}

