package com.fpt.printhub_3d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrintHub3DApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrintHub3DApplication.class, args);
    }

}
