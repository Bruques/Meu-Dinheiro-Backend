package com.brunomarques.meudinheiro.controller;

import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import com.brunomarques.meudinheiro.service.AppUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class AppUserController {

    private final AppUserService userService;
    private final AppUserRepository appUserRepository;

    public AppUserController(AppUserService userService,
                             AppUserRepository appUserRepository) {
        this.userService = userService;
        this.appUserRepository = appUserRepository;
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

    @PostMapping("/atualizar-fatura")
    public void atualizarFatura(
            @RequestParam String uid,
            @RequestParam Integer fechamento,
            @RequestParam Integer vencimento) {
        userService.atualizarFatura(uid, fechamento, vencimento);
    }

    @GetMapping("/dia-fechamento/{uid}")
    public Integer getDiaFechamento(@PathVariable String uid) {
        return userService.buscarDiaFechamento(uid);
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> syncUser(@AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        String firebaseUid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        // Verifica se o usuário já existe no banco
        Optional<AppUser> userOpt = appUserRepository.findById(firebaseUid);

        if (userOpt.isEmpty()) {
            System.out.println("🌱 Criando novo perfil no Postgres para: " + firebaseUid);
            AppUser newUser = new AppUser();
            newUser.setFirebaseUid(firebaseUid);

            newUser.setEmail(email);
            // Default values
            newUser.setCustomCategories(List.of("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Vestuário", "Outros"));
            newUser.setDiaFechamentoFatura(21);
            newUser.setDiaVencimentoFatura(24);

            appUserRepository.save(newUser);
        }

        return org.springframework.http.ResponseEntity.ok().build();
    }
}