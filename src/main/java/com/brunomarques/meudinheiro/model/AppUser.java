package com.brunomarques.meudinheiro.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_categories", joinColumns = @JoinColumn(name = "firebase_uid"))
    @Column(name = "category_name")
    private List<String> customCategories = new ArrayList<>();

    @Column(name = "dia_fechamento_fatura")
    private Integer diaFechamentoFatura = 10;

    @Column(name = "dia_vencimento_fatura")
    private Integer diaVencimentoFatura = 17;

}