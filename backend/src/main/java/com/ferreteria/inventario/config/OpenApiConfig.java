/*
 * Nombre: OpenApiConfig.java
 * Descripción: Documentación OpenAPI 3: metadatos de la API y esquema de seguridad Bearer JWT.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Define la documentación interactiva (Swagger UI en {@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad. */
    public static final String ESQUEMA = "bearerAuth";

    /** @return definición OpenAPI */
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("API Inventario Ferretería").version("1.0.0")
                        .description("CRUD y operaciones del sistema de inventario. Formato de intercambio: JSON. "
                                + "Idioma de mensajes por cabecera Accept-Language (es, en)."))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
