package com.example.rta.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "mad_libelle_extra")
public class LibelleExtra {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private Integer id;

	private String libelleOriginal;

	protected LibelleExtra() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getLibelleOriginal() {
		return libelleOriginal;
	}

	public void setLibelleOriginal(String libelleOriginal) {
		this.libelleOriginal = libelleOriginal;
	}
}

