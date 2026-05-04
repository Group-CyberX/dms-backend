package com.dms.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test/hello")
    public String hello() {
        return "Protected endpoint accessed successfully";
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/admin/test")
    public String testAdmin(){
        return "Only admin";
    }
}