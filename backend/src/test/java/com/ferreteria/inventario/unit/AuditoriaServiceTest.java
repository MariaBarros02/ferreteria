/*
 * Nombre: AuditoriaServiceTest.java
 * Descripción: Pruebas unitarias para el registro y consulta de movimientos
 *              de inventario en AuditoriaService.
 * Fecha de creación: 2026-10-09
 * Fecha de actualización: 2026-10-09
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.repository.MovimientoInventarioRepository;
import com.ferreteria.inventario.service.AuditoriaService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private static final Instant INSTANTE =
            Instant.parse("2026-10-09T15:30:00Z");

    @Mock
    private MovimientoInventarioRepository movimientos;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @BeforeEach
    void configurarReloj() {
        auditoriaService = new AuditoriaService(
                movimientos,
                Clock.fixed(INSTANTE, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Registra un movimiento con todos sus campos")
    void registraMovimientoConTodosLosCampos() {
        when(movimientos.save(any(MovimientoInventario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        MovimientoInventario resultado = auditoriaService.registrar(
                TipoMovimiento.COMPRA,
                10L,
                new BigDecimal("5.00"),
                7,
                "127.0.0.1",
                "FAC-001",
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                "Reposición de inventario"
        );

        ArgumentCaptor<MovimientoInventario> captor =
                ArgumentCaptor.forClass(MovimientoInventario.class);

        verify(movimientos).save(captor.capture());

        MovimientoInventario guardado = captor.getValue();

        assertThat(resultado).isNotNull();
        assertThat(guardado.getTipo()).isEqualTo(TipoMovimiento.COMPRA);
        assertThat(guardado.getIdProducto()).isEqualTo(10L);
        assertThat(guardado.getCantidad())
                .isEqualByComparingTo("5.00");
        assertThat(guardado.getIdUsuario()).isEqualTo(7);
        assertThat(guardado.getIp()).isEqualTo("127.0.0.1");
        assertThat(guardado.getDocumentoRelacionado()).isEqualTo("FAC-001");
        assertThat(guardado.getValorAnterior())
                .isEqualByComparingTo("10.00");
        assertThat(guardado.getValorNuevo())
                .isEqualByComparingTo("15.00");
        assertThat(guardado.getMotivo())
                .isEqualTo("Reposición de inventario");
        assertThat(guardado.getFechaHora())
                .isEqualTo(LocalDateTime.ofInstant(INSTANTE, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Consulta todos los movimientos cuando no se indica producto")
    void consultaTodosLosMovimientosCuandoProductoEsNulo() {
        Page<MovimientoInventario> pagina =
                new PageImpl<>(List.of());

        when(movimientos.findAll(any(Pageable.class)))
                .thenReturn(pagina);

        PaginaResponse<MovimientoResponse> resultado =
                auditoriaService.consultar(null, 0, 20);

        assertThat(resultado).isNotNull();
        verify(movimientos).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Filtra los movimientos por producto")
    void consultaMovimientosPorProducto() {
        Long idProducto = 10L;

        Page<MovimientoInventario> pagina =
                new PageImpl<>(List.of());

        when(movimientos.findByIdProducto(
                eq(idProducto), any(Pageable.class)))
                .thenReturn(pagina);

        PaginaResponse<MovimientoResponse> resultado =
                auditoriaService.consultar(idProducto, 0, 20);

        assertThat(resultado).isNotNull();
        verify(movimientos).findByIdProducto(
                eq(idProducto), any(Pageable.class));
    }

    @Test
    @DisplayName("Limita el tamaño de página al máximo de 100")
    void limitaTamanoDePagina() {
        when(movimientos.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditoriaService.consultar(null, -1, 500);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(movimientos).findAll(captor.capture());

        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Ajusta el tamaño de página cuando es menor que uno")
    void ajustaTamanoDePaginaMinimo() {
        when(movimientos.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditoriaService.consultar(null, 0, 0);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(movimientos).findAll(captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
