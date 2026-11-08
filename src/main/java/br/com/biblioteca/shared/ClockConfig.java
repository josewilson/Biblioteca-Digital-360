package br.com.biblioteca.shared;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio único da aplicação (injetável, para testar prazos e atrasos). A zona define o que é "dia" de atraso. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${app.zone:America/Sao_Paulo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
