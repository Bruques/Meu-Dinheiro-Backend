package com.brunomarques.meudinheiro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.brunomarques.meudinheiro.dto.ExpenseDto; // Ajuste o import
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Base64;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.List;

@Service
public class MeuDinheiroService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MeuDinheiroService() {
        this.objectMapper.registerModule(new JavaTimeModule());
    }


    public List<ExpenseDto> processExpenseText(String text, List<String> categoriasDoUsuario) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey;

        String dataHoje = LocalDate.now().toString();
        int anoAtual = LocalDate.now().getYear();

        // Transforma a lista de Java ["A", "B"] numa String formatada "A, B"
        String categoriasFormatadas = String.join(", ", categoriasDoUsuario);

        // 1. PROMPT ATUALIZADO: Com a trava de categorias
        String prompt = "Extraia os dados financeiros da frase abaixo. " +
                "Retorne SEMPRE um ARRAY de objetos JSON (lista). " +
                "Se houver apenas um gasto, retorne um array com um único objeto. " +
                "Se houver múltiplos gastos, separe-os em objetos distintos dentro do array. " +
                "O dia de HOJE é " + dataHoje + " e o ano é " + anoAtual + ". " +
                "Regras para cada objeto: " +
                "name: Nome curto da despesa. " +
                "value: Apenas número (ex: 50.00). " +
                "category: VOCÊ ESTÁ PROIBIDO de inventar categorias. Classifique o gasto escolhendo EXATAMENTE UMA destas opções: [" + categoriasFormatadas + "]. Se não se encaixar em nenhuma, escolha a mais próxima ou 'Outros'. " +
                "date: Data yyyy-MM-dd. " +
                "paymentType: Crédito, Débito, Pix, Cartão Benefício ou null. " +
                "Frase: " + text;

        String requestBody = """
        {
          "contents": [{
            "parts":[{"text": "%s"}]
          }],
          "generationConfig": {
            "response_mime_type": "application/json"
          }
        }
        """.formatted(prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        String responseBody = restTemplate.postForObject(url, request, String.class);

        JsonNode rootNode = objectMapper.readTree(responseBody);
        String aiJsonResponse = rootNode.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        // 2. LENDO A LISTA: Converte o JSON direto para um List<ExpenseDto>
        return objectMapper.readValue(aiJsonResponse, new TypeReference<List<ExpenseDto>>() {});
    }

    public LocalDate calcularFluxoDeCaixa(LocalDate dataCompra, String tipoPagamento) {
        int diaFechamento = 21;
        int diaVencimento = 24;

        // Se for nulo ou não for crédito, sai na mesma hora
        if (tipoPagamento == null || !tipoPagamento.trim().equalsIgnoreCase("Crédito")) {
            return dataCompra;
        }

        // LÓGICA DO CARTÃO DE CRÉDITO
        int diaDaCompra = dataCompra.getDayOfMonth();

        if (diaDaCompra <= diaFechamento) {
            return dataCompra.withDayOfMonth(diaVencimento);
        } else {
            return dataCompra.plusMonths(1).withDayOfMonth(diaVencimento);
        }
    }

    public List<ExpenseDto> processExpenseAudio(byte[] audioBytes) throws Exception {
        // 1. Transforma o áudio em Texto (Base64)
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey;

        // O mesmo prompt poderoso que você já usa, mas avisando que é um áudio
        String prompt = "Extraia os dados financeiros do áudio em anexo. Retorne SEMPRE um ARRAY de objetos JSON (lista). Se houver apenas um gasto, retorne um array com um único objeto. O dia de HOJE é 2026-04-13 e o ano é 2026. Regras para cada objeto: name: Nome curto. value: Apenas número (ex: 50.00). category: Categoria. date: Data yyyy-MM-dd. paymentType: Crédito, Débito, Pix ou null.";

        // 2. Monta o JSON especial Multimodal do Gemini
        String requestBody = """
                {
                  "contents": [{
                    "parts": [
                      {"text": "%s"},
                      {
                        "inline_data": {
                          "mime_type": "audio/ogg",
                          "data": "%s"
                        }
                      }
                    ]
                  }],
                  "generationConfig": {
                    "response_mime_type": "application/json"
                  }
                }
                """.formatted(prompt, base64Audio);

        // 3. Dispara para o Gemini (Mesma lógica que você já tem no método de texto)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // OBS: Substitua "SUA_URL_DO_GEMINI_AQUI" pela variável de URL que você já usa na sua classe
        String response = restTemplate.postForObject(geminiApiUrl, request, String.class);

        // 4. Converte a resposta do Gemini para a sua lista de DTOs
        return extrairDtosDaResposta(response);
    }

    private List<ExpenseDto> extrairDtosDaResposta(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        String jsonLimpo = root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        return objectMapper.readValue(jsonLimpo, new TypeReference<List<ExpenseDto>>() {});
    }
}