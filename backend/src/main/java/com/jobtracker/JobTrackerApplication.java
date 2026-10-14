package com.jobtracker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
@SpringBootApplication
@ConfigurationPropertiesScan
public class JobTrackerApplication {
    public static void main(String[] args) { SpringApplication.run(JobTrackerApplication.class, args); }
}
