package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

            // 1. A IA processa o texto e devolve o DTO (Extração pura)
            ExpenseDto dto = aiExpenseService.processExpenseText(userText);

            // 2. Transformamos o DTO na nossa Entidade do Banco de Dados
            Expense newExpense = new Expense();
            newExpense.setName(dto.name());
            newExpense.setValue(dto.value());
            newExpense.setCategory(dto.category());
            newExpense.setPaymentType(dto.paymentType());
            newExpense.setDate(dto.date()); // Data original da compra (String dd/MM/yyyy)

            // 3. A INTERCEPTAÇÃO: Calculamos a data de cobrança real
            // Chamamos o método que criamos para aplicar a regra do dia 21 e 24
            java.time.LocalDate cobranca = aiExpenseService.calcularFluxoDeCaixa(dto.date(), dto.paymentType());
            newExpense.setDataCobranca(cobranca);

            // 4. Salvamos no banco de dados e devolvemos o objeto salvo
            return expenseRepository.save(newExpense);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao processar e salvar o gasto");
        }
    }

    @GetMapping
    public java.util.List<com.brunomarques.meudinheiro.model.Expense> getAllExpenses() {
        // O findAll() vai no banco H2 e traz todos os registros criados!
        return expenseRepository.findAll();
    }

    @GetMapping("/mes")
    public List<Expense> getExpensesByMonth(@RequestParam int mes, @RequestParam int ano) {
        return expenseRepository.findByMesEAno(mes, ano);
    }

    // Rota para deletar um gasto pelo ID
    @DeleteMapping("/{id}")
    public void deleteExpense(@PathVariable Long id) {
        expenseRepository.deleteById(id);
    }

    // Rota para atualizar um gasto existente
    @PutMapping("/{id}")
    public com.brunomarques.meudinheiro.model.Expense updateExpense(
            @PathVariable Long id,
            @RequestBody com.brunomarques.meudinheiro.dto.ExpenseDto dto) {

        // 1. Busca o gasto existente no banco
        com.brunomarques.meudinheiro.model.Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto não encontrado"));

        // 2. Atualiza os dados básicos vindo do DTO
        expense.setName(dto.name());
        expense.setValue(dto.value());
        expense.setCategory(dto.category());
        expense.setDate(dto.date()); // Data da compra (String dd/MM/yyyy)
        expense.setPaymentType(dto.paymentType());

        // 3. A REGRA DE OURO: Recalcula a data de cobrança
        // Chamamos o serviço para garantir que, se a data ou o tipo mudaram,
        // a 'dataCobranca' seja atualizada automaticamente.
        java.time.LocalDate novaCobranca = aiExpenseService.calcularFluxoDeCaixa(dto.date(), dto.paymentType());
        expense.setDataCobranca(novaCobranca);

        // 4. Salva a entidade atualizada
        return expenseRepository.save(expense);
    }
}