package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    @Autowired
    private MeuDinheiroService aiExpenseService;

    @Autowired
    private MeuDinheiroRepository expenseRepository; // Injetamos o nosso repository aqui

    @PostMapping("/extract")
    public Expense extractAndSaveExpense(@RequestBody Map<String, String> payload) {
        try {
            String userText = payload.get("text");

            // 1. A IA processa o texto e devolve o DTO
            ExpenseDto dto = aiExpenseService.processExpenseText(userText);

            // 2. Transformamos o DTO na nossa Entidade do Banco de Dados
            Expense newExpense = new Expense();
            newExpense.setName(dto.name());
            newExpense.setValue(dto.value());
            newExpense.setCategory(dto.category());
            newExpense.setDate(dto.date());
            newExpense.setPaymentType(dto.paymentType());

            // 3. Salvamos no banco de dados e devolvemos o objeto salvo (agora com o ID gerado)
            return expenseRepository.save(newExpense);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao processar e salvar o gasto");
        }
    }
}