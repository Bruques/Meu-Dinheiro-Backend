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
        // Usamos a versão v20.0 da Graph API (padrão atual de produção)
        String url = "https://graph.facebook.com/v20.0/" + phoneId + "/messages";

        // Montando o JSON no formato exato que a Meta exige
        String requestBody = """
                {
                  "messaging_product": "whatsapp",
                  "recipient_type": "individual",
                  "to": "%s",
                  "type": "text",
                  "text": {
                    "preview_url": false,
                    "body": "%s"
                  }
                }
                """.formatted(numeroDestino, texto);

        // --- CÓDIGO DE DEBUG (RAIO-X) ---
        System.out.println("🔍 INICIANDO ENVIO PARA A META...");
        System.out.println("📱 Phone ID configurado: " + phoneId);

        if (token == null || token.isBlank()) {
            System.out.println("❌ ERRO CRÍTICO: O Token está VAZIO ou NULO no Railway!");
        } else {
            System.out.println("🔑 Token lido com sucesso. Começa com: " + token.substring(0, Math.min(token.length(), 15)) + "...");
            System.out.println("📏 Tamanho total do token: " + token.length() + " caracteres.");
        }
        // --------------------------------

        // Configurando os cabeçalhos com o Token de Segurança
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token); // Isso adiciona o "Authorization: Bearer SEU_TOKEN"

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            // Dispara a mensagem para o WhatsApp!
            restTemplate.postForObject(url, request, String.class);
            System.out.println("✅ Mensagem enviada com sucesso para: " + numeroDestino);
        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}
