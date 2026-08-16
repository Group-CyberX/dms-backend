package com.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
// Enables the daily SLA sweep in SlaMonitorService, which tells approvers when
// a workflow they are holding has gone past its due date.
@org.springframework.scheduling.annotation.EnableScheduling
public class DmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmsBackendApplication.class, args);
    }

}
