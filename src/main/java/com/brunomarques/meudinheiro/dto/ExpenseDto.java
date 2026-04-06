package com.brunomarques.meudinheiro.dto;

public record ExpenseDto(
        String name,
        Double value,
        String category,
        String date,
        String paymentType
) {}
