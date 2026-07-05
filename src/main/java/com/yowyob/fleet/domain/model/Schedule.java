package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité de domaine : Planning de service.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Un planning regroupe un ensemble d'affectations sur une période donnée
 * (journalière, hebdomadaire ou mensuelle) pour une flotte.
 *
 * Cycle de vie : DRAFT → PUBLISHED → ARCHIVED
 */
public class Schedule {

    public enum PeriodType {
        DAILY, WEEKLY, MONTHLY
    }

    public enum Status {
        DRAFT,      // En cours de construction
        PUBLISHED,  // Publié et visible par les conducteurs
        ARCHIVED    // Archivé (période passée)
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;
    private final UUID fleetId;
    private final UUID managerId;
    private String title;
    private final PeriodType periodType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private Status status;
    private String notes;
    private final LocalDateTime createdAt;
    private UUID createdBy;

    // ─────────────────────────────────────────────────────────────────────────

    public Schedule(UUID id,
                    UUID fleetId,
                    UUID managerId,
                    String title,
                    PeriodType periodType,
                    LocalDate startDate,
                    LocalDate endDate,
                    Status status,
                    String notes,
                    LocalDateTime createdAt,
                    UUID createdBy) {

        if (fleetId == null)
            throw new IllegalArgumentException("La flotte est obligatoire pour un planning.");
        if (managerId == null)
            throw new IllegalArgumentException("Le gestionnaire est obligatoire pour un planning.");
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Le titre du planning est obligatoire.");
        if (periodType == null)
            throw new IllegalArgumentException("Le type de période est obligatoire.");
        if (startDate == null || endDate == null)
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        if (endDate.isBefore(startDate))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");

        this.id = id;
        this.fleetId = fleetId;
        this.managerId = managerId;
        this.title = title;
        this.periodType = periodType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null ? status : Status.DRAFT;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.createdBy = createdBy;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /** Publie le planning — le rend visible par les conducteurs. */
    public void publish() {
        if (this.status == Status.ARCHIVED)
            throw new IllegalStateException("Un planning archivé ne peut pas être publié.");
        this.status = Status.PUBLISHED;
    }

    /** Archive le planning — fin de période. */
    public void archive() {
        this.status = Status.ARCHIVED;
    }

    /** Met à jour le titre et les notes. */
    public void update(String title, String notes) {
        if (this.status == Status.ARCHIVED)
            throw new IllegalStateException("Un planning archivé ne peut pas être modifié.");
        if (title != null && !title.isBlank()) this.title = title;
        this.notes = notes;
    }

    public boolean isDraft()     { return this.status == Status.DRAFT; }
    public boolean isPublished() { return this.status == Status.PUBLISHED; }
    public boolean isEditable()  { return this.status != Status.ARCHIVED; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()             { return id; }
    public UUID getFleetId()        { return fleetId; }
    public UUID getManagerId()      { return managerId; }
    public String getTitle()        { return title; }
    public PeriodType getPeriodType() { return periodType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate()   { return endDate; }
    public Status getStatus()       { return status; }
    public String getNotes()        { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy()      { return createdBy; }

    public void setId(UUID id)      { this.id = id; }
}
