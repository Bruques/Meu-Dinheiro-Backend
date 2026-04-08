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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;

@Service
public class MeuDinheiroService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MeuDinheiroService() {
        this.objectMapper.registerModule(new JavaTimeModule());
    }


    public ExpenseDto processExpenseText(String text) throws Exception {
        // Mudar para o 2.5 quando puder, levemente mais barato
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + apiKey;

        String dataHoje = LocalDate.now().toString();
        int anoAtual = LocalDate.now().getYear();

        // Prompt Engineering rigoroso
        String prompt = "Você é um extrator de dados financeiros de altíssima precisão. " +
                "Sua única função é analisar o texto do usuário e retornar EXATAMENTE UM objeto JSON válido. " +
                "É ESTRITAMENTE PROIBIDO usar formatação Markdown (NÃO use ```json ou ```). Retorne apenas as chaves e valores. " +
                "--- " +
                "CONTEXTO TEMPORAL (INEXORÁVEL): " +
                "- O dia de HOJE é " + dataHoje + ". " +
                "- O ano atual é " + anoAtual + ". " +
                "--- " +
                "REGRAS DO JSON E TIPOS DE DADOS: " +
                "1. name: Nome curto e descritivo da despesa (String). " +
                "2. value: Apenas o valor numérico, usando ponto para decimais, sem a moeda (Number). " +
                "3. category: A categoria mais adequada, ex: Alimentação, Transporte (String). " +
                "4. date: Data no formato exato yyyy-MM-dd (String). " +
                "   - Se o usuário disser hoje ou NÃO mencionar tempo, use OBRIGATORIAMENTE " + dataHoje + ". " +
                "   - Se o texto citar dia e mês, mas omitir o ano, use OBRIGATORIAMENTE o ano " + anoAtual + ". " +
                "5. paymentType: Padronize para Crédito, Débito, Pix ou Cartão Benefício (String ou null). " +
                "   - Se não for mencionado, retorne o valor null nativo do JSON. " +
                "--- " +
                "ESTRUTURA ESPERADA (sem usar aspas na explicação, mas use no JSON real): " +
                "{ name: exemplo, value: 0.00, category: exemplo, date: yyyy-MM-dd, paymentType: null } " +
                "--- " +
                "Texto do usuário: " + text;

        // Montando o corpo da requisição no formato que o Gemini exige
        String requestBody = """
                {
                  "contents": [{
                    "parts":[{"text": "%s"}]
                  }]
                }
                """.formatted(prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Fazendo a chamada HTTP
        String responseBody = restTemplate.postForObject(url, request, String.class);

        // Navegando no JSON de resposta do Gemini para pegar o texto gerado
        JsonNode rootNode = objectMapper.readTree(responseBody);
        String aiJsonResponse = rootNode.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        // Limpando qualquer possível formatação extra que a IA mande por engano
        aiJsonResponse = aiJsonResponse.replace("```json", "").replace("```", "").trim();

        // Convertendo o JSON final para o nosso objeto Java (ExpenseDto)
        return objectMapper.readValue(aiJsonResponse, ExpenseDto.class);
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
}