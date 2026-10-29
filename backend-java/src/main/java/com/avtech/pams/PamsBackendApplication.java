package com.avtech.pams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PamsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PamsBackendApplication.class, args);
	}

}
