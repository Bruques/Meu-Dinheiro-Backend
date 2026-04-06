package com.brunomarques.meudinheiro.config; // Ajuste para o nome do seu pacote

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Libera para todos os endpoints da nossa API
                        .allowedOrigins("http://localhost:4200") // Permite o Angular
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Permite esses verbos
                        .allowedHeaders("*") // Permite qualquer cabeçalho
                        .allowCredentials(false);
            }
        };
    }
}