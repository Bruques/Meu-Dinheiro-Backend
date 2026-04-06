package com.brunomarques.meudinheiro.repository; // Ajuste o pacote

import com.brunomarques.meudinheiro.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeuDinheiroRepository extends JpaRepository<Expense, Long> {
    // Só de herdar o JpaRepository, você ganha métodos como save(), findAll(), deleteById() de graça!
}