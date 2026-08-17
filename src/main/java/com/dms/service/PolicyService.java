package com.dms.service;

import com.dms.dao.*;
import com.dms.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Backs the Document Policies screen: metadata standards, retention, automatic
 * classification, edit locks and the tag vocabulary.
 *
 * These are the Document Administrator's responsibilities from requirements
 * §4.1, and the retention half answers §9.2's "automated retention policy
 * enforcement".
 */
@Service
public class PolicyService {

    private final RetentionPolicyRepository retentionRepository;
    private final ClassificationRuleRepository classificationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository metadataRepository;
    private final DocumentTagRepository documentTagRepository;
    private final DocumentVersionRepository versionRepository;
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final DocumentLockService documentLockService;
    private final AuditLogService auditLogService;

    @Value("${app.document.lock.timeout-minutes:15}")
    private long lockTimeoutMinutes;

    public PolicyService(RetentionPolicyRepository retentionRepository,
                         ClassificationRuleRepository classificationRepository,
                         DocumentRepository documentRepository,
                         DocumentMetadataRepository metadataRepository,
                         DocumentTagRepository documentTagRepository,
                         DocumentVersionRepository versionRepository,
                         TagRepository tagRepository,
                         TagService tagService,
                         DocumentLockService documentLockService,
                         AuditLogService auditLogService) {
        this.retentionRepository = retentionRepository;
        this.classificationRepository = classificationRepository;
        this.documentRepository = documentRepository;
        this.metadataRepository = metadataRepository;
        this.documentTagRepository = documentTagRepository;
        this.versionRepository = versionRepository;
        this.tagRepository = tagRepository;
        this.tagService = tagService;
        this.documentLockService = documentLockService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------
    // Metadata standards
    // ------------------------------------------------------------------

    /** Every metadata field in use, with how widely it is applied. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> metadataKeys() {
        Map<String, Set<UUID>> documentsByKey = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<String>> valuesByKey = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (DocumentMetadata entry : metadataRepository.findAll()) {
            if (entry.getKey() == null || entry.getDocument() == null) {
                continue;
            }
            documentsByKey.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                    .add(entry.getDocument().getDocument_id());
            if (entry.getValue() != null) {
                valuesByKey.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(entry.getValue());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        documentsByKey.forEach((key, documents) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("documentCount", documents.size());
            row.put("distinctValues", valuesByKey.getOrDefault(key, Set.of()).size());
            List<String> samples = new ArrayList<>(valuesByKey.getOrDefault(key, Set.of()));
            Collections.sort(samples);
            row.put("sampleValues", samples.size() > 3 ? samples.subList(0, 3) : samples);
            result.add(row);
        });
        result.sort((a, b) -> ((Integer) b.get("documentCount")) - ((Integer) a.get("documentCount")));
        return result;
    }

    // ------------------------------------------------------------------
    // Tag vocabulary
    // ------------------------------------------------------------------

    /** Every tag with the number of documents carrying it. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tagUsage() {
        Map<UUID, Long> countByTag = new HashMap<>();
        for (DocumentTag link : documentTagRepository.findAll()) {
            countByTag.merge(link.getTagId(), 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Tag tag : tagRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tagId", tag.getTag_id());
            row.put("tagName", tag.getTag_name());
            row.put("documentCount", countByTag.getOrDefault(tag.getTag_id(), 0L));
            result.add(row);
        }
        result.sort((a, b) -> Long.compare((Long) b.get("documentCount"), (Long) a.get("documentCount")));
        return result;
    }

    /** Removes a tag from the vocabulary and from every document carrying it. */
    @Transactional
    public void deleteTag(UUID tagId, String actorIp) {
        documentTagRepository.findAll().stream()
                .filter(link -> tagId.equals(link.getTagId()))
                .forEach(documentTagRepository::delete);
        tagRepository.deleteById(tagId);
        auditLogService.createAuditLog("TAG_DELETED", tagId, actorIp, "SUCCESS");
    }

    // ------------------------------------------------------------------
    // Edit locks (US-30: an administrator can release a lock)
    // ------------------------------------------------------------------

    /** Documents currently held for editing, ignoring locks that have expired. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lockedDocuments() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(lockTimeoutMinutes);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Documents document : documentRepository.findAllActive()) {
            if (document.getLockedByUserId() == null || document.getLockedAt() == null) {
                continue;
            }
            boolean expired = document.getLockedAt().isBefore(staleBefore);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", document.getDocument_id());
            row.put("title", document.getTitle());
            row.put("lockedByUsername", document.getLockedByUsername());
            row.put("lockedAt", document.getLockedAt());
            row.put("expired", expired);
            result.add(row);
        }
        return result;
    }

    /** Force-releases someone else's lock. */
    @Transactional
    public void forceUnlock(UUID documentId, UUID actorId, String actorIp) {
        documentLockService.release(documentId, actorId, true, actorIp);
    }

    // ------------------------------------------------------------------
    // Retention
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RetentionPolicy> retentionPolicies() {
        return retentionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public RetentionPolicy saveRetentionPolicy(RetentionPolicy policy, String actorIp) {
        if (policy.getName() == null || policy.getName().isBlank()) {
            throw new IllegalArgumentException("A retention policy needs a name");
        }
        if (policy.getRetainDays() < 1) {
            throw new IllegalArgumentException("Retention period must be at least one day");
        }
        if (policy.getScope() == RetentionPolicy.Scope.TAG
                && (policy.getMatchValue() == null || policy.getMatchValue().isBlank())) {
            throw new IllegalArgumentException("A tag-scoped policy needs a tag name");
        }

        RetentionPolicy saved = retentionRepository.save(policy);
        auditLogService.createAuditLog("RETENTION_POLICY_SAVED", saved.getPolicyId(), actorIp, "SUCCESS");
        return saved;
    }

    @Transactional
    public void deleteRetentionPolicy(UUID policyId, String actorIp) {
        retentionRepository.deleteById(policyId);
        auditLogService.createAuditLog("RETENTION_POLICY_DELETED", policyId, actorIp, "SUCCESS");
    }

    /**
     * Documents a policy would act on right now. Shown before anything is
     * archived, because a retention rule that silently removes the wrong
     * documents is worse than no rule at all.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewRetention(UUID policyId) {
        RetentionPolicy policy = retentionRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Documents document : dueDocuments(policy)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", document.getDocument_id());
            row.put("title", document.getTitle());
            row.put("createdAt", document.getCreated_at());
            result.add(row);
        }
        return result;
    }

    /** Applies one policy now. Returns how many documents it acted on. */
    @Transactional
    public int runRetentionPolicy(UUID policyId, String actorIp) {
        RetentionPolicy policy = retentionRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        return applyPolicy(policy, actorIp);
    }

    /**
     * Nightly enforcement pass, an hour after the SLA monitor so the two do not
     * compete for the small connection pool.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void enforceRetentionPolicies() {
        for (RetentionPolicy policy : retentionRepository.findByActiveTrue()) {
            try {
                applyPolicy(policy, "scheduled");
            } catch (Exception e) {
                System.err.println("Retention policy '" + policy.getName() + "' failed: " + e.getMessage());
            }
        }
    }

    private int applyPolicy(RetentionPolicy policy, String actorIp) {
        List<Documents> due = dueDocuments(policy);

        if (policy.getAction() == RetentionPolicy.Action.ARCHIVE) {
            for (Documents document : due) {
                documentRepository.softDeleteById(document.getDocument_id());
                auditLogService.createAuditLog("RETENTION_ARCHIVED",
                        document.getDocument_id(), actorIp, "SUCCESS");
            }
        }

        policy.setLastRunAt(LocalDateTime.now());
        policy.setLastRunAffected(due.size());
        retentionRepository.save(policy);
        return due.size();
    }

    /** Active documents older than the policy's retention period and in its scope. */
    private List<Documents> dueDocuments(RetentionPolicy policy) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.getRetainDays());

        Set<UUID> inScope = null;
        if (policy.getScope() == RetentionPolicy.Scope.TAG) {
            inScope = new HashSet<>();
            Optional<Tag> tag = tagRepository.findByTagNameIgnoreCase(policy.getMatchValue());
            if (tag.isEmpty()) {
                return List.of();
            }
            for (DocumentTag link : documentTagRepository.findAll()) {
                if (tag.get().getTag_id().equals(link.getTagId())) {
                    inScope.add(link.getDocumentId());
                }
            }
        }

        List<Documents> due = new ArrayList<>();
        for (Documents document : documentRepository.findAllActive()) {
            if (document.getCreated_at() == null || !document.getCreated_at().isBefore(cutoff)) {
                continue;
            }
            if (inScope != null && !inScope.contains(document.getDocument_id())) {
                continue;
            }
            due.add(document);
        }
        return due;
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ClassificationRule> classificationRules() {
        return classificationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public ClassificationRule saveClassificationRule(ClassificationRule rule, String actorIp) {
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("A rule needs a name");
        }
        if (rule.getMatchPhrase() == null || rule.getMatchPhrase().isBlank()) {
            throw new IllegalArgumentException("A rule needs a phrase to look for");
        }
        if (rule.getApplyTag() == null || rule.getApplyTag().isBlank()) {
            throw new IllegalArgumentException("A rule needs a tag to apply");
        }

        ClassificationRule saved = classificationRepository.save(rule);
        auditLogService.createAuditLog("CLASSIFICATION_RULE_SAVED", saved.getRuleId(), actorIp, "SUCCESS");
        return saved;
    }

    @Transactional
    public void deleteClassificationRule(UUID ruleId, String actorIp) {
        classificationRepository.deleteById(ruleId);
        auditLogService.createAuditLog("CLASSIFICATION_RULE_DELETED", ruleId, actorIp, "SUCCESS");
    }

    /**
     * Runs every active rule over the whole library and tags what matches.
     *
     * @return number of tags applied
     */
    @Transactional
    public int applyClassificationRules(String actorIp) {
        List<ClassificationRule> rules = classificationRepository.findByActiveTrue();
        if (rules.isEmpty()) {
            return 0;
        }

        // OCR text per document, so a rule can match on content as well as title.
        Map<UUID, StringBuilder> textByDocument = new HashMap<>();
        for (DocumentVersions version : versionRepository.findAll()) {
            String text = version.getOcr_content();
            if (text != null && !text.isBlank()) {
                textByDocument.computeIfAbsent(version.getDocument_id(), id -> new StringBuilder())
                        .append('\n').append(text);
            }
        }

        int applied = 0;
        for (Documents document : documentRepository.findAllActive()) {
            StringBuilder ocr = textByDocument.get(document.getDocument_id());
            String haystack = ((document.getTitle() == null ? "" : document.getTitle())
                    + (ocr == null ? "" : ocr)).toLowerCase();

            for (ClassificationRule rule : rules) {
                if (!haystack.contains(rule.getMatchPhrase().toLowerCase())) {
                    continue;
                }
                Tag tag = tagService.getOrCreateTag(rule.getApplyTag());
                if (documentTagRepository.existsByDocumentIdAndTagId(
                        document.getDocument_id(), tag.getTag_id())) {
                    continue; // already carries it
                }
                tagService.addTagToDocument(document.getDocument_id(), rule.getApplyTag());
                rule.setTimesApplied(rule.getTimesApplied() + 1);
                classificationRepository.save(rule);
                applied++;
                auditLogService.createAuditLog("DOCUMENT_CLASSIFIED",
                        document.getDocument_id(), actorIp, "SUCCESS");
            }
        }
        return applied;
    }
}
