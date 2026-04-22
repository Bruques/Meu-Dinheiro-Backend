package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    @Autowired
    private MeuDinheiroService meuDinheiroService;

    @Autowired
    private MeuDinheiroRepository expenseRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @PostMapping("/extract")
    public List<Expense> extractAndSaveExpense(@RequestBody Map<String, String> payload, @AuthenticationPrincipal Jwt jwt) {
        try {
            String userText = payload.get("text");
            String userId = jwt.getSubject();

            // 1. Busca o usuário no banco para pegar as categorias dele
            AppUser usuarioLogado = appUserRepository.findById(userId).orElse(new AppUser());
            List<String> categoriasDoUsuario = usuarioLogado.getCustomCategories();

            // Se por acaso a lista vier vazia (segurança extra), manda um padrão na hora
            if (categoriasDoUsuario == null || categoriasDoUsuario.isEmpty()) {
                categoriasDoUsuario = List.of("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Outros");
            }

            // 2. Passa a lista pro Gemini trabalhar engessado
            List<ExpenseDto> dtos = meuDinheiroService.processExpenseText(userText, categoriasDoUsuario);

            List<Expense> despesasParaSalvar = new ArrayList<>();

            // Faz um loop criando uma Entidade para cada DTO encontrado
            for (ExpenseDto dto : dtos) {
                Expense newExpense = new Expense();
                newExpense.setName(dto.name());
                newExpense.setValue(dto.value());
                newExpense.setCategory(dto.category());
                newExpense.setPaymentType(dto.paymentType());
                newExpense.setDate(dto.date());
                newExpense.setUserId(userId);

                Integer fechamento = usuarioLogado.getDiaFechamentoFatura() != null ? usuarioLogado.getDiaFechamentoFatura() : 21;
                Integer vencimento = usuarioLogado.getDiaVencimentoFatura() != null ? usuarioLogado.getDiaVencimentoFatura() : 24;

                LocalDate cobranca = meuDinheiroService.calcularFluxoDeCaixa(dto.date(), dto.paymentType(), fechamento, vencimento);
                newExpense.setDataCobranca(cobranca);

                despesasParaSalvar.add(newExpense);
            }

            // Salva todos de uma vez no banco (muito mais rápido!) e retorna a lista
            return expenseRepository.saveAll(despesasParaSalvar);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao processar e salvar os gastos em lote");
        }
    }

    @GetMapping
    public List<Expense> getAllExpenses(@AuthenticationPrincipal Jwt jwt) {
        // Agora não trazemos mais TUDO. Trazemos tudo DO USUÁRIO.
        return expenseRepository.findByUserId(jwt.getSubject());
    }

    @GetMapping("/mes")
    public List<Expense> getExpensesByMonth(
            @RequestParam int mes,
            @RequestParam int ano,
            @AuthenticationPrincipal Jwt jwt) {
        // Filtramos por Mês, Ano e USUÁRIO
        return expenseRepository.findByMesEAno(jwt.getSubject(), mes, ano);
    }

    @DeleteMapping("/{id}")
    public void deleteExpense(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        // Segurança extra: só deleta se o gasto pertencer ao usuário logado
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto não encontrado"));

        if (expense.getUserId().equals(jwt.getSubject())) {
            expenseRepository.deleteById(id);
        } else {
            throw new RuntimeException("Você não tem permissão para deletar este gasto");
        }
    }

    @PutMapping("/{id}")
    public Expense updateExpense(
            @PathVariable Long id,
            @RequestBody ExpenseDto dto,
            @AuthenticationPrincipal Jwt jwt) {

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto não encontrado"));

        String userId = jwt.getSubject();
        AppUser usuarioLogado = appUserRepository.findById(userId).orElse(new AppUser());

        // Segurança extra: verifica se é o dono
        if (!expense.getUserId().equals(jwt.getSubject())) {
            throw new RuntimeException("Acesso negado");
        }

        expense.setName(dto.name());
        expense.setValue(dto.value());
        expense.setCategory(dto.category());
        expense.setDate(dto.date());
        expense.setPaymentType(dto.paymentType());

        Integer fechamento = usuarioLogado.getDiaFechamentoFatura() != null ? usuarioLogado.getDiaFechamentoFatura() : 21;
        Integer vencimento = usuarioLogado.getDiaVencimentoFatura() != null ? usuarioLogado.getDiaVencimentoFatura() : 24;

        LocalDate novaCobranca = meuDinheiroService.calcularFluxoDeCaixa(dto.date(), dto.paymentType(), fechamento, vencimento);
        expense.setDataCobranca(novaCobranca);

        return expenseRepository.save(expense);
    }
}