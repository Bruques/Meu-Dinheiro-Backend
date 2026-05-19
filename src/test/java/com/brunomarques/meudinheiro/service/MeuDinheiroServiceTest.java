package com.brunomarques.meudinheiro.service;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeuDinheiroServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private MeuDinheiroService service;

    @BeforeEach
    void setUp() {
        service = new MeuDinheiroService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
    }

    // calcularFluxoDeCaixa

    @Test
    void calcularFluxoDeCaixa_nonCreditPaymentReturnsPurchaseDate() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 10);

        assertEquals(purchaseDate, service.calcularFluxoDeCaixa(purchaseDate, "Débito", 21, 24));
        assertEquals(purchaseDate, service.calcularFluxoDeCaixa(purchaseDate, "Pix", 21, 24));
        assertEquals(purchaseDate, service.calcularFluxoDeCaixa(purchaseDate, "Cartão Benefício", 21, 24));
    }

    @Test
    void calcularFluxoDeCaixa_nullPaymentType_returnsPurchaseDate() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 10);

        assertEquals(purchaseDate, service.calcularFluxoDeCaixa(purchaseDate, null, 21, 24));
    }

    @Test
    void calcularFluxoDeCaixa_creditBeforeClosingDay_returnsCurrentMonthDueDate() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 10); // day 10, closing day 21
        LocalDate expected = LocalDate.of(2025, 6, 24);     // same month, due day 24

        assertEquals(expected, service.calcularFluxoDeCaixa(purchaseDate, "Crédito", 21, 24));
    }

    @Test
    void calcularFluxoDeCaixa_creditOnClosingDay_countsAsCurrentMonth() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 21); // day == closing day
        LocalDate expected = LocalDate.of(2025, 6, 24);

        assertEquals(expected, service.calcularFluxoDeCaixa(purchaseDate, "Crédito", 21, 24));
    }

    @Test
    void calcularFluxoDeCaixa_creditAfterClosingDay_returnsNextMonthDueDate() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 22); // day 22, closing day 21
        LocalDate expected = LocalDate.of(2025, 7, 24);     // next month, due day 24

        assertEquals(expected, service.calcularFluxoDeCaixa(purchaseDate, "Crédito", 21, 24));
    }

    @Test
    void calcularFluxoDeCaixa_creditIsCaseInsensitive() {
        LocalDate purchaseDate = LocalDate.of(2025, 6, 10);
        LocalDate expected = LocalDate.of(2025, 6, 24);

        assertEquals(expected, service.calcularFluxoDeCaixa(purchaseDate, "crédito", 21, 24));
        assertEquals(expected, service.calcularFluxoDeCaixa(purchaseDate, "CRÉDITO", 21, 24));
    }

    // processExpenseText

    @Test
    void processExpenseText_parsesGeminiResponseIntoExpenseDtoList() throws Exception {
        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[{\\"name\\":\\"Café\\",\\"value\\":5.50,\\"category\\":\\"Alimentação\\",\\"date\\":\\"2025-06-10\\",\\"paymentType\\":\\"Pix\\"}]"
                  }]
                }
              }]
            }
            """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(geminiResponse);

        List<ExpenseDto> result = service.processExpenseText("Café 5.50 no pix", List.of("Alimentação", "Outros"));

        assertEquals(1, result.size());
        assertEquals("Café", result.get(0).name());
        assertEquals(5.50, result.get(0).value());
        assertEquals("Alimentação", result.get(0).category());
        assertEquals("Pix", result.get(0).paymentType());
        assertEquals(LocalDate.of(2025, 6, 10), result.get(0).date());
    }

    @Test
    void processExpenseText_includesUserCategoriesInPrompt() throws Exception {
        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text": "[]"}]
                }
              }]
            }
            """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(geminiResponse);

        List<String> categories = List.of("Alimentação", "Lazer", "Saúde");
        service.processExpenseText("teste", categories);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(String.class));

        String requestBody = captor.getValue().getBody();
        assertTrue(requestBody.contains("Alimentação"));
        assertTrue(requestBody.contains("Lazer"));
        assertTrue(requestBody.contains("Saúde"));
    }

    @Test
    void processExpenseAudio_parsesGeminiResponseIntoExpenseDtoList() throws Exception {
        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[{\\"name\\":\\"Mercado\\",\\"value\\":120.00,\\"category\\":\\"Alimentação\\",\\"date\\":\\"2025-06-10\\",\\"paymentType\\":\\"Débito\\"}]"
                  }]
                }
              }]
            }
            """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(geminiResponse);

        byte[] fakeAudio = "fake-audio-bytes".getBytes();
        List<ExpenseDto> result = service.processExpenseAudio(fakeAudio, List.of("Alimentação", "Outros"));

        assertEquals(1, result.size());
        assertEquals("Mercado", result.get(0).name());
        assertEquals(120.00, result.get(0).value());
    }
}
