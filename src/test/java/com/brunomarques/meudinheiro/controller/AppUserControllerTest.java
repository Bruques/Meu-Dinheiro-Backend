package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.service.AppUserService;
import org.junit.jupiter.api.Test;
import com.brunomarques.meudinheiro.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppUserController.class)
@Import(SecurityConfig.class)
class AppUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService userService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void gerarCodigo_retornaCodigo() throws Exception {
        when(userService.iniciarVinculo("uid-123", "user@example.com")).thenReturn("654321");

        mockMvc.perform(post("/api/users/gerar-codigo")
                        .param("uid", "uid-123")
                        .param("email", "user@example.com")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("654321"));
    }

    @Test
    void verificarWhatsapp_comNumero_retornaTrue() throws Exception {
        when(userService.usuarioTemWhatsapp("uid-123")).thenReturn(true);

        mockMvc.perform(get("/api/users/tem-whatsapp/uid-123")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void getDiaFechamento_retornaDia() throws Exception {
        when(userService.buscarDiaFechamento("uid-123")).thenReturn(21);

        mockMvc.perform(get("/api/users/dia-fechamento/uid-123")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("21"));
    }

    @Test
    void syncUser_semAuth_retorna401() throws Exception {
        mockMvc.perform(post("/api/users/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void syncUser_usuarioNovo_retorna200() throws Exception {
        when(appUserRepository.findById("new-uid")).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/users/sync")
                        .with(jwt().jwt(j -> j
                                .subject("new-uid")
                                .claim("email", "new@example.com"))))
                .andExpect(status().isOk());

        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void getConfiguracoes_comAuth_retornaMap() throws Exception {
        AppUser user = new AppUser();
        user.setDiaFechamentoFatura(15);
        user.setDiaVencimentoFatura(20);

        when(appUserRepository.findById("uid-1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/config")
                        .with(jwt().jwt(j -> j.subject("uid-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechamento").value(15))
                .andExpect(jsonPath("$.vencimento").value(20));
    }

    @Test
    void getCategoriasDoUsuario_retornaCategorias() throws Exception {
        when(userService.buscarCategorias("uid-123"))
                .thenReturn(List.of("Alimentação", "Transporte", "Lazer"));

        mockMvc.perform(get("/api/users/uid-123/categorias")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Alimentação"))
                .andExpect(jsonPath("$[2]").value("Lazer"));
    }
}
