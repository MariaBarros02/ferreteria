/*
 * Nombre: AsistenteController.java
 * Descripción: API REST del asistente virtual: bienvenida con los temas permitidos para el rol y respuesta a preguntas en texto libre o a temas elegidos. Requiere sesión iniciada (cualquier rol); responde en el idioma de Accept-Language (es/en).
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.AsistenteMensajeRequest;
import com.ferreteria.inventario.dto.AsistenteRespuesta;
import com.ferreteria.inventario.service.AsistenteService;
import com.ferreteria.inventario.service.ContextoSolicitud;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asistente virtual (preguntas frecuentes y consultas de solo lectura). */
@RestController
@RequestMapping("/api/asistente")
@RequiredArgsConstructor
@Tag(name = "Asistente virtual", description = "Chatbot de soporte con reglas deterministas (árbol de decisión de preguntas frecuentes)")
public class AsistenteController {

    private final AsistenteService servicio;
    private final ContextoSolicitud contexto;

    /** Bienvenida y temas principales del rol. */
    @GetMapping("/inicio")
    @Operation(summary = "Iniciar conversación", description = "Saludo y temas principales permitidos para el rol del usuario.")
    public AsistenteRespuesta inicio() {
        return servicio.inicio(contexto.rol(), idioma());
    }

    /** Responde una pregunta o un tema elegido. */
    @PostMapping("/mensajes")
    @Operation(summary = "Enviar mensaje al asistente", description = "Envíe 'texto' (pregunta, código de barras, "
            + "\"precio del taladro\", \"¿a cuánto está el dólar?\") o 'nodo' (id de un tema sugerido). No se guarda la conversación.")
    public AsistenteRespuesta mensaje(@Valid @RequestBody AsistenteMensajeRequest req) {
        return servicio.responder(req, contexto.rol(), idioma());
    }

    private static String idioma() {
        return "en".equals(LocaleContextHolder.getLocale().getLanguage()) ? "en" : "es";
    }
}
