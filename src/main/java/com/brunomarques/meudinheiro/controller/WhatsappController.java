package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.AppUserService;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import com.brunomarques.meudinheiro.service.WhatsappService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {
    private final WhatsappService whatsAppService;
    private final MeuDinheiroService meuDinheiroService;
    private final MeuDinheiroRepository meuDinheiroRepository;

    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;

    // Invente uma senha forte aqui. Você vai precisar dela no painel da Meta.
    private final String VERIFY_TOKEN = "meu_token_secreto_123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsappController(WhatsappService whatsAppService,
                              MeuDinheiroService meuDinheiroService,
                              MeuDinheiroRepository meuDinheiroRepository,
                              AppUserService appUserService,
                              AppUserRepository appUserRepository) {
        this.whatsAppService = whatsAppService;
        this.meuDinheiroService = meuDinheiroService;
        this.meuDinheiroRepository = meuDinheiroRepository;
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
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

            // 1. FILTRO DA META: Ignora o que não for mensagem de usuário
            if (!rootNode.has("object") || !rootNode.get("object").asText().equals("whatsapp_business_account")) {
                return ResponseEntity.ok().build();
            }

            JsonNode value = rootNode.path("entry").get(0).path("changes").get(0).path("value");
            if (!value.has("messages")) {
                return ResponseEntity.ok().build(); // É só um recibo de leitura, ignora.
            }

            JsonNode messageNode = value.path("messages").get(0);
            String numeroCliente = messageNode.path("from").asText();
            String tipoMensagem = messageNode.path("type").asText();

            // Extrai o texto antecipadamente se for uma mensagem de texto
            String textoDoCliente = "";
            if ("text".equals(tipoMensagem)) {
                textoDoCliente = messageNode.path("text").path("body").asText().trim();
            }

            // ====================================================================================
            // 2. O SEGURANÇA (NOVO FLUXO DE IDENTIDADE E FIREBASE)
            // ====================================================================================
            Optional<AppUser> usuarioOpt = appUserRepository.findByWhatsappNumber(numeroCliente);

            if (usuarioOpt.isEmpty()) {
                // USUÁRIO DESCONHECIDO! Vamos checar se ele está tentando se vincular
                if ("text".equals(tipoMensagem) && textoDoCliente.matches("\\d{6}")) {
                    // Ele mandou 6 números exatos. Tenta validar o código!
                    boolean sucesso = appUserService.confirmarVinculo(numeroCliente, textoDoCliente);
                    if (sucesso) {
                        whatsAppService.enviarMensagem(numeroCliente, "✅ *Conta vinculada com sucesso!* \nAgora os gastos que você enviar aqui vão direto para o seu painel.");
                    } else {
                        whatsAppService.enviarMensagem(numeroCliente, "❌ Código inválido ou expirado. Gere um novo no seu painel web.");
                    }
                } else {
                    // Não é código e não tá cadastrado
                    whatsAppService.enviarMensagem(numeroCliente, "🤖 Olá! Vi que seu número ainda não está vinculado.\n\nAcesse o site Meu Dinheiro, vá em 'Perfil', gere um código de 6 dígitos e digite ele aqui no chat para liberar o acesso.");
                }

                // Encerra a requisição AQUI. Não chama a IA se não tiver conta!
                return ResponseEntity.ok().build();
            }

            // SE CHEGOU AQUI, O USUÁRIO EXISTE. PEGAMOS O UID DO FIREBASE!
            AppUser usuarioLogado = usuarioOpt.get();
            String firebaseUid = usuarioLogado.getFirebaseUid();

            // ====================================================================================
            // 3. O ATENDIMENTO (PROCESSA GASTOS EM TEXTO OU ÁUDIO)
            // ====================================================================================
            if ("text".equals(tipoMensagem)) {
                System.out.println("📱 Mensagem TEXTO de: " + numeroCliente);

                try {
                    List<ExpenseDto> dtos = meuDinheiroService.processExpenseText(textoDoCliente);
                    List<Expense> despesasParaSalvar = new ArrayList<>();
                    StringBuilder mensagemResposta = new StringBuilder("✅ *Gasto Registrado!*\n\n");

                    for (ExpenseDto dto : dtos) {
                        Expense newExpense = new Expense();
                        newExpense.setName(dto.name());
                        newExpense.setValue(dto.value());
                        newExpense.setCategory(dto.category());
                        newExpense.setPaymentType(dto.paymentType());
                        newExpense.setDate(dto.date());

                        // AQUI ESTÁ A MUDANÇA: Salvando o UID do Firebase em vez do telefone!
                        newExpense.setUserId(firebaseUid);
                        despesasParaSalvar.add(newExpense);

                        mensagemResposta.append("🛒 *Item:* ").append(dto.name()).append("\n")
                                .append("💰 *Valor:* R$ ").append(dto.value()).append("\n")
                                .append("💳 *Pagamento:* ").append(dto.paymentType()).append("\n\n");
                    }
                    meuDinheiroRepository.saveAll(despesasParaSalvar);
                    whatsAppService.enviarMensagem(numeroCliente, mensagemResposta.toString());

                } catch (Exception e) {
                    whatsAppService.enviarMensagem(numeroCliente, "Ops! 😅 Tive um problema para entender esse gasto. Pode tentar falar de outra forma?");
                }
            }
            else if ("audio".equals(tipoMensagem)) {
                System.out.println("🎤 Mensagem ÁUDIO de: " + numeroCliente);
                whatsAppService.enviarMensagem(numeroCliente, "🎧 Estou ouvindo seu áudio...");

                String mediaId = messageNode.path("audio").path("id").asText();
                String urlDeDownload = whatsAppService.obterUrlDaMidia(mediaId);

                if (urlDeDownload != null) {
                    byte[] audioBytes = whatsAppService.baixarArquivo(urlDeDownload);
                    if (audioBytes != null) {
                        try {
                            List<ExpenseDto> dtos = meuDinheiroService.processExpenseAudio(audioBytes);
                            List<Expense> despesasParaSalvar = new ArrayList<>();
                            StringBuilder mensagemResposta = new StringBuilder("✅ *Áudio Processado!*\n\n");

                            for (ExpenseDto dto : dtos) {
                                Expense newExpense = new Expense();
                                newExpense.setName(dto.name());
                                newExpense.setValue(dto.value());
                                newExpense.setCategory(dto.category());
                                newExpense.setPaymentType(dto.paymentType());
                                newExpense.setDate(dto.date());

                                // AQUI ESTÁ A MUDANÇA: Salvando o UID do Firebase em vez do telefone!
                                newExpense.setUserId(firebaseUid);
                                despesasParaSalvar.add(newExpense);

                                mensagemResposta.append("🛒 *Item:* ").append(dto.name()).append("\n")
                                        .append("💰 *Valor:* R$ ").append(dto.value()).append("\n")
                                        .append("💳 *Pagamento:* ").append(dto.paymentType()).append("\n\n");
                            }
                            meuDinheiroRepository.saveAll(despesasParaSalvar);
                            whatsAppService.enviarMensagem(numeroCliente, mensagemResposta.toString());

                        } catch (Exception e) {
                            whatsAppService.enviarMensagem(numeroCliente, "⚠️ Consegui ouvir, mas não entendi os valores. Pode repetir?");
                        }
                    }
                }
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            System.err.println("Erro crítico no webhook: " + e.getMessage());
            return ResponseEntity.ok().build();
        }
    }
}
