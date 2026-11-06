package com.gestionale.industriatech.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // L'email arriva già normalizzata (AccountService.normalizeEmail)
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    // Bootstrap del primo amministratore: ce n'è già almeno uno?
    boolean existsByRuolo(RuoloAccount ruolo);

    long countByRuoloAndAbilitatoTrue(RuoloAccount ruolo);

    List<Account> findAllByOrderByCreatoIlAsc();
}
