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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class MeuDinheiroService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpenseDto processExpenseText(String text) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        // Passamos a data atual para a IA usar como referência caso o usuário diga "hoje" ou "ontem"
        String dataHoje = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Prompt Engineering rigoroso
        String prompt = "Você é um assistente financeiro. Extraia os dados do gasto a partir do texto abaixo. " +
                "Retorne APENAS um objeto JSON estrito, sem formatação markdown (não use ```json). " +
                "O JSON deve ter as chaves exatas: 'name' (string curta), 'value' (apenas números/decimais), " +
                "'category' (string curta com a melhor categoria), 'date' (formato dd/MM/yyyy. Use " + dataHoje + " se não especificado), " +
                "e 'paymentType' (string curta com o método de pagamento, ex: 'crédito', 'débito', 'pix', 'cartão de benefício'. ATENÇÃO: se o método de pagamento NÃO for mencionado no texto, o valor de 'paymentType' DEVE ser explicitamente null, sem aspas). " +
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
}