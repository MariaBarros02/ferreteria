/*
 * Nombre: MonedaServiceTest.java
 * Descripción: Pruebas unitarias de monedas y tasas de cambio (RF-23, RN-17): moneda funcional, vigencia, tasas futuras, monedas inactivas, conversión y auditoría.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.Moneda;
import com.ferreteria.inventario.domain.model.TasaCambio;
import com.ferreteria.inventario.dto.TasaCambioRequest;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Casos UT-MON-01 … UT-MON-06. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonedaServiceTest {

    @Mock MonedaRepository monedas;
    @Mock TasaCambioRepository tasas;
    @Mock UsuarioRepository usuarios;
    @Mock InventarioRepository inventarios;
    @Mock ProductoRepository productos;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;

    MonedaService servicio;
    final Actor gerente = new Actor(5, "gerencia@ferreteria.com", "10.0.0.5");
    final Moneda cop = Moneda.builder().codigo("COP").nombre("Peso colombiano").simbolo("$").decimales((short) 0).activa(true).funcional(true).build();
    final Moneda usd = Moneda.builder().codigo("USD").nombre("Dólar").simbolo("US$").decimales((short) 2).activa(true).build();
    final Moneda eur = Moneda.builder().codigo("EUR").nombre("Euro").simbolo("€").decimales((short) 2).activa(true).build();

    @BeforeEach
    void preparar() {
        // 2026-10-16 15:00 UTC = 10:00 en Bogotá
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, ParametrosNegocio.porDefecto(), reloj);
        servicio = new MonedaService(monedas, tasas, usuarios, kardex, eventos);
        when(monedas.findFirstByFuncionalTrue()).thenReturn(Optional.of(cop));
        when(monedas.findById("COP")).thenReturn(Optional.of(cop));
        when(monedas.findById("USD")).thenReturn(Optional.of(usd));
        when(monedas.findById("EUR")).thenReturn(Optional.of(eur));
        when(monedas.findAllByOrderByFuncionalDescCodigoAsc()).thenReturn(List.of(cop, eur, usd));
        when(tasas.save(any(TasaCambio.class))).thenAnswer(a -> { TasaCambio t = a.getArgument(0); t.setId(1L); return t; });
    }

    private void tasa(String codigo, String valor, LocalDate desde) {
        when(tasas.findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(eq(codigo), any()))
                .thenReturn(Optional.of(TasaCambio.builder().codigoMoneda(codigo).tasa(new BigDecimal(valor)).fechaVigencia(desde).build()));
    }

    @Test @DisplayName("UT-MON-01 la moneda funcional (COP) siempre aplica tasa 1 y no requiere tasa registrada")
    void funcionalTasaUno() {
        MonedaService.Cambio c = servicio.cambioParaDocumento(null);
        assertThat(c.moneda()).isEqualTo("COP");
        assertThat(c.tasa()).isEqualByComparingTo("1");
        assertThat(c.esFuncional()).isTrue();
        assertThat(servicio.cambioParaDocumento("cop").moneda()).isEqualTo("COP");
    }

    @Test @DisplayName("UT-MON-02 la tasa vigente se consulta con la fecha del negocio (America/Bogota)")
    void tasaVigente() {
        tasa("USD", "4123.45", LocalDate.of(2026, 10, 15));
        MonedaService.Cambio c = servicio.cambioParaDocumento("usd");
        assertThat(c.moneda()).isEqualTo("USD");
        assertThat(c.tasa()).isEqualByComparingTo("4123.45");
        verify(tasas).findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc("USD", LocalDate.of(2026, 10, 16));
    }

    @Test @DisplayName("UT-MON-03 sin tasa vigente o con la moneda inactiva no se emiten documentos (422)")
    void sinTasaOInactiva() {
        when(tasas.findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(eq("USD"), any()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.cambioParaDocumento("USD"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SIN_TASA_CAMBIO"));
        eur.setActiva(false);
        assertThatThrownBy(() -> servicio.cambioParaDocumento("EUR"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MONEDA_INVALIDA"));
        assertThatThrownBy(() -> servicio.cambioParaDocumento("XYZ"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MONEDA_INVALIDA"));
    }

    @Test @DisplayName("UT-MON-04 registrar tasa: se audita; no se admiten fechas futuras ni tasas para COP")
    void registrarTasa() {
        var t = servicio.registrarTasa("USD", new TasaCambioRequest(new BigDecimal("4000.5"), LocalDate.of(2026, 10, 16),
                "TRM Banco de la República"), gerente);
        assertThat(t.tasa()).isEqualByComparingTo("4000.5");
        verify(eventos).registrar(eq(gerente), eq(Auditables.MONEDAS), eq(Auditables.TASA_CAMBIO), eq("TASA_CAMBIO"), eq(1L),
                any(), any());
        assertThatThrownBy(() -> servicio.registrarTasa("USD", new TasaCambioRequest(BigDecimal.TEN, LocalDate.of(2026, 10, 17), null), gerente))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("TASA_FUTURA"));
        assertThatThrownBy(() -> servicio.registrarTasa("COP", new TasaCambioRequest(BigDecimal.ONE, LocalDate.of(2026, 10, 16), null), gerente))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MONEDA_FUNCIONAL"));
    }

    @Test @DisplayName("UT-MON-05 conversión entre dos monedas extranjeras pasa por COP y redondea a los decimales de destino")
    void conversion() {
        tasa("USD", "4000", LocalDate.of(2026, 10, 16));
        tasa("EUR", "4400", LocalDate.of(2026, 10, 16));
        var r = servicio.convertir(new BigDecimal("100"), "USD", "EUR");   // 100 × 4000 / 4400 = 90,909… → 90,91
        assertThat(r.resultado()).isEqualByComparingTo("90.91");
        var cop = servicio.convertir(new BigDecimal("12.5"), "USD", "COP");  // 50.000 COP (0 decimales)
        assertThat(cop.resultado()).isEqualByComparingTo("50000");
    }

    @Test @DisplayName("UT-MON-06 la moneda funcional no se desactiva; una extranjera sí, con auditoría")
    void desactivar() {
        assertThatThrownBy(() -> servicio.cambiarEstado("COP", false, gerente))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MONEDA_FUNCIONAL"));
        when(monedas.save(any(Moneda.class))).thenAnswer(a -> a.getArgument(0));
        assertThat(servicio.cambiarEstado("EUR", false, gerente).activa()).isFalse();
        verify(eventos, never()).registrar(any(), eq(Auditables.MONEDAS), eq(Auditables.DESACTIVAR), eq("MONEDA"), eq("COP"), any(), any());
    }
}
