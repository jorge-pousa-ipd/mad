package com.example.rta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mad_bloccontenu")
public class BlocContenu {

	@Id
	@Column(nullable = false)
	private Integer id;

	@Column(nullable = false)
	private Integer idunitecontenu;

	@Column(nullable = false)
	private Integer idetude;

	private String titre;

	@Column(nullable = false)
	private String blocxml;

	private String applicabilitevehicule;


	protected BlocContenu() {
	}


	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getIdunitecontenu() {
		return idunitecontenu;
	}

	public void setIdunitecontenu(Integer idUnitecontenu) {
		this.idunitecontenu = idUnitecontenu;
	}

	public Integer getIdetude() {
		return idetude;
	}

	public void setIdetude(Integer idetude) {
		this.idetude = idetude;
	}

	public String getTitre() {
		return titre;
	}

	public void setTitre(String titre) {
		this.titre = titre;
	}

	public String getBlocxml() {
		return blocxml;
	}

	public void setBlocxml(String blocxml) {
		this.blocxml = blocxml;
	}

	public String getApplicabilitevehicule() {
		return applicabilitevehicule;
	}

	public void setApplicabilitevehicule(String applicabilitevehicule) {
		this.applicabilitevehicule = applicabilitevehicule;
	}
}
