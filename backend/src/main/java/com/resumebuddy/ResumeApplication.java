package com.resumebuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResumeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeApplication.class, args);
    }
}