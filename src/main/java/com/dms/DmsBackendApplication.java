package com.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmsBackendApplication.class, args);
    }

}
