package com.example.reconocimiento;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        Dotenv.configure().ignoreIfMissing().load().entries().forEach(e -> {
            if (System.getenv(e.getKey()) == null) System.setProperty(e.getKey(), e.getValue());
        });
        SpringApplication.run(App.class, args);
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
