package com.brunomarques.meudinheiro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WhatsappService service;

    @BeforeEach
    void setUp() {
        service = new WhatsappService(restTemplate);
        ReflectionTestUtils.setField(service, "phoneId", "1234567890");
        ReflectionTestUtils.setField(service, "token", "test-token");
    }

    // enviarMensagem

    @Test
    void enviarMensagem_callsMetaApiWithCorrectUrl() {
        service.enviarMensagem("5511999999999", "Olá!");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForObject(urlCaptor.capture(), any(), eq(String.class));

        assertTrue(urlCaptor.getValue().contains("1234567890"));
        assertTrue(urlCaptor.getValue().contains("graph.facebook.com"));
    }

    @Test
    void enviarMensagem_doesNotThrowWhenApiFails() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API offline"));

        assertDoesNotThrow(() -> service.enviarMensagem("5511999999999", "Olá!"));
    }

    // obterUrlDaMidia

    @Test
    void obterUrlDaMidia_parsesDownloadUrl() {
        String metaResponse = "{\"url\": \"https://download.example.com/audio123\", \"id\": \"media-id\"}";

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(metaResponse));

        String result = service.obterUrlDaMidia("media-id-123");

        assertEquals("https://download.example.com/audio123", result);
    }

    @Test
    void obterUrlDaMidia_returnsNullOnException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertNull(service.obterUrlDaMidia("media-id-123"));
    }

    // baixarArquivo

    @Test
    void baixarArquivo_returnsAudioBytes() {
        byte[] fakeBytes = "audio-content".getBytes();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(fakeBytes));

        byte[] result = service.baixarArquivo("https://download.example.com/audio123");

        assertArrayEquals(fakeBytes, result);
    }

    @Test
    void baixarArquivo_returnsNullOnException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RuntimeException("Download failed"));

        assertNull(service.baixarArquivo("https://download.example.com/audio123"));
    }
}
