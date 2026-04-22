package com.brunomarques.meudinheiro.service;

import com.brunomarques.meudinheiro.model.AppUser;
import com.brunomarques.meudinheiro.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;

    public AppUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. O Angular chama isso para gerar o código na tela
    public String iniciarVinculo(String firebaseUid, String email) {
        // Busca se o usuário já existe ou cria um novo
        AppUser user = userRepository.findById(firebaseUid).orElse(new AppUser());
        user.setFirebaseUid(firebaseUid);
        user.setEmail(email);

        // 👇 SE FOR UM USUÁRIO NOVO, DÁ AS CATEGORIAS PADRÕES
        if (user.getCustomCategories().isEmpty()) {
            user.setCustomCategories(List.of(
                    "Alimentação", "Transporte", "Moradia", "Saúde",
                    "Lazer", "Educação", "Vestuário", "Outros"
            ));
        }

        // Gera um código aleatório de 6 dígitos
        String codigo = String.format("%06d", new Random().nextInt(999999));
        user.setVerificationCode(codigo);

        userRepository.save(user);
        return codigo;
    }

    // 2. O WhatsApp chama isso quando o usuário manda os 6 dígitos
    public boolean confirmarVinculo(String numeroWhatsapp, String codigo) {
        return userRepository.findByVerificationCode(codigo).map(user -> {
            user.setWhatsappNumber(numeroWhatsapp);
            user.setVerificationCode(null); // Limpa o código para não usar de novo
            userRepository.save(user);
            return true;
        }).orElse(false);
    }

    public boolean usuarioTemWhatsapp(String firebaseUid) {
        return userRepository.findById(firebaseUid)
                .map(user -> user.getWhatsappNumber() != null)
                .orElse(false);
    }

    public void atualizarFatura(String firebaseUid, Integer novoFechamento, Integer novoVencimento) {
        userRepository.findById(firebaseUid).ifPresent(user -> {
            user.setDiaFechamentoFatura(novoFechamento);
            user.setDiaVencimentoFatura(novoVencimento);
            userRepository.save(user);
        });
    }

    public Integer buscarDiaFechamento(String firebaseUid) {
        return userRepository.findById(firebaseUid)
                .map(AppUser::getDiaFechamentoFatura)
                .orElse(10);
    }
}