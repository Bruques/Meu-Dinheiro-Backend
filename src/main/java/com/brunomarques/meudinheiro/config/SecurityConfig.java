package com.brunomarques.meudinheiro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Libera a comunicação com o Angular
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. Desativa proteção CSRF
                .csrf(csrf -> csrf.disable())
                // 3. CONFIGURAÇÃO DE REGRAS DE ACESSO
                .authorizeHttpRequests(auth -> auth
                        // LIBERAÇÃO WHATSAPP: Permite que a Meta acesse o webhook sem token
                        .requestMatchers("/api/whatsapp/**").permitAll()

                        // O RESTO CONTINUA BLOQUEADO: Exige autenticação para todo o resto
                        .anyRequest().authenticated()
                )
                // 4. Configuração do JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    // Configuração do CORS (Deixa o Angular conversar com o Java sem dar erro)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://meu-dinheiro-web-app.vercel.app"
        )); // URL do seu Angular
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}