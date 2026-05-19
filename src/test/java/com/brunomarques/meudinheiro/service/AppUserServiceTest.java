package com.brunomarques.meudinheiro.service;

import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private AppUserService userService;

    // iniciarVinculo

    @Test
    void iniciarVinculo_newUser_setsDefaultCategories() {
        when(userRepository.findById("uid-123")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.iniciarVinculo("uid-123", "user@example.com");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());

        List<String> categories = captor.getValue().getCustomCategories();
        assertFalse(categories.isEmpty());
        assertTrue(categories.contains("Alimentação"));
        assertTrue(categories.contains("Transporte"));
        assertTrue(categories.contains("Outros"));
    }

    @Test
    void iniciarVinculo_existingUserWithCategories_doesNotOverwriteCategories() {
        AppUser existingUser = new AppUser();
        existingUser.setFirebaseUid("uid-123");
        existingUser.setCustomCategories(List.of("MinhaCategoria"));

        when(userRepository.findById("uid-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.iniciarVinculo("uid-123", "user@example.com");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());

        assertEquals(List.of("MinhaCategoria"), captor.getValue().getCustomCategories());
    }

    @Test
    void iniciarVinculo_returnsValidSixDigitCode() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String code = userService.iniciarVinculo("uid-123", "user@example.com");

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void iniciarVinculo_savesUserWithMatchingVerificationCode() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String returnedCode = userService.iniciarVinculo("uid-123", "user@example.com");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());

        assertEquals(returnedCode, captor.getValue().getVerificationCode());
    }

    // confirmarVinculo

    @Test
    void confirmarVinculo_validCode_linksWhatsappAndReturnsTrue() {
        AppUser user = new AppUser();
        user.setVerificationCode("123456");

        when(userRepository.findByVerificationCode("123456")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = userService.confirmarVinculo("5511999999999", "123456");

        assertTrue(result);
        assertEquals("5511999999999", user.getWhatsappNumber());
        assertNull(user.getVerificationCode());
    }

    @Test
    void confirmarVinculo_invalidCode_returnsFalse() {
        when(userRepository.findByVerificationCode("000000")).thenReturn(Optional.empty());

        boolean result = userService.confirmarVinculo("5511999999999", "000000");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    // usuarioTemWhatsapp

    @Test
    void usuarioTemWhatsapp_userWithNumber_returnsTrue() {
        AppUser user = new AppUser();
        user.setWhatsappNumber("5511999999999");

        when(userRepository.findById("uid-123")).thenReturn(Optional.of(user));

        assertTrue(userService.usuarioTemWhatsapp("uid-123"));
    }

    @Test
    void usuarioTemWhatsapp_userWithoutNumber_returnsFalse() {
        AppUser user = new AppUser();

        when(userRepository.findById("uid-123")).thenReturn(Optional.of(user));

        assertFalse(userService.usuarioTemWhatsapp("uid-123"));
    }

    @Test
    void usuarioTemWhatsapp_userNotFound_returnsFalse() {
        when(userRepository.findById("uid-unknown")).thenReturn(Optional.empty());

        assertFalse(userService.usuarioTemWhatsapp("uid-unknown"));
    }

    // buscarCategorias

    @Test
    void buscarCategorias_userExists_returnsUserCategories() {
        AppUser user = new AppUser();
        user.setCustomCategories(List.of("Viagem", "Pets"));

        when(userRepository.findById("uid-123")).thenReturn(Optional.of(user));

        assertEquals(List.of("Viagem", "Pets"), userService.buscarCategorias("uid-123"));
    }

    @Test
    void buscarCategorias_userNotFound_returnsDefaultList() {
        when(userRepository.findById("uid-unknown")).thenReturn(Optional.empty());

        List<String> result = userService.buscarCategorias("uid-unknown");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Alimentação"));
        assertTrue(result.contains("Outros"));
    }

    // buscarDiaFechamento

    @Test
    void buscarDiaFechamento_userNotFound_returnsDefault10() {
        when(userRepository.findById("uid-unknown")).thenReturn(Optional.empty());

        assertEquals(10, userService.buscarDiaFechamento("uid-unknown"));
    }
}
