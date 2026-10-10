/*
 * Nombre: I18nConfig.java
 * Descripción: Internacionalización: idioma por cabecera Accept-Language (es por defecto, en) y mensajes de validación traducidos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configura el idioma de las respuestas y enlaza Bean Validation con {@link MessageSource}.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private final MessageSource messageSource;

    /** @param messageSource fuente de mensajes de Spring Boot (messages*.properties) */
    public I18nConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** @return resolutor de idioma basado en Accept-Language, español por defecto */
    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setDefaultLocale(Locale.forLanguageTag("es"));
        r.setSupportedLocales(List.of(Locale.forLanguageTag("es"), Locale.ENGLISH));
        return r;
    }

    @Override
    public Validator getValidator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
}
