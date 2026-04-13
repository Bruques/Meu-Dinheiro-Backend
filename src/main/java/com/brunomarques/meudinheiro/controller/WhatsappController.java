package com.brunomarques.meudinheiro.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

    // Invente uma senha forte aqui. Você vai precisar dela no painel da Meta.
    private final String VERIFY_TOKEN = "meu_token_secreto_123";

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
        // Por enquanto, apenas printamos o que chega para ver a estrutura
        System.out.println("📩 Mensagem recebida: " + payload);

        // Aqui é onde chamaremos o seu MeuDinheiroService.processExpenseText() futuramente

        return ResponseEntity.ok().build();
    }
}
