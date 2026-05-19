package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.repository.MeuDinheiroRepository;
import com.brunomarques.meudinheiro.service.AppUserService;
import com.brunomarques.meudinheiro.service.MeuDinheiroService;
import com.brunomarques.meudinheiro.service.RateLimiterService;
import com.brunomarques.meudinheiro.service.WhatsappService;
import org.junit.jupiter.api.Test;
import com.brunomarques.meudinheiro.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WhatsappController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "whatsapp.app.secret=test-secret",
        "whatsapp.verify.token=test-verify-token",
        "whatsapp.api.phone-id=test-phone-id",
        "whatsapp.api.token=test-api-token"
})
class WhatsappControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhatsappService whatsappService;

    @MockitoBean
    private MeuDinheiroService meuDinheiroService;

    @MockitoBean
    private MeuDinheiroRepository meuDinheiroRepository;

    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String computeHmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return "sha256=" + hex;
    }

    @Test
    void verifyWebhook_tokenValido_retornaChallenge() throws Exception {
        mockMvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-verify-token")
                        .param("hub.challenge", "meu-challenge-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("meu-challenge-123"));
    }

    @Test
    void verifyWebhook_tokenInvalido_retorna403() throws Exception {
        mockMvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado")
                        .param("hub.challenge", "meu-challenge-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleMessage_semAssinatura_retorna403() throws Exception {
        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"object\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleMessage_assinaturaInvalida_retorna403() throws Exception {
        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=assinatura-invalida")
                        .content("{\"object\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleMessage_payloadNaoWhatsapp_retorna200() throws Exception {
        String payload = "{\"object\":\"other\",\"entry\":[]}";
        String signature = computeHmac(payload);

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void handleMessage_usuarioNaoVinculado_codigoValido_vincula() throws Exception {
        String payload = "{\"object\":\"whatsapp_business_account\","
                + "\"entry\":[{\"changes\":[{\"value\":{\"messages\":["
                + "{\"from\":\"5511999999999\",\"type\":\"text\",\"text\":{\"body\":\"123456\"}}"
                + "]}}]}]}";
        String signature = computeHmac(payload);

        when(appUserRepository.findByWhatsappNumber("5511999999999")).thenReturn(Optional.empty());
        when(appUserService.confirmarVinculo("5511999999999", "123456")).thenReturn(true);

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .content(payload))
                .andExpect(status().isOk());

        verify(appUserService).confirmarVinculo("5511999999999", "123456");
        verify(whatsappService).enviarMensagem(eq("5511999999999"), argThat(msg -> msg.contains("vinculada")));
    }
}
