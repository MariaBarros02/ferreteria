/*
 * Nombre: MotorAsistenteTest.java
 * Descripción: Pruebas unitarias del motor de reglas del asistente virtual: validación de la base de conocimiento, normalización del texto, comparación de palabras, clasificación determinista y permisos por rol.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ferreteria.inventario.service.BaseConocimiento;
import com.ferreteria.inventario.service.BaseConocimiento.Enlace;
import com.ferreteria.inventario.service.BaseConocimiento.Nodo;
import com.ferreteria.inventario.service.MotorAsistente;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotorAsistenteTest {

    private static Nodo nodo(String id, List<String> clavesEs, List<String> clavesEn, List<String> opciones,
                             List<String> roles, Enlace enlace, boolean oculto) {
        return new Nodo(id, oculto, Map.of("es", "Título " + id, "en", "Title " + id),
                Map.of("es", "Respuesta " + id, "en", "Answer " + id), Map.of("es", clavesEs, "en", clavesEn),
                opciones, roles, enlace, false);
    }

    /** Base pequeña: "ventas" (tema con hijos), "registrar" (frase de dos palabras), "cierre" (solo Gerencia), "hola" (oculto). */
    private static MotorAsistente motor() {
        return new MotorAsistente(new BaseConocimiento("9.9.9", List.of("ventas", "cierre"), List.of(
                nodo("ventas", List.of("ventas", "vender"), List.of("sales"), List.of("registrar"), List.of(), null, false),
                nodo("registrar", List.of("registrar venta", "nueva venta"), List.of("record sale"), null,
                        List.of("VENTAS", "ADMINISTRADOR"), new Enlace("/ventas/nueva", null, Map.of("es", "Abrir")), false),
                nodo("cierre", List.of("cierre mensual", "cierre"), List.of("closing"), null, List.of("GERENCIA"),
                        new Enlace("/cierres", List.of("GERENCIA", "ADMINISTRADOR"), Map.of("es", "Ir")), false),
                nodo("hola", List.of("hola"), List.of("hello"), null, List.of(), null, true))));
    }

    // ------------------------------------------------------------------ validación de la base

    @Test @DisplayName("UT-MAS-01 rechaza ids inválidos o repetidos")
    void idsInvalidos() {
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("A B"),
                List.of(nodo("A B", List.of(), List.of(), null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inválido");
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("a"),
                List.of(nodo("a", List.of(), List.of(), null, null, null, false), nodo("a", List.of(), List.of(), null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetido");
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("a"),
                List.of(new Nodo(null, false, null, Map.of("es", "x"), null, null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("UT-MAS-02 rechaza opciones o temas principales inexistentes, raíz vacía y nodos sin respuesta en español")
    void referenciasRotas() {
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("a"),
                List.of(nodo("a", List.of(), List.of(), List.of("zz"), null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zz");
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("zz"),
                List.of(nodo("a", List.of(), List.of(), null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zz");
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", null,
                List.of(nodo("a", List.of(), List.of(), null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("temas principales");
        assertThatThrownBy(() -> new MotorAsistente(new BaseConocimiento("1", List.of("a"),
                List.of(new Nodo("a", false, null, Map.of("en", "only english"), null, null, null, null, false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("español");
    }

    @Test @DisplayName("UT-MAS-03 los textos caen al español si falta el idioma y las colecciones nulas quedan vacías")
    void textosYNulos() {
        Nodo n = new Nodo("a", false, Map.of("es", "Título"), Map.of("es", "Respuesta"), null, null, null,
                new Enlace("/x", null, null), false);
        assertThat(n.tituloEn("en")).isEqualTo("Título");
        assertThat(n.respuestaEn("es")).isEqualTo("Respuesta");
        assertThat(n.claves()).isEmpty();
        assertThat(n.opciones()).isEmpty();
        assertThat(n.roles()).isEmpty();
        assertThat(n.enlace().roles()).isEmpty();
        assertThat(n.enlace().textoEn("en")).isEmpty();
        assertThat(new BaseConocimiento("1", null, null).nodos()).isEmpty();
    }

    // ------------------------------------------------------------------ texto

    @Test @DisplayName("UT-MAS-04 normaliza: minúsculas, sin tildes ni signos, espacios simples; nulo → vacío")
    void normalizar() {
        assertThat(MotorAsistente.normalizar("  ¡Cómo   REGISTRO, una Venta?! ")).isEqualTo("como registro una venta");
        assertThat(MotorAsistente.normalizar("Contraseña / PIN-123")).isEqualTo("contrasena pin 123");
        assertThat(MotorAsistente.normalizar(null)).isEmpty();
    }

    @Test @DisplayName("UT-MAS-05 palabras significativas: sin palabras vacías ni repetidas, en orden")
    void palabras() {
        assertThat(MotorAsistente.palabras("como registro una venta de la venta")).containsExactly("como", "registro", "venta");
        assertThat(MotorAsistente.palabras("how do i record the sale")).containsExactly("how", "record", "sale");
        assertThat(MotorAsistente.palabras("")).isEmpty();
    }

    @Test @DisplayName("UT-MAS-06 compara palabras: igual, plural/conjugación cercana o misma raíz; no confunde palabras distintas")
    void coincide() {
        assertThat(MotorAsistente.coincide("venta", "venta")).isTrue();
        assertThat(MotorAsistente.coincide("ventas", "venta")).isTrue();       // una letra más al final
        assertThat(MotorAsistente.coincide("venta", "ventas")).isTrue();       // simétrico
        assertThat(MotorAsistente.coincide("precios", "precio")).isTrue();
        assertThat(MotorAsistente.coincide("registro", "registrar")).isTrue(); // misma raíz "registr"
        assertThat(MotorAsistente.coincide("compras", "compra")).isTrue();
        assertThat(MotorAsistente.coincide("comprobante", "compra")).isFalse();
        assertThat(MotorAsistente.coincide("ventanas", "venta")).isFalse();    // tres letras de diferencia
        assertThat(MotorAsistente.coincide("ir", "iras")).isFalse();           // palabras cortas solo si son iguales
        assertThat(MotorAsistente.coincide("iva", "ivas")).isFalse();
    }

    // ------------------------------------------------------------------ clasificación

    @Test @DisplayName("UT-MAS-07 una frase de dos palabras vence a una palabra suelta; la respuesta es siempre la misma")
    void fraseEspecifica() {
        MotorAsistente m = motor();
        List<MotorAsistente.Puntaje> r = m.clasificar("¿Cómo REGISTRO una venta?", "es");
        assertThat(r).extracting(p -> p.nodo().id()).containsExactly("registrar", "ventas");
        assertThat(r.get(0).puntaje()).isCloseTo(2.0, within(1e-9));
        assertThat(r.get(1).puntaje()).isCloseTo(1.0, within(1e-9));
        assertThat(m.clasificar("¿Cómo REGISTRO una venta?", "es")).isEqualTo(r);   // determinista
    }

    @Test @DisplayName("UT-MAS-08 cada frase adicional suma 0,1 y las del otro idioma valen el 80 %")
    void bonoYOtroIdioma() {
        MotorAsistente m = motor();
        // solo coincide "ventas" ("vender" tiene otra raíz): una frase de una palabra
        assertThat(m.clasificar("ventas", "es").get(0).puntaje()).isCloseTo(1.0, within(1e-9));
        // "nueva venta" (2) y "registrar venta" (2) coinciden → 2 + 0,1
        assertThat(m.clasificar("registrar nueva venta", "es").get(0).puntaje()).isCloseTo(2.1, within(1e-9));
        // frase en inglés consultada en español: 2 palabras × 0,8
        List<MotorAsistente.Puntaje> r = m.clasificar("record sale", "es");
        assertThat(r.get(0).nodo().id()).isEqualTo("registrar");
        assertThat(r.get(0).puntaje()).isCloseTo(1.6, within(1e-9));
        assertThat(m.clasificar("record sale", "en").get(0).puntaje()).isCloseTo(2.0, within(1e-9));
    }

    @Test @DisplayName("UT-MAS-09 empates: se ordenan por la posición en la base; sin palabras útiles no hay resultados")
    void empatesYVacios() {
        MotorAsistente m = motor();
        List<MotorAsistente.Puntaje> r = m.clasificar("ventas cierre", "es");
        assertThat(r).extracting(p -> p.nodo().id()).containsExactly("ventas", "cierre");
        assertThat(r.get(0).puntaje()).isEqualTo(r.get(1).puntaje());
        assertThat(r.get(0).orden()).isLessThan(r.get(1).orden());
        assertThat(m.clasificar("de la el", "es")).isEmpty();
        assertThat(m.clasificar(null, "es")).isEmpty();
        assertThat(m.clasificar("xyz", "es")).isEmpty();
    }

    // ------------------------------------------------------------------ navegación y permisos

    @Test @DisplayName("UT-MAS-10 temas principales filtrados por rol, hijos en orden y búsqueda por id")
    void navegacion() {
        MotorAsistente m = motor();
        assertThat(m.version()).isEqualTo("9.9.9");
        assertThat(m.raiz("VENTAS")).extracting(Nodo::id).containsExactly("ventas");
        assertThat(m.raiz("GERENCIA")).extracting(Nodo::id).containsExactly("ventas", "cierre");
        assertThat(m.raiz(null)).extracting(Nodo::id).containsExactly("ventas");
        assertThat(m.hijos(m.nodo("ventas").orElseThrow())).extracting(Nodo::id).containsExactly("registrar");
        assertThat(m.nodo("no-existe")).isEmpty();
        assertThat(m.nodo(null)).isEmpty();
    }

    @Test @DisplayName("UT-MAS-11 permisos: roles del nodo, roles propios del enlace y nodos sin enlace")
    void permisos() {
        MotorAsistente m = motor();
        Nodo registrar = m.nodo("registrar").orElseThrow();
        Nodo cierre = m.nodo("cierre").orElseThrow();
        Nodo ventas = m.nodo("ventas").orElseThrow();
        assertThat(m.permitido(registrar, "VENTAS")).isTrue();
        assertThat(m.permitido(registrar, "GERENCIA")).isFalse();
        assertThat(m.permitido(registrar, null)).isFalse();
        assertThat(m.permitido(ventas, null)).isTrue();
        assertThat(m.enlacePermitido(registrar, "ADMINISTRADOR")).isTrue();     // usa los roles del nodo
        assertThat(m.enlacePermitido(registrar, "COMPRAS")).isFalse();
        assertThat(m.enlacePermitido(cierre, "ADMINISTRADOR")).isTrue();        // el enlace amplía los roles
        assertThat(m.enlacePermitido(cierre, null)).isFalse();
        assertThat(m.enlacePermitido(ventas, "VENTAS")).isFalse();              // sin enlace
        Nodo libre = nodo("libre", List.of(), List.of(), null, List.of(), new Enlace("/x", null, null), false);
        Nodo sinRuta = nodo("sinruta", List.of(), List.of(), null, List.of(), new Enlace(null, null, null), false);
        assertThat(m.enlacePermitido(libre, null)).isTrue();
        assertThat(m.enlacePermitido(sinRuta, "VENTAS")).isFalse();
    }
}
