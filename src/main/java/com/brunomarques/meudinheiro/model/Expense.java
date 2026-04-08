package com.brunomarques.meudinheiro.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

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
    private LocalDate date;
    private String paymentType;
    @Column(name = "data_cobranca")
    private LocalDate dataCobranca;
}