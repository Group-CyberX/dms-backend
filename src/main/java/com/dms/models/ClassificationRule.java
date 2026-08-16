package com.dms.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A rule that files a document under a tag automatically.
 *
 * Requirements §4.2.1 asks for intelligent document classification. This is the
 * deterministic half of that: a phrase to look for in the document's title and
 * extracted text, and the tag to apply when it is found. Rules are explicit and
 * auditable, which suits an approval trail better than a model whose reasoning
 * cannot be shown - a trained classifier is recorded as further work.
 */
@Entity
@Table(name = "classification_rules")
public class ClassificationRule {

    @Id
    @Column(name = "rule_id", updatable = false, nullable = false)
    private UUID ruleId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    /** Matched case-insensitively against the title and any OCR text. */
    @Column(name = "match_phrase", nullable = false)
    private String matchPhrase;

    /** Tag applied when the phrase is found. Created if it does not exist yet. */
    @Column(name = "apply_tag", nullable = false)
    private String applyTag;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "times_applied", nullable = false)
    private int timesApplied = 0;

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMatchPhrase() { return matchPhrase; }
    public void setMatchPhrase(String matchPhrase) { this.matchPhrase = matchPhrase; }

    public String getApplyTag() { return applyTag; }
    public void setApplyTag(String applyTag) { this.applyTag = applyTag; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getTimesApplied() { return timesApplied; }
    public void setTimesApplied(int timesApplied) { this.timesApplied = timesApplied; }
}
