package de.hems.kasse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("de.hems.kasse")
public class KasseApplication {
    public static void main(String[] args) {
        SpringApplication.run(KasseApplication.class, args);
    }
}
