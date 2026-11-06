package com.gestionale.industriatech.entity;

/**
 * Lezione 13: i ruoli applicativi. Spring Security li vede come "ROLE_USER" e "ROLE_ADMIN".
 */
public enum RuoloAccount {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
