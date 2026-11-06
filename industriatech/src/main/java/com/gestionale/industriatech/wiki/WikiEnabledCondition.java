package com.gestionale.industriatech.wiki;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Lezione 11: la wiki è FACOLTATIVA.
 *
 * Nella lezione originale, senza app.wiki.vault-path l'applicazione non parte.
 * Nel nostro progetto preferiamo che parta comunque: chi non ha ancora creato
 * la sua LLM Wiki (o i test automatici) usa il gestionale senza wiki.
 *
 * Le classi della wiki hanno @Conditional(WikiEnabledCondition.class):
 * Spring le crea solo se il percorso è impostato (variabile WIKI_VAULT_PATH).
 */
public class WikiEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("app.wiki.vault-path"));
    }
}
