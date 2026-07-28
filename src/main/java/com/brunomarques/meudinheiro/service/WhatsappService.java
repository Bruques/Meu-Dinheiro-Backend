package com.brunomarques.meudinheiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    @Value("${whatsapp.api.phone-id}")
    private String phoneId;

    @Value("${whatsapp.api.token}")
    private String token;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsappService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enviarMensagem(String numeroDestino, String texto) {
        String url = "https://graph.facebook.com/v20.0/" + phoneId + "/messages";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", numeroDestino);
        body.put("type", "text");
        body.put("text", Map.of("body", texto));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        try {
            String jsonPayload = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            log.debug("Enviando mensagem para Meta: {}", jsonPayload);
            restTemplate.postForObject(url, entity, String.class);
            log.info("Mensagem entregue ao WhatsApp para {}", numeroDestino);
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem via WhatsApp para {}", numeroDestino, e);
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

            log.debug("URL de download encontrada para mídia {}", mediaId);
            return urlDownload;
        } catch (Exception e) {
            log.error("Erro ao processar JSON da mídia {}", mediaId, e);
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
            log.debug("Áudio baixado com sucesso, {} bytes", response.getBody().length);
            return response.getBody();
        } catch (Exception e) {
            log.error("Erro ao baixar arquivo de mídia", e);
            return null;
        }
    }
}
