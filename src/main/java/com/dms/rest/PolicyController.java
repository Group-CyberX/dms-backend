package com.dms.rest;

import com.dms.models.ClassificationRule;
import com.dms.models.RetentionPolicy;
import com.dms.security.SecurityUtils;
import com.dms.service.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Document Policies screen: metadata standards, retention, classification
 * rules, edit locks and the tag vocabulary.
 *
 * Reading is gated on canViewPolicy and changes on canCreatePolicy /
 * canEditPolicy / canDeletePolicy, matching the permission catalogue the Role
 * Management screen already exposes.
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    // ---- Metadata standards ------------------------------------------

    @GetMapping("/metadata-keys")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<Map<String, Object>> metadataKeys() {
        return policyService.metadataKeys();
    }

    // ---- Tag vocabulary ----------------------------------------------

    @GetMapping("/tags")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<Map<String, Object>> tags() {
        return policyService.tagUsage();
    }

    @DeleteMapping("/tags/{tagId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeletePolicy')")
    public ResponseEntity<Void> deleteTag(@PathVariable UUID tagId, HttpServletRequest request) {
        policyService.deleteTag(tagId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // ---- Edit locks ---------------------------------------------------

    @GetMapping("/locks")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<Map<String, Object>> locks() {
        return policyService.lockedDocuments();
    }

    @PostMapping("/locks/{documentId}/release")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditPolicy')")
    public ResponseEntity<Void> releaseLock(@PathVariable UUID documentId, HttpServletRequest request) {
        policyService.forceUnlock(documentId, SecurityUtils.currentUserId(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // ---- Retention -----------------------------------------------------

    @GetMapping("/retention")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<RetentionPolicy> retentionPolicies() {
        return policyService.retentionPolicies();
    }

    @PostMapping("/retention")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreatePolicy')")
    public RetentionPolicy createRetentionPolicy(@RequestBody RetentionPolicy policy,
                                                 HttpServletRequest request) {
        policy.setPolicyId(UUID.randomUUID());
        return policyService.saveRetentionPolicy(policy, request.getRemoteAddr());
    }

    @PutMapping("/retention/{policyId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditPolicy')")
    public RetentionPolicy updateRetentionPolicy(@PathVariable UUID policyId,
                                                 @RequestBody RetentionPolicy policy,
                                                 HttpServletRequest request) {
        policy.setPolicyId(policyId);
        return policyService.saveRetentionPolicy(policy, request.getRemoteAddr());
    }

    @DeleteMapping("/retention/{policyId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeletePolicy')")
    public ResponseEntity<Void> deleteRetentionPolicy(@PathVariable UUID policyId,
                                                      HttpServletRequest request) {
        policyService.deleteRetentionPolicy(policyId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** What this policy would act on, without acting on it. */
    @GetMapping("/retention/{policyId}/preview")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<Map<String, Object>> previewRetention(@PathVariable UUID policyId) {
        return policyService.previewRetention(policyId);
    }

    @PostMapping("/retention/{policyId}/run")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditPolicy')")
    public Map<String, Object> runRetention(@PathVariable UUID policyId, HttpServletRequest request) {
        int affected = policyService.runRetentionPolicy(policyId, request.getRemoteAddr());
        return Map.of("affected", affected);
    }

    // ---- Classification -------------------------------------------------

    @GetMapping("/classification")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewPolicy')")
    public List<ClassificationRule> classificationRules() {
        return policyService.classificationRules();
    }

    @PostMapping("/classification")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreatePolicy')")
    public ClassificationRule createRule(@RequestBody ClassificationRule rule,
                                         HttpServletRequest request) {
        rule.setRuleId(UUID.randomUUID());
        return policyService.saveClassificationRule(rule, request.getRemoteAddr());
    }

    @PutMapping("/classification/{ruleId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditPolicy')")
    public ClassificationRule updateRule(@PathVariable UUID ruleId,
                                         @RequestBody ClassificationRule rule,
                                         HttpServletRequest request) {
        rule.setRuleId(ruleId);
        return policyService.saveClassificationRule(rule, request.getRemoteAddr());
    }

    @DeleteMapping("/classification/{ruleId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeletePolicy')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId, HttpServletRequest request) {
        policyService.deleteClassificationRule(ruleId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Runs every active rule over the library and tags what matches. */
    @PostMapping("/classification/apply")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditPolicy')")
    public Map<String, Object> applyRules(HttpServletRequest request) {
        int applied = policyService.applyClassificationRules(request.getRemoteAddr());
        return Map.of("tagsApplied", applied);
    }
}
