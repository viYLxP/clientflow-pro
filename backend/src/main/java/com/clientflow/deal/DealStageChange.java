package com.clientflow.deal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One row per stage change (append-only history). The first row of a deal has fromStage = null.
 * صف لكل تغيير مرحلة (سجل يُضاف له فقط). أول صف للصفقة يكون fromStage = null.
 */
@Entity
@Table(name = "deal_stage_change")
public class DealStageChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private DealStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 20)
    private DealStage toStage;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected DealStageChange() {
    }

    public DealStageChange(Deal deal, DealStage fromStage, DealStage toStage, String changedBy, Instant changedAt) {
        this.deal = deal;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Deal getDeal() { return deal; }
    public DealStage getFromStage() { return fromStage; }
    public DealStage getToStage() { return toStage; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
