package com.vietnam.pji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PjiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PjiApplication.class, args);
	}

}
