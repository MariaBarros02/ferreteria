/*
 * Nombre: CalculosTest.java
 * Descripción: Pruebas unitarias de las fórmulas de importes (Montos) y de precio/costo (CalculadoraPrecio): RF-04, RF-07, RN-07.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.service.CalculadoraPrecio;
import com.ferreteria.inventario.service.Montos;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos UT-CAL-01 … UT-CAL-10. */
class CalculosTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test @DisplayName("UT-CAL-01 RF-07: 10 u a $10.000 + 10 u a $14.000 → costo promedio $12.000")
    void promedioPonderado() {
        BigDecimal c1 = CalculadoraPrecio.promedioPonderado(BigDecimal.ZERO, null, d("10"), d("10000"));
        BigDecimal c2 = CalculadoraPrecio.promedioPonderado(d("10"), c1, d("10"), d("14000"));
        assertThat(c1).isEqualByComparingTo("10000");
        assertThat(c2).isEqualByComparingTo("12000");
    }

    @Test @DisplayName("UT-CAL-02 RN-07 recargo: $12.000 con 30 % → precio propuesto $15.600")
    void recargo() {
        assertThat(CalculadoraPrecio.calcular(d("12000"), d("0.30"), CalculadoraPrecio.RECARGO, BigDecimal.ONE))
                .isEqualByComparingTo("15600");
    }

    @Test @DisplayName("UT-CAL-03 margen sobre precio: P = C / (1 − m) no es lo mismo que recargo")
    void margen() {
        BigDecimal p = CalculadoraPrecio.calcular(d("12000"), d("0.30"), CalculadoraPrecio.MARGEN, BigDecimal.ONE);
        assertThat(p).isEqualByComparingTo("17143");
        assertThat(CalculadoraPrecio.margen(d("12000"), p)).isEqualByComparingTo("0.3000");
    }

    @Test @DisplayName("UT-CAL-04 margen del 100 % o más se rechaza")
    void margenInvalido() {
        assertThatThrownBy(() -> CalculadoraPrecio.calcular(d("100"), BigDecimal.ONE, CalculadoraPrecio.MARGEN, BigDecimal.ONE))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MARGEN_INVALIDO"));
    }

    @Test @DisplayName("UT-CAL-05 redondeo configurable al múltiplo (100) con HALF_UP")
    void redondeoMultiplo() {
        assertThat(CalculadoraPrecio.redondear(d("15649"), d("100"))).isEqualByComparingTo("15600");
        assertThat(CalculadoraPrecio.redondear(d("15650"), d("100"))).isEqualByComparingTo("15700");
        assertThat(CalculadoraPrecio.redondear(d("1.005"), d("0.01"))).isEqualByComparingTo("1.01");
    }

    @Test @DisplayName("UT-CAL-06 costo negativo o porcentaje nulo se rechazan")
    void datosInvalidos() {
        assertThatThrownBy(() -> CalculadoraPrecio.calcular(d("-1"), d("0.1"), CalculadoraPrecio.RECARGO, BigDecimal.ONE))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CalculadoraPrecio.calcular(d("1"), null, CalculadoraPrecio.RECARGO, BigDecimal.ONE))
                .isInstanceOf(ApiException.class);
    }

    @Test @DisplayName("UT-CAL-07 línea: descuento sobre el subtotal e IVA sobre la base (redondeo a centavos)")
    void lineaConIvaYDescuento() {
        Montos.Linea l = Montos.linea(d("3"), d("33333.33"), d("5"), d("19"));
        assertThat(l.subtotal()).isEqualByComparingTo("99999.99");
        assertThat(l.descuento()).isEqualByComparingTo("5000.00");
        assertThat(l.impuesto()).isEqualByComparingTo("18050.00");
        assertThat(l.total()).isEqualByComparingTo("113049.99");
    }

    @Test @DisplayName("UT-CAL-08 totales del documento = suma de líneas ya redondeadas")
    void totales() {
        Montos.Totales t = Montos.totales(List.of(Montos.linea(d("1"), d("10.005"), null, null),
                Montos.linea(d("1"), d("10.005"), null, null)));
        assertThat(t.subtotal()).isEqualByComparingTo("20.02");
        assertThat(t.total()).isEqualByComparingTo("20.02");
    }

    @Test @DisplayName("UT-CAL-09 devolución parcial: valor proporcional del total persistido de la línea")
    void proporcional() {
        assertThat(Montos.proporcional(d("1392300"), d("2"), d("1"))).isEqualByComparingTo("696150.00");
        assertThat(Montos.proporcional(d("100"), d("3"), d("1"))).isEqualByComparingTo("33.33");
    }

    @Test @DisplayName("UT-CAL-10 promedio sin existencias previas toma el costo de la entrada")
    void promedioSinExistencias() {
        assertThat(CalculadoraPrecio.promedioPonderado(BigDecimal.ZERO, d("999"), d("5"), d("10")))
                .isEqualByComparingTo("10");
    }
}
