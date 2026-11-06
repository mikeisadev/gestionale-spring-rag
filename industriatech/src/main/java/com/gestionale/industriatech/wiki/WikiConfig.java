package com.gestionale.industriatech.wiki;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** Lezione 11: legge le proprietà app.wiki.* (solo se la wiki è configurata). */
@Configuration
@Conditional(WikiEnabledCondition.class)
@EnableConfigurationProperties(WikiProperties.class)
public class WikiConfig {
}
