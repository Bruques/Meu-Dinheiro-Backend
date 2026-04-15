package com.brunomarques.meudinheiro.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "app_users") // Evitamos o nome "user" pois é palavra reservada em alguns bancos
public class AppUser {

    // O ID aqui NÃO é gerado automaticamente.
    // Nós vamos salvar exatamente o UID que vem do Firebase.
    @Id
    @Column(name = "firebase_uid")
    private String firebaseUid;

    private String email;

    @Column(name = "whatsapp_number", unique = true)
    private String whatsappNumber;

    // Campos temporários para fazer o vínculo com segurança
    @Column(name = "verification_code")
    private String verificationCode;

}