package com.dms.rest;

import com.dms.dto.ApproverOptionDTO;
import com.dms.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ApproverController {

    private final UserService userService;

    public ApproverController(UserService userService) {
        this.userService = userService;
    }

    // Get list of available approvers
    @GetMapping
    public List<ApproverOptionDTO> getApproverOptions() {
        return userService.getApproverOptions();
    }

    // Get currently logged-in user details
    @GetMapping("/me")
    public ApproverOptionDTO getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        // If no user is authenticated
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized");
        }

        return userService.getCurrentUserSummary(userDetails.getUsername());
    }
}