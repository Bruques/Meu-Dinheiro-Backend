package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import com.brunomarques.meudinheiro.service.WhatsappService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {
    private final WhatsappService whatsAppService;
    private final MeuDinheiroService meuDinheiroService;
    private final MeuDinheiroRepository meuDinheiroRepository;

    // Invente uma senha forte aqui. Você vai precisar dela no painel da Meta.
    private final String VERIFY_TOKEN = "meu_token_secreto_123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsappController(WhatsappService whatsAppService,
                              MeuDinheiroService meuDinheiroService,
                              MeuDinheiroRepository meuDinheiroRepository) {
        this.whatsAppService = whatsAppService;
        this.meuDinheiroService = meuDinheiroService;
        this.meuDinheiroRepository = meuDinheiroRepository;
    }

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

                        System.out.println("📱 Mensagem de: " + numeroCliente);
                        System.out.println("💬 Texto: " + textoDoCliente);

                        try {
                            // 1. Manda a frase do Zap pro Gemini processar
                            List<ExpenseDto> dtos = meuDinheiroService.processExpenseText(textoDoCliente);

                            List<Expense> despesasParaSalvar = new ArrayList<>();

                            // 2. Começa a montar a resposta (O Recibo do WhatsApp)
                            StringBuilder mensagemResposta = new StringBuilder("✅ *Gasto Registrado!*\n\n");

                            // 3. Transforma o que a IA entendeu em dados pro Banco
                            for (ExpenseDto dto : dtos) {
                                Expense newExpense = new Expense();
                                newExpense.setName(dto.name());
                                newExpense.setValue(dto.value());
                                newExpense.setCategory(dto.category());
                                newExpense.setPaymentType(dto.paymentType());
                                newExpense.setDate(dto.date());

                                // AQUI É O SEGREDO DO MVP: O número do Zap vira o ID do usuário
                                newExpense.setUserId(numeroCliente);

                                despesasParaSalvar.add(newExpense);

                                // Adiciona o item formatado no "recibo"
                                mensagemResposta.append("🛒 *Item:* ").append(dto.name()).append("\n")
                                        .append("💰 *Valor:* R$ ").append(dto.value()).append("\n")
                                        .append("🏷️ *Categoria:* ").append(dto.category()).append("\n")
                                        .append("💳 *Pagamento:* ").append(dto.paymentType()).append("\n\n");
                            }

                            // 4. Salva tudo de uma vez no Banco de Dados
                            meuDinheiroRepository.saveAll(despesasParaSalvar);

                            // 5. Manda a mensagem bonitinha de volta pro usuário
                            whatsAppService.enviarMensagem(numeroCliente, mensagemResposta.toString());

                        } catch (Exception e) {
                            System.err.println("❌ Erro na IA: " + e.getMessage());
                            // Se a IA não entender, ou der erro, avisa o usuário com educação
                            whatsAppService.enviarMensagem(numeroCliente, "Ops! 😅 Tive um problema para entender esse gasto. Pode tentar falar de outra forma?");
                        }
                    }

                    if ("audio".equals(tipoMensagem)) {
                        System.out.println("🎤 Mensagem de ÁUDIO recebida de: " + numeroCliente);

                        // 1. Pega o ID e busca a URL
                        String mediaId = messageNode.path("audio").path("id").asText();
                        whatsAppService.enviarMensagem(numeroCliente, "🎧 Estou ouvindo seu áudio, só um segundo...");

                        String urlDeDownload = whatsAppService.obterUrlDaMidia(mediaId);

                        if (urlDeDownload != null) {
                            byte[] audioBytes = whatsAppService.baixarArquivo(urlDeDownload);

                            if (audioBytes != null) {
                                try {
                                    // --- AQUI A MÁGICA ACONTECE ---
                                    // 2. Manda os bytes para o Gemini processar
                                    List<ExpenseDto> dtos = meuDinheiroService.processExpenseAudio(audioBytes);

                                    List<Expense> despesasParaSalvar = new ArrayList<>();
                                    StringBuilder mensagemResposta = new StringBuilder("✅ *Áudio Processado com Sucesso!*\n\n");

                                    // 3. Organiza os dados para o banco e para o recibo
                                    for (ExpenseDto dto : dtos) {
                                        Expense newExpense = new Expense();
                                        newExpense.setName(dto.name());
                                        newExpense.setValue(dto.value());
                                        newExpense.setCategory(dto.category());
                                        newExpense.setPaymentType(dto.paymentType());
                                        newExpense.setDate(dto.date());
                                        newExpense.setUserId(numeroCliente);

                                        despesasParaSalvar.add(newExpense);

                                        mensagemResposta.append("🛒 *Item:* ").append(dto.name()).append("\n")
                                                .append("💰 *Valor:* R$ ").append(dto.value()).append("\n")
                                                .append("🏷️ *Categoria:* ").append(dto.category()).append("\n")
                                                .append("💳 *Pagamento:* ").append(dto.paymentType()).append("\n\n");
                                    }

                                    // 4. Salva no banco e responde o usuário
                                    meuDinheiroRepository.saveAll(despesasParaSalvar);
                                    whatsAppService.enviarMensagem(numeroCliente, mensagemResposta.toString());

                                } catch (Exception e) {
                                    System.err.println("❌ Erro ao processar IA do Áudio: " + e.getMessage());
                                    whatsAppService.enviarMensagem(numeroCliente, "⚠️ Consegui ouvir, mas não entendi os valores. Pode repetir de forma mais clara?");
                                }
                            } else {
                                whatsAppService.enviarMensagem(numeroCliente, "❌ Erro ao baixar o arquivo de áudio.");
                            }
                        }
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
