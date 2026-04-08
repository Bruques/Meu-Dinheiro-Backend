package com.brunomarques.meudinheiro.repository; // Ajuste o pacote

import com.brunomarques.meudinheiro.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeuDinheiroRepository extends JpaRepository<Expense, Long> {
    // Só de herdar o JpaRepository, você ganha métodos como save(), findAll(), deleteById() de graça!

    // Busca os gastos onde o mês e o ano da DATA DE COBRANÇA sejam iguais aos que passarmos
    @Query("SELECT e FROM Expense e WHERE MONTH(e.dataCobranca) = :mes AND YEAR(e.dataCobranca) = :ano")
    List<Expense> findByMesEAno(@Param("mes") int mes, @Param("ano") int ano);
}