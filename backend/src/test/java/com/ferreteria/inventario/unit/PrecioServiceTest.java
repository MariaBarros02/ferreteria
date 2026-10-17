/*
 * Nombre: PrecioServiceTest.java
 * Descripción: Pruebas unitarias de propuesta y aprobación de precios (RF-07, RF-16, RN-07): cálculo, segregación de funciones, rechazo y estados.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.PrecioPropuestaRequest;
import com.ferreteria.inventario.dto.PrecioResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.service.Actor;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.service.PrecioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/** Casos UT-PRE-01 … UT-PRE-07. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecioServiceTest {

    @Mock PrecioRepository precios;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock EventoAuditoriaService eventos;

    PrecioService servicio;
    Producto producto;
    final List<Precio> guardados = new ArrayList<>();
    final Actor inventario = new Actor(3, "marta.inventario@ferreteria.com", "x");
    final Actor gerencia = new Actor(5, "gerencia@ferreteria.com", "x");

    @BeforeEach
    void preparar() {
        servicio = new PrecioService(precios, productos, usuarios, eventos, ParametrosNegocio.porDefecto(),
                Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC));
        producto = Producto.builder().id(1L).nombre("Taladro").marca("Bosch").presentacion("500W")
                .costoPromedio(new BigDecimal("12000")).porcentajeIncremento(new BigDecimal("0.30")).build();
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(precios.save(any(Precio.class))).thenAnswer(a -> {
            Precio p = a.getArgument(0);
            if (p.getId() == null) {
                p.setId((long) guardados.size() + 1);
                guardados.add(p);
            }
            return p;
        });
        when(precios.findForUpdate(anyLong())).thenAnswer(a -> guardados.stream()
                .filter(p -> p.getId().equals(a.getArgument(0))).findFirst());
        when(precios.vigentes(eq(1L), any())).thenAnswer(a -> guardados.stream()
                .filter(p -> p.getPrecioVigente() != null).toList());
        when(precios.findByIdProductoAndEstadoAprobacion(anyLong(), any())).thenReturn(List.of());
        when(usuarios.findById(any())).thenReturn(Optional.empty());
    }

    private PrecioResponse proponer() {
        return servicio.proponer(new PrecioPropuestaRequest(1L, null, null, null, null), inventario);
    }

    @Test @DisplayName("UT-PRE-01 propuesta por recargo: costo $12.000 × 1,30 = $15.600 y no queda vigente")
    void proponerRecargo() {
        PrecioResponse r = proponer();
        assertThat(r.precioPropuesto()).isEqualByComparingTo("15600");
        assertThat(r.estado()).isEqualTo("propuesto");
        assertThat(r.precioVigente()).isNull();
        assertThat(r.precioActual()).isNull();
    }

    @Test @DisplayName("UT-PRE-02 sin costo promedio no se calcula (422); el precio manual exige justificación")
    void sinCosto() {
        producto.setCostoPromedio(null);
        assertThatThrownBy(this::proponer).satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SIN_COSTO"));
        PrecioResponse r = servicio.proponer(new PrecioPropuestaRequest(1L, "manual", null, new BigDecimal("20000"),
                "Precio de lanzamiento"), inventario);
        assertThat(r.precioPropuesto()).isEqualByComparingTo("20000");
        assertThat(r.modoCalculo()).isEqualTo("manual");
    }

    @Test @DisplayName("UT-PRE-03 aprobar vuelve vigente el precio y registra aprobador y fechas")
    void aprobar() {
        proponer();
        PrecioResponse r = servicio.aprobar(1L, gerencia);
        assertThat(r.estado()).isEqualTo("aprobado");
        assertThat(r.precioVigente()).isEqualByComparingTo("15600");
        assertThat(guardados.get(0).getUsuarioAprobador()).isEqualTo(5);
        assertThat(guardados.get(0).getFechaVigencia()).isNotNull();
    }

    @Test @DisplayName("UT-PRE-04 quien propone no puede aprobar su propia propuesta")
    void segregacion() {
        proponer();
        assertThatThrownBy(() -> servicio.aprobar(1L, inventario))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MISMA_PERSONA"));
        assertThat(guardados.get(0).getPrecioVigente()).isNull();
    }

    @Test @DisplayName("UT-PRE-05 una propuesta rechazada no afecta el precio vigente y no se puede aprobar después")
    void rechazar() {
        proponer();
        servicio.rechazar(1L, "Muy alto", gerencia);
        assertThat(guardados.get(0).getEstadoAprobacion()).isEqualTo("rechazado");
        assertThat(guardados.get(0).getPrecioVigente()).isNull();
        assertThatThrownBy(() -> servicio.aprobar(1L, gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRECIO_YA_DECIDIDO"));
    }

    @Test @DisplayName("UT-PRE-06 el rechazo exige motivo")
    void rechazoSinMotivo() {
        proponer();
        assertThatThrownBy(() -> servicio.rechazar(1L, " ", gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MOTIVO_OBLIGATORIO"));
    }

    @Test @DisplayName("UT-PRE-07 la propuesta automática no se duplica si el precio vigente ya es el calculado")
    void automaticaSinDuplicados() {
        proponer();
        servicio.aprobar(1L, gerencia);
        assertThat(servicio.proponerAutomatico(producto, inventario)).isEmpty();
        verify(eventos, never()).registrar(eq(inventario), any(), eq("PROPONER"), any(), eq(2L), any(), any());
    }
}
