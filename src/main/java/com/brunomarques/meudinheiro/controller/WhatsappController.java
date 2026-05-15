package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.dto.ExpenseDto;
import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.model.Expense;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.AppUserService;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import com.brunomarques.meudinheiro.service.WhatsappService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    // A Chave Secreta do App (Pegue no Painel da Meta > Configurações > Básico)
    @Value("${whatsapp.app.secret}")
    private String APP_SECRET;

    @Value("${whatsapp.verify.token}")
    private String VERIFY_TOKEN;

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

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            System.out.println("✅ Webhook validado com sucesso!");
            return ResponseEntity.ok(challenge);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleMessage(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody String payload) {

        if (signatureHeader == null || !isValidSignature(payload, signatureHeader)) {
            System.out.println("🚨 TENTATIVA DE ATAQUE BLOQUEADA: Assinatura inválida ou ausente!");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // Barra o hacker aqui!
        }

        try {
            JsonNode rootNode = objectMapper.readTree(payload);

            if (!rootNode.has("object") || !rootNode.get("object").asText().equals("whatsapp_business_account")) {
                return ResponseEntity.ok().build();
            }

            JsonNode value = rootNode.path("entry").get(0).path("changes").get(0).path("value");
            if (!value.has("messages")) {
                return ResponseEntity.ok().build();
            }

            JsonNode messageNode = value.path("messages").get(0);
            String numeroCliente = messageNode.path("from").asText();
            String tipoMensagem = messageNode.path("type").asText();

            String textoDoCliente = "";
            if ("text".equals(tipoMensagem)) {
                textoDoCliente = messageNode.path("text").path("body").asText().trim();
            }

            Optional<AppUser> usuarioOpt = appUserRepository.findByWhatsappNumber(numeroCliente);

            if (usuarioOpt.isEmpty()) {
                if ("text".equals(tipoMensagem) && textoDoCliente.matches("\\d{6}")) {
                    boolean sucesso = appUserService.confirmarVinculo(numeroCliente, textoDoCliente);
                    if (sucesso) {
                        whatsAppService.enviarMensagem(numeroCliente, "✅ *Conta vinculada com sucesso!* \nAgora os gastos que você enviar aqui vão direto para o seu painel.");
                    } else {
                        whatsAppService.enviarMensagem(numeroCliente, "❌ Código inválido ou expirado. Gere um novo no seu painel web.");
                    }
                } else {
                    whatsAppService.enviarMensagem(numeroCliente, "🤖 Olá! Vi que seu número ainda não está vinculado.\n\nAcesse o site Meu Dinheiro, vá em 'Perfil', gere um código de 6 dígitos e digite ele aqui no chat para liberar o acesso.");
                }
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
                    AppUser user = appUserRepository.findById(firebaseUid).get();
                    List<ExpenseDto> dtos = meuDinheiroService.processExpenseText(textoDoCliente, user.getCustomCategories());
                    List<Expense> despesasParaSalvar = new ArrayList<>();
                    StringBuilder mensagemResposta = new StringBuilder("✅ *Gasto Registrado!*\n\n");

                    for (ExpenseDto dto : dtos) {
                        Expense newExpense = new Expense();
                        newExpense.setName(dto.name());
                        newExpense.setValue(dto.value());
                        newExpense.setCategory(dto.category());
                        newExpense.setPaymentType(dto.paymentType());
                        newExpense.setDate(dto.date());

                        Integer fechamento = usuarioLogado.getDiaFechamentoFatura() != null ? usuarioLogado.getDiaFechamentoFatura() : 21;
                        Integer vencimento = usuarioLogado.getDiaVencimentoFatura() != null ? usuarioLogado.getDiaVencimentoFatura() : 24;

                        LocalDate cobranca = meuDinheiroService.calcularFluxoDeCaixa(dto.date(), dto.paymentType(), fechamento, vencimento);
                        newExpense.setDataCobranca(cobranca);

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
                            List<ExpenseDto> dtos = meuDinheiroService.processExpenseAudio(audioBytes, usuarioLogado.getCustomCategories());
                            List<Expense> despesasParaSalvar = new ArrayList<>();
                            StringBuilder mensagemResposta = new StringBuilder("✅ *Áudio Processado!*\n\n");

                            for (ExpenseDto dto : dtos) {
                                Expense newExpense = new Expense();
                                newExpense.setName(dto.name());
                                newExpense.setValue(dto.value());
                                newExpense.setCategory(dto.category());
                                newExpense.setPaymentType(dto.paymentType());
                                newExpense.setDate(dto.date());

                                Integer fechamento = usuarioLogado.getDiaFechamentoFatura() != null ? usuarioLogado.getDiaFechamentoFatura() : 21;
                                Integer vencimento = usuarioLogado.getDiaVencimentoFatura() != null ? usuarioLogado.getDiaVencimentoFatura() : 24;

                                LocalDate cobranca = meuDinheiroService.calcularFluxoDeCaixa(dto.date(), dto.paymentType(), fechamento, vencimento);
                                newExpense.setDataCobranca(cobranca);

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

    private boolean isValidSignature(String payload, String signatureHeader) {
        try {
            String[] parts = signatureHeader.split("=");
            if (parts.length != 2) return false;
            String expectedHash = parts[1];

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return expectedHash.equals(hexString.toString());

        } catch (Exception e) {
            System.out.println("Erro ao validar criptografia: " + e.getMessage());
            return false;
        }
    }
}