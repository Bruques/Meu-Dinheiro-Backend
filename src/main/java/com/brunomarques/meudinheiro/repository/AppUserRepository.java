package com.brunomarques.meudinheiro.repository;

import com.brunomarques.meudinheiro.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    // O Webhook vai usar isso para achar quem é o dono do número que mandou o áudio/texto
    Optional<AppUser> findByWhatsappNumber(String whatsappNumber);

    // O Webhook vai usar isso para checar se alguém mandou o código secreto de 6 dígitos
    Optional<AppUser> findByVerificationCode(String verificationCode);
}