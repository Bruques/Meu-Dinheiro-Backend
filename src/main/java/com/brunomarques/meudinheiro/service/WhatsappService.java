package com.brunomarques.meudinheiro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsappService {

    @Value("${whatsapp.api.phone-id}")
    private String phoneId;

    @Value("${whatsapp.api.token}")
    private String token;

    private final RestTemplate restTemplate = new RestTemplate();

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
}
