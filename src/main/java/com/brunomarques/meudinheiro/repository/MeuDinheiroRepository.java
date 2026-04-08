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

    // Agora a query exige o userId além do mês e ano
    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND MONTH(e.dataCobranca) = :mes AND YEAR(e.dataCobranca) = :ano")
    List<Expense> findByMesEAno(
            @Param("userId") String userId,
            @Param("mes") int mes,
            @Param("ano") int ano
    );

    // Opcional: Se quiser listar tudo de um usuário sem filtro de data
    List<Expense> findByUserId(String userId);
}