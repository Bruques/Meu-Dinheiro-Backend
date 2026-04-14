package com.brunomarques.meudinheiro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WhatsappService {

    @Value("${whatsapp.api.phone-id}")
    private String phoneId;

    @Value("${whatsapp.api.token}")
    private String token;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void enviarMensagem(String numeroDestino, String texto) {
        String url = "https://graph.facebook.com/v20.0/" + phoneId + "/messages";

        // 1. Criamos o corpo usando um Map ou String bem limpa
        // Certifique-se de que não há espaços extras ou quebras de linha estranhas
        String jsonPayload = """
            {
              "messaging_product": "whatsapp",
              "recipient_type": "individual",
              "to": "%s",
              "type": "text",
              "text": { "body": "%s" }
            }
            """.formatted(numeroDestino, texto.replace("\n", "\\n")); // Escapa quebras de linha

        // 2. Cabeçalhos explícitos
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        try {
            // Log de depuração para ver o que REALMENTE está saindo
            System.out.println("🚀 Enviando JSON para Meta: " + jsonPayload);

            restTemplate.postForObject(url, entity, String.class);
            System.out.println("✅ Mensagem entregue ao WhatsApp!");
        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    // 1. Pega o ID e descobre o Link de Download
    public String obterUrlDaMidia(String mediaId) {
        String url = "https://graph.facebook.com/v20.0/" + mediaId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Mudamos de JsonNode.class para String.class para evitar o erro de definição de tipo
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Agora usamos o objectMapper para navegar no texto do JSON manualmente
            JsonNode root = objectMapper.readTree(response.getBody());
            String urlDownload = root.path("url").asText();

            System.out.println("🔗 URL de download encontrada: " + urlDownload);
            return urlDownload;
        } catch (Exception e) {
            System.err.println("❌ Erro ao processar JSON da mídia: " + e.getMessage());
            return null;
        }
    }

    // 2. Pega o Link e baixa o arquivo em Bytes
    public byte[] baixarArquivo(String mediaUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token); // Sem o token, o download é bloqueado
        headers.add("User-Agent", "curl/7.64.1"); // Truquezinho: a Meta as vezes bloqueia Java puro

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(mediaUrl, HttpMethod.GET, entity, byte[].class);
            System.out.println("✅ Áudio baixado com sucesso! Tamanho: " + response.getBody().length + " bytes");
            return response.getBody();
        } catch (Exception e) {
            System.err.println("❌ Erro ao baixar o arquivo físico: " + e.getMessage());
            return null;
        }
    }
}
