/*
 * Nombre: ReglasBaseDatosIT.java
 * Descripción: Pruebas de sistema sobre el esquema real: disparadores y restricciones que hacen cumplir RN-03, RN-09 y RN-16.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.service.VentaService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/** Casos ST-BD-01 … ST-BD-05. */
class ReglasBaseDatosIT extends BaseIT {

    @Autowired VentaService ventaService;

    private Long ventaExistente() {
        var p = productoConStock("10", "100");
        usuario("bd-user@test.com", "Ventas", com.ferreteria.inventario.domain.enums.EstadoCuenta.ACTIVO);
        return ventaService.registrar(new VentaRequest(cliente().getId(),
                List.of(new VentaRequest.Linea(p.getId(), BigDecimal.ONE, null)), null, null), "bd-user@test.com", "127.0.0.1").id();
    }

    @Test @DisplayName("ST-BD-01 RN-16: un movimiento de inventario no se puede editar")
    void movimientoInmutable() {
        ventaExistente();
        assertThatThrownBy(() -> jdbc.update("update movimiento_inventario set cantidad = 999"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("RN-16");
    }

    @Test @DisplayName("ST-BD-02 RN-09: una venta no se puede eliminar")
    void ventaNoSeBorra() {
        Long id = ventaExistente();
        assertThatThrownBy(() -> jdbc.update("delete from venta where id_venta = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("RN-09");
    }

    @Test @DisplayName("ST-BD-03 RN-03: el saldo disponible no puede quedar negativo (CHECK)")
    void saldoNoNegativo() {
        var p = productoConStock("1", null);
        assertThatThrownBy(() -> jdbc.update("update inventario set disponible = -1 where id_producto = ?", p.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test @DisplayName("ST-BD-04 RN-01: el código de barras es único (UNIQUE)")
    void codigoUnicoEnBd() {
        var p = productoConStock("1", null);
        assertThatThrownBy(() -> jdbc.update("insert into producto(nombre,marca,presentacion,codigo_barras,unidad_base) values ('x','y','z',?, 'u')",
                p.getCodigoBarras())).isInstanceOf(DataAccessException.class);
    }

    @Test @DisplayName("ST-BD-05 RN-14: el número interno de venta y la numeración fiscal no coinciden")
    void numeracionDistintaEnBd() {
        ventaExistente();
        Integer iguales = jdbc.queryForObject("select count(*) from venta v join factura f on f.id_venta=v.id_venta where v.numero_interno = f.numeracion_dian", Integer.class);
        assertThat(iguales).isZero();
    }
}
