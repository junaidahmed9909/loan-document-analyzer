package com.junaid.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LoanAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanAnalyzerApplication.class, args);
    }
}
