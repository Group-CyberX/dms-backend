package com.dms.rest;

import com.dms.security.SecurityUtils;
import com.dms.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The Settings screen.
 *
 * Personal preferences need no permission beyond being signed in - they only
 * affect the caller. Organisation settings are readable by anyone who can open
 * the screen and writable only with canEditSetting.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/me")
    public Map<String, Object> myPreferences() {
        return settingsService.userPreferences(SecurityUtils.currentUserId());
    }

    @PutMapping("/me")
    public Map<String, Object> saveMyPreferences(@RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        return settingsService.saveUserPreferences(
                SecurityUtils.currentUserId(), body, request.getRemoteAddr());
    }

    @GetMapping("/organisation")
    public Map<String, Object> organisation() {
        return settingsService.organisationSettings();
    }

    @PutMapping("/organisation")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditSetting')")
    public Map<String, Object> saveOrganisation(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        return settingsService.saveOrganisationSettings(
                body, SecurityUtils.currentUserId(), request.getRemoteAddr());
    }
}
