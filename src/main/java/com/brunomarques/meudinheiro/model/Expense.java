package com.brunomarques.meudinheiro.model; // Ajuste para o seu pacote

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(name = "expense_value")
    private Double value;
    private String category;
    @Column(name = "expense_date")
    private String date;
    private String paymentType;
}