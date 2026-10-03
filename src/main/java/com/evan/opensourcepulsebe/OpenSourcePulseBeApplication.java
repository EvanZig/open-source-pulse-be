package com.evan.opensourcepulsebe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenSourcePulseBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenSourcePulseBeApplication.class, args);
    }

}
