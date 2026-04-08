package com.brunomarques.meudinheiro.dto;
import java.time.LocalDate;

public record ExpenseDto(
        String name,
        Double value,
        String category,
        LocalDate date,
        String paymentType
) {}
