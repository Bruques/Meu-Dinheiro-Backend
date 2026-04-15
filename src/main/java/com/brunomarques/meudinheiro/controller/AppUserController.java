package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.service.AppUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class AppUserController {

    private final AppUserService userService;

    public AppUserController(AppUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/gerar-codigo")
    public String gerarCodigo(@RequestParam String uid, @RequestParam String email) {
        return userService.iniciarVinculo(uid, email);
    }

    // TODO: - Melhorar esse nome
    @GetMapping("/tem-whatsapp/{uid}")
    public boolean verificarWhatsapp(@PathVariable String uid) {
        return userService.usuarioTemWhatsapp(uid);
    }
}