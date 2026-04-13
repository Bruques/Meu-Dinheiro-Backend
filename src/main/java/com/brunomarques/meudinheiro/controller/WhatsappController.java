package com.brunomarques.meudinheiro.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

    // Invente uma senha forte aqui. Você vai precisar dela no painel da Meta.
    private final String VERIFY_TOKEN = "meu_token_secreto_123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        // A Meta envia um GET para testar se o seu servidor responde corretamente
        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            System.out.println("✅ Webhook validado com sucesso!");
            return ResponseEntity.ok(challenge);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleMessage(@RequestBody String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);

            // 1. Verifica se é um evento do WhatsApp Business
            if (rootNode.has("object") && rootNode.get("object").asText().equals("whatsapp_business_account")) {

                JsonNode entry = rootNode.path("entry").get(0);
                JsonNode changes = entry.path("changes").get(0);
                JsonNode value = changes.path("value");

                // 2. Filtra: Só queremos prosseguir se houver uma "message" real (ignora recibos de leitura)
                if (value.has("messages")) {
                    JsonNode messageNode = value.path("messages").get(0);

                    // Extrai o número do telefone do cliente (para sabermos de quem é o gasto)
                    String numeroCliente = messageNode.path("from").asText();

                    // Extrai o tipo da mensagem (text, audio, image, etc)
                    String tipoMensagem = messageNode.path("type").asText();

                    if ("text".equals(tipoMensagem)) {
                        String textoDoCliente = messageNode.path("text").path("body").asText();

                        System.out.println("📱 Nova mensagem de: " + numeroCliente);
                        System.out.println("💬 Texto: " + textoDoCliente);

                        // TODO: Passo 4 - Chamar a IA e salvar no banco!
                        // List<ExpenseDto> dtos = aiExpenseService.processExpenseText(textoDoCliente);
                        // salvarNoBanco(dtos);
                        // enviarMensagemDeConfirmacao(numeroCliente, "Gasto salvo!");
                    }
                }
            }

            // Sempre retorne 200 OK rápido para a Meta, senão ela acha que seu servidor caiu
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            System.err.println("Erro ao processar webhook: " + e.getMessage());
            return ResponseEntity.ok().build(); // Retorna 200 mesmo no erro para a Meta parar de reenviar
        }
    }
}
