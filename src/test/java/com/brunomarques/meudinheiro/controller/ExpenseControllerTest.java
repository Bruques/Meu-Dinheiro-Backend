package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import com.brunomarques.meudinheiro.service.RateLimiterService;
import com.brunomarques.meudinheiro.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
class ExpenseControllerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeuDinheiroService meuDinheiroService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private MeuDinheiroRepository expenseRepository;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void ping_returns200() throws Exception {
        when(expenseRepository.count()).thenReturn(3L);

        mockMvc.perform(get("/api/expenses/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Online")));
    }

    @Test
    void getAllExpenses_semAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllExpenses_comAuth_retornaLista() throws Exception {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setName("Mercado");
        expense.setValue(50.0);
        expense.setUserId("uid-1");

        when(expenseRepository.findByUserId("uid-1")).thenReturn(List.of(expense));

        mockMvc.perform(get("/api/expenses")
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].value").value(50.0));
    }

    @Test
    void getExpensesByMonth_comAuth_retornaFiltrado() throws Exception {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setName("Aluguel");
        expense.setValue(1200.0);
        expense.setUserId("uid-1");

        when(expenseRepository.findByMesEAno("uid-1", 5, 2026)).thenReturn(List.of(expense));

        mockMvc.perform(get("/api/expenses/mes")
                        .param("mes", "5")
                        .param("ano", "2026")
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Aluguel"));
    }

    @Test
    void saveManualExpense_comAuth_salvaERetorna() throws Exception {
        Expense expense = new Expense();
        expense.setName("Café");
        expense.setValue(10.0);
        expense.setCategory("Alimentação");
        expense.setDate(LocalDate.of(2026, 5, 19));
        expense.setPaymentType("Pix");

        Expense saved = new Expense();
        saved.setId(1L);
        saved.setName("Café");
        saved.setValue(10.0);
        saved.setUserId("uid-1");

        when(rateLimiterService.tryConsumeManual("uid-1")).thenReturn(true);
        when(appUserRepository.findById("uid-1")).thenReturn(Optional.empty());
        when(meuDinheiroService.calcularFluxoDeCaixa(any(), any(), anyInt(), anyInt()))
                .thenReturn(LocalDate.of(2026, 5, 19));
        when(expenseRepository.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expense))
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Café"));
    }

    @Test
    void saveManualExpense_rateLimitExcedido_retorna429() throws Exception {
        Expense expense = new Expense();
        expense.setName("Café");
        expense.setValue(10.0);
        expense.setDate(LocalDate.of(2026, 5, 19));
        expense.setPaymentType("Pix");

        when(rateLimiterService.tryConsumeManual("uid-1")).thenReturn(false);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expense))
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void extractAndSave_textoLongo_retorna413() throws Exception {
        String longText = "x".repeat(251);

        when(rateLimiterService.tryConsumeAi("uid-1")).thenReturn(true);

        mockMvc.perform(post("/api/expenses/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + longText + "\"}")
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void deleteExpense_donoCorreto_deleta() throws Exception {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setUserId("uid-1");

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

        mockMvc.perform(delete("/api/expenses/1")
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk());

        verify(expenseRepository).deleteById(1L);
    }

    @Test
    void updateExpense_comAuth_atualizaERetorna() throws Exception {
        Expense existing = new Expense();
        existing.setId(1L);
        existing.setUserId("uid-1");
        existing.setName("Mercado");
        existing.setValue(100.0);

        ExpenseDto dto = new ExpenseDto("Supermercado", 120.0, "Alimentação",
                LocalDate.of(2026, 5, 19), "Débito");

        Expense updated = new Expense();
        updated.setId(1L);
        updated.setName("Supermercado");
        updated.setValue(120.0);
        updated.setUserId("uid-1");

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appUserRepository.findById("uid-1")).thenReturn(Optional.empty());
        when(meuDinheiroService.calcularFluxoDeCaixa(any(), any(), anyInt(), anyInt()))
                .thenReturn(LocalDate.of(2026, 5, 19));
        when(expenseRepository.save(any())).thenReturn(updated);

        mockMvc.perform(put("/api/expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Supermercado"))
                .andExpect(jsonPath("$.value").value(120.0));
    }
}
