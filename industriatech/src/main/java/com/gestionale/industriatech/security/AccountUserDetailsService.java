package com.gestionale.industriatech.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.service.AccountService;

/** Lezione 13: come Spring Security trova un account durante il login (l'email è lo "username"). */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return accountRepository.findByEmail(AccountService.normalizeEmail(email))
                .map(AccountUserDetails::new)
                // Il messaggio NON arriva al client: AuthController risponde sempre "Email o password non corretti"
                .orElseThrow(() -> new UsernameNotFoundException("Account non trovato"));
    }
}
