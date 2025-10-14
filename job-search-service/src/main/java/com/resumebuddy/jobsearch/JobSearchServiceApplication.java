package com.resumebuddy.jobsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class JobSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSearchServiceApplication.class, args);
    }
}
