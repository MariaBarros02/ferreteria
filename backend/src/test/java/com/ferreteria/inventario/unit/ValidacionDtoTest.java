/*
 * Nombre: ValidacionDtoTest.java
 * Descripción: Pruebas unitarias de Bean Validation sobre los DTO: reglas RN-06, RN-01, longitudes y anti-inyección de marcado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.VentaRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos UT-VAL-01 … UT-VAL-10. */
class ValidacionDtoTest {

    private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

    private TerceroRequest tercero(TipoPersona tp, String nombre, String apellido, String razon, boolean cli, boolean prov) {
        return new TerceroRequest(tp, tp == TipoPersona.NATURAL ? TipoIdentificacion.CC : TipoIdentificacion.NIT,
                "1002003000", nombre, null, apellido, null, razon, "Cra 10 # 20-30", "Cali", "3001234567",
                "a@b.com", cli, prov);
    }

    private ProductoRequest producto(String nombre, String codigo) {
        return new ProductoRequest(nombre, "Bosch", "500W", codigo, "unidad", TipoRastreo.NINGUNO, false, false);
    }

    @Test @DisplayName("UT-VAL-01 persona natural válida")
    void naturalValida() {
        assertThat(v.validate(tercero(TipoPersona.NATURAL, "Juan", "Pérez", null, true, false))).isEmpty();
    }

    @Test @DisplayName("UT-VAL-02 persona natural sin apellido es inválida (RN-06)")
    void naturalSinApellido() {
        assertThat(v.validate(tercero(TipoPersona.NATURAL, "Juan", null, null, true, false))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-03 persona jurídica con NIT y razón social, sin apellido, es válida (HU-08)")
    void juridicaValida() {
        assertThat(v.validate(tercero(TipoPersona.JURIDICA, null, null, "Distribuidora S.A.S.", false, true))).isEmpty();
    }

    @Test @DisplayName("UT-VAL-04 persona jurídica sin razón social es inválida (RN-06)")
    void juridicaSinRazon() {
        assertThat(v.validate(tercero(TipoPersona.JURIDICA, null, null, " ", false, true))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-05 un tercero sin rol de cliente ni proveedor es inválido")
    void sinRol() {
        assertThat(v.validate(tercero(TipoPersona.NATURAL, "Juan", "Pérez", null, false, false))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-06 código de barras: solo 8 a 14 dígitos")
    void codigoBarras() {
        assertThat(v.validate(producto("Taladro", "7701234500015"))).isEmpty();
        assertThat(v.validate(producto("Taladro", "123"))).isNotEmpty();
        assertThat(v.validate(producto("Taladro", "77012345ABCD1"))).isNotEmpty();
        assertThat(v.validate(producto("Taladro", "7701234500015' OR '1'='1"))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-07 se rechaza HTML/script en campos de texto (XSS almacenado)")
    void sinHtml() {
        assertThat(v.validate(producto("<script>alert(1)</script>", "7701234500015"))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-08 límite de longitud del nombre (150) en el borde")
    void longitudBorde() {
        assertThat(v.validate(producto("a".repeat(150), "7701234500015"))).isEmpty();
        assertThat(v.validate(producto("a".repeat(151), "7701234500015"))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-09 venta sin líneas es inválida")
    void ventaSinLineas() {
        assertThat(v.validate(new VentaRequest(1L, List.of(), null, null))).isNotEmpty();
    }

    @Test @DisplayName("UT-VAL-10 cantidad cero o negativa en línea de venta es inválida")
    void cantidadNoPositiva() {
        var cero = new VentaRequest(1L, List.of(new VentaRequest.Linea(1L, BigDecimal.ZERO, null)), null, null);
        var neg = new VentaRequest(1L, List.of(new VentaRequest.Linea(1L, new BigDecimal("-1"), null)), null, null);
        var ok = new VentaRequest(1L, List.of(new VentaRequest.Linea(1L, new BigDecimal("0.5"), null)), null, null);
        assertThat(v.validate(cero)).isNotEmpty();
        assertThat(v.validate(neg)).isNotEmpty();
        assertThat(v.validate(ok)).isEmpty();
    }
}
