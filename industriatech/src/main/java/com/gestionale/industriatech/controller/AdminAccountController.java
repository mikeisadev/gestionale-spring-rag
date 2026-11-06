package com.gestionale.industriatech.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.dto.auth.AccountResponse;
import com.gestionale.industriatech.dto.auth.AccountUpdateRequest;
import com.gestionale.industriatech.security.AccountUserDetails;
import com.gestionale.industriatech.service.AccountConflictException;
import com.gestionale.industriatech.service.AccountService;

import jakarta.validation.Valid;

/**
 * Lezione 13 (decisione B del piano): gli amministratori assegnano ruolo e reparto.
 * Le regole d'accesso (solo ROLE_ADMIN) sono in SecurityConfig, non qui.
 */
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountService.list();
    }

    @PatchMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request,
                                  @AuthenticationPrincipal AccountUserDetails admin) {
        return accountService.update(id, request, admin.getId());
    }

    @ExceptionHandler(AccountConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(AccountConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Dati non validi");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
