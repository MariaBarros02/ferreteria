/*
 * Nombre: ReservaServiceTest.java
 * Descripción: Pruebas unitarias de reservas (RF-05, RN-03): reserva sobre el disponible vendible (sin lotes vencidos), validaciones, cancelación autorizada, liberación de reservas vencidas (en bloque y una a una), listado con filtros y datos de la respuesta.
 * Fecha de creación: 2026-10-19
 * Fecha de actualización: 2026-10-19
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoReserva;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Reserva;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ReservaRequest;
import com.ferreteria.inventario.dto.ReservaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Casos UT-RES-01 … UT-RES-11. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservaServiceTest {

    @Mock ReservaRepository reservas;
    @Mock ProductoRepository productos;
    @Mock TerceroRepository terceros;
    @Mock UsuarioRepository usuarios;
    @Mock LoteRepository lotes;
    @Mock InventarioRepository inventarios;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;

    static final LocalDate HOY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);
    final Actor ventas = new Actor(1, "laura.ventas@ferreteria.com", "10.0.0.1");

    ReservaService servicio;
    Inventario invTaladro;
    Inventario invPegante;
    Reserva guardada;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, ParametrosNegocio.porDefecto(), reloj);
        servicio = new ReservaService(reservas, productos, terceros, usuarios, lotes, kardex, eventos);

        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).nombre("Taladro").marca("Bosch")
                .presentacion("500W").tipoRastreo(TipoRastreo.NINGUNO).build()));
        when(productos.findById(11L)).thenReturn(Optional.of(Producto.builder().id(11L).nombre("Pegante").marca("Sika")
                .presentacion("250ml").tipoRastreo(TipoRastreo.LOTE).tieneVencimiento(true).build()));
        when(productos.findById(12L)).thenReturn(Optional.of(Producto.builder().id(12L).nombre("Viejo").marca("X")
                .presentacion("1").tipoRastreo(TipoRastreo.NINGUNO).activo(false).build()));
        invTaladro = Inventario.builder().idProducto(10L).disponible(new BigDecimal("10")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        invPegante = Inventario.builder().idProducto(11L).disponible(new BigDecimal("8")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        when(inventarios.findForUpdate(10L)).thenReturn(Optional.of(invTaladro));
        when(inventarios.findForUpdate(11L)).thenReturn(Optional.of(invPegante));

        when(terceros.findById(4L)).thenReturn(Optional.of(Tercero.builder().id(4L).cliente(true)
                .razonSocial("Constructora XYZ").numeroIdentificacion("800").build()));
        when(terceros.findById(9L)).thenReturn(Optional.of(Tercero.builder().id(9L).cliente(false).proveedor(true)
                .razonSocial("Distribuidora").numeroIdentificacion("900").build()));
        when(usuarios.findById(1)).thenReturn(Optional.of(Usuario.builder().id(1).correo("laura.ventas@ferreteria.com").build()));

        when(reservas.save(any(Reserva.class))).thenAnswer(a -> {
            Reserva r = a.getArgument(0);
            if (r.getId() == null) {
                r.setId(70L);
            }
            guardada = r;
            return r;
        });
        when(reservas.findForUpdate(70L)).thenAnswer(a -> Optional.ofNullable(guardada));
        when(auditoria.registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(a -> MovimientoInventario.builder().id(900L).build());
    }

    private ReservaResponse reservar(Long producto, Long cliente, String cantidad, LocalDate limite) {
        return servicio.crear(new ReservaRequest(producto, cliente, "  PED-1  ", new BigDecimal(cantidad), limite), ventas);
    }

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private Reserva activaVencida(Long id, Long idProducto, String cantidad, Integer idUsuario) {
        return Reserva.builder().id(id).idProducto(idProducto).idCliente(4L).idPedido("P-" + id)
                .cantidad(new BigDecimal(cantidad)).fechaLimite(LocalDate.of(2026, 10, 10)).estado(EstadoReserva.ACTIVA)
                .idUsuario(idUsuario).build();
    }

    @Test @DisplayName("UT-RES-01 crear: pasa de disponible a reservado, queda ACTIVA y la respuesta trae producto, cliente y usuario")
    @SuppressWarnings("unchecked")
    void crear() {
        ReservaResponse r = reservar(10L, 4L, "4", LocalDate.of(2026, 10, 20));

        assertThat(r).isNotNull();
        assertThat(r.id()).isEqualTo(70L);
        assertThat(r.producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(r.cliente()).isEqualTo("Constructora XYZ");
        assertThat(r.idPedido()).isEqualTo("PED-1");
        assertThat(r.cantidad()).isEqualByComparingTo("4");
        assertThat(r.estado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(r.usuario()).isEqualTo("laura.ventas@ferreteria.com");
        assertThat(r.fechaCreacion()).isEqualTo(AHORA);
        assertThat(r.fechaLimite()).isEqualTo(LocalDate.of(2026, 10, 20));

        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("6");
        assertThat(invTaladro.getReservado()).isEqualByComparingTo("4");
        verify(auditoria, times(2)).registrar(eq(TipoMovimiento.RESERVA), eq(10L), any(), eq(1), any(), eq("RES-70"),
                any(), any(), any(), any(), any(), any());

        ArgumentCaptor<Map<String, Object>> d = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(eq(ventas), eq(Auditables.INVENTARIO), eq(Auditables.RESERVAR), eq("RESERVA"), eq(70L),
                anyString(), d.capture());
        assertThat(d.getValue()).containsEntry("cliente", "Constructora XYZ").containsEntry("pedido", "PED-1")
                .containsEntry("fechaLimite", "2026-10-20");
    }

    @Test @DisplayName("UT-RES-02 se puede reservar exactamente el disponible, pero no más")
    void reservarAlLimite() {
        assertThatThrownBy(() -> reservar(10L, 4L, "11", HOY))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("SALDO_INSUFICIENTE"));
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("10");

        reservar(10L, 4L, "10", HOY);
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("0");
        assertThat(invTaladro.getReservado()).isEqualByComparingTo("10");
    }

    @Test @DisplayName("UT-RES-03 producto por lote: las unidades de lotes vencidos no se pueden reservar")
    void loteVencidoNoReservable() {
        when(lotes.sumaVencida(11L, HOY)).thenReturn(new BigDecimal("3"));    // vendible: 8 − 3 = 5
        assertThatThrownBy(() -> reservar(11L, 4L, "6", HOY))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("SALDO_INSUFICIENTE"));
        reservar(11L, 4L, "5", HOY);
        assertThat(invPegante.getReservado()).isEqualByComparingTo("5");

        when(lotes.sumaVencida(11L, HOY)).thenReturn(new BigDecimal("50"));   // vendible nunca negativo
        assertThatThrownBy(() -> reservar(11L, 4L, "1", HOY))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("SALDO_INSUFICIENTE"));
        verify(lotes, never()).sumaVencida(eq(10L), any());
    }

    @Test @DisplayName("UT-RES-04 la fecha límite puede ser hoy pero no una fecha pasada")
    void fechaLimite() {
        assertThatThrownBy(() -> reservar(10L, 4L, "1", HOY.minusDays(1)))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("FECHA_LIMITE_INVALIDA"));
        assertThat(reservar(10L, 4L, "1", HOY).fechaLimite()).isEqualTo(HOY);
    }

    @Test @DisplayName("UT-RES-05 producto inexistente o inactivo, cliente inexistente o tercero que no es cliente se rechazan")
    void validaciones() {
        assertThatThrownBy(() -> reservar(404L, 4L, "1", HOY)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> reservar(12L, 4L, "1", HOY)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> reservar(10L, 404L, "1", HOY)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> reservar(10L, 9L, "1", HOY)).satisfies(e -> assertThat(codigo(e)).isEqualTo("CLIENTE_INVALIDO"));
        verify(reservas, never()).save(any());
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("10");
    }

    @Test @DisplayName("UT-RES-06 cancelar devuelve el reservado a disponible, registra fecha y motivo; solo reservas activas")
    void cancelar() {
        reservar(10L, 4L, "4", HOY);

        ReservaResponse r = servicio.cancelar(70L, "  Cliente desistió  ", ventas);

        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo(EstadoReserva.LIBERADA_CANCELACION);
        assertThat(r.fechaLiberacion()).isEqualTo(AHORA);
        assertThat(r.motivoLiberacion()).isEqualTo("Cliente desistió");
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("10");
        assertThat(invTaladro.getReservado()).isEqualByComparingTo("0");
        verify(eventos).registrar(eq(ventas), eq(Auditables.INVENTARIO), eq(Auditables.LIBERAR), eq("RESERVA"), eq(70L),
                eq("Reserva RES-70 liberada (liberada_cancelacion)"), eq(Map.of("motivo", "Cliente desistió")));

        assertThatThrownBy(() -> servicio.cancelar(70L, "otra vez", ventas))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("RESERVA_NO_ACTIVA"));
        assertThatThrownBy(() -> servicio.cancelar(999L, "x", ventas))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-RES-07 liberar vencidas: solo libera las que siguen activas al bloquearlas y devuelve cuántas")
    void liberarVencidas() {
        invTaladro.setReservado(new BigDecimal("3"));
        Reserva vencida = activaVencida(81L, 10L, "3", 1);
        Reserva yaLiberada = activaVencida(82L, 10L, "1", 1);
        Reserva desaparecida = activaVencida(83L, 10L, "1", 1);
        when(reservas.findByEstadoAndFechaLimiteBefore(EstadoReserva.ACTIVA, HOY))
                .thenReturn(List.of(vencida, yaLiberada, desaparecida));
        when(reservas.findForUpdate(81L)).thenReturn(Optional.of(vencida));
        when(reservas.findForUpdate(82L)).thenReturn(Optional.of(Reserva.builder().id(82L).idProducto(10L)
                .cantidad(BigDecimal.ONE).estado(EstadoReserva.LIBERADA_VENTA).build()));
        when(reservas.findForUpdate(83L)).thenReturn(Optional.empty());

        int n = servicio.liberarVencidas(ventas);

        assertThat(n).isEqualTo(1);
        assertThat(vencida.getEstado()).isEqualTo(EstadoReserva.LIBERADA_VENCIMIENTO);
        assertThat(vencida.getMotivoLiberacion()).isEqualTo("Vencimiento de la fecha límite 2026-10-10");
        assertThat(vencida.getFechaLiberacion()).isEqualTo(AHORA);
        assertThat(invTaladro.getReservado()).isEqualByComparingTo("0");
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("13");
        verify(reservas, times(1)).save(any());
    }

    @Test @DisplayName("UT-RES-08 identificadores de reservas activas vencidas (para la tarea programada)")
    void idsVencidas() {
        when(reservas.findByEstadoAndFechaLimiteBefore(EstadoReserva.ACTIVA, HOY))
                .thenReturn(List.of(activaVencida(81L, 10L, "1", 1), activaVencida(82L, 10L, "1", 1)));
        assertThat(servicio.idsVencidas()).containsExactly(81L, 82L);
    }

    @Test @DisplayName("UT-RES-09 liberar una: solo si existe, está activa, venció y tiene creador; el responsable es quien la creó")
    void liberarUna() {
        assertThat(servicio.liberarUna(404L)).isZero();

        when(reservas.findForUpdate(84L)).thenReturn(Optional.of(Reserva.builder().id(84L).idProducto(10L)
                .cantidad(BigDecimal.ONE).fechaLimite(LocalDate.of(2026, 10, 10)).estado(EstadoReserva.LIBERADA_VENTA)
                .idUsuario(1).build()));
        assertThat(servicio.liberarUna(84L)).isZero();

        when(reservas.findForUpdate(85L)).thenReturn(Optional.of(Reserva.builder().id(85L).idProducto(10L)
                .cantidad(BigDecimal.ONE).fechaLimite(HOY).estado(EstadoReserva.ACTIVA).idUsuario(1).build()));
        assertThat(servicio.liberarUna(85L)).isZero();                       // vence hoy: aún no está vencida

        when(reservas.findForUpdate(86L)).thenReturn(Optional.of(activaVencida(86L, 10L, "1", null)));
        assertThat(servicio.liberarUna(86L)).isZero();                       // sin usuario responsable
        verify(reservas, never()).save(any());

        invTaladro.setReservado(new BigDecimal("2"));
        Reserva r = activaVencida(87L, 10L, "2", 1);
        when(reservas.findForUpdate(87L)).thenReturn(Optional.of(r));

        assertThat(servicio.liberarUna(87L)).isEqualTo(1);
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.LIBERADA_VENCIMIENTO);
        assertThat(invTaladro.getDisponible()).isEqualByComparingTo("12");
        Actor creador = new Actor(1, "laura.ventas@ferreteria.com", null);
        verify(eventos).registrar(eq(creador), eq(Auditables.INVENTARIO), eq(Auditables.LIBERAR), eq("RESERVA"), eq(87L),
                eq("Reserva RES-87 liberada (liberada_vencimiento)"), any());
    }

    @Test @DisplayName("UT-RES-10 listar: filtros por estado, producto y cliente; página desde 0 y tamaño 1–100")
    @SuppressWarnings("unchecked")
    void listar() {
        Reserva r = activaVencida(81L, 10L, "1", 1);
        when(reservas.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(r), a.getArgument(1), 1));

        PaginaResponse<ReservaResponse> p = servicio.listar(EstadoReserva.ACTIVA, 10L, 4L, -1, 500);
        assertThat(p).isNotNull();
        assertThat(p.pagina()).isZero();
        assertThat(p.tamano()).isEqualTo(100);
        assertThat(p.contenido()).extracting(ReservaResponse::id).containsExactly(81L);

        servicio.listar(null, null, null, 0, 0);
        ArgumentCaptor<Specification<Reserva>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(reservas, times(2)).findAll(spec.capture(), pag.capture());
        assertThat(pag.getAllValues().get(1).getPageSize()).isEqualTo(1);

        Root<Reserva> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> pEstado = mock(Path.class);
        Path<Object> pProducto = mock(Path.class);
        Path<Object> pCliente = mock(Path.class);
        Predicate porEstado = mock(Predicate.class);
        Predicate porProducto = mock(Predicate.class);
        Predicate porCliente = mock(Predicate.class);
        Predicate todos = mock(Predicate.class);
        Predicate ninguno = mock(Predicate.class);
        doReturn(pEstado).when(root).get("estado");
        doReturn(pProducto).when(root).get("idProducto");
        doReturn(pCliente).when(root).get("idCliente");
        when(cb.equal(pEstado, EstadoReserva.ACTIVA)).thenReturn(porEstado);
        when(cb.equal(pProducto, 10L)).thenReturn(porProducto);
        when(cb.equal(pCliente, 4L)).thenReturn(porCliente);
        when(cb.and(porEstado, porProducto, porCliente)).thenReturn(todos);
        when(cb.and()).thenReturn(ninguno);

        assertThat(spec.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(todos);
        assertThat(spec.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(ninguno);
    }

    @Test @DisplayName("UT-RES-11 la respuesta tolera producto, cliente o usuario inexistentes")
    @SuppressWarnings("unchecked")
    void respuestaSinRelaciones() {
        Reserva huerfana = Reserva.builder().id(90L).idProducto(404L).idCliente(404L).idPedido("P-90")
                .cantidad(BigDecimal.ONE).fechaLimite(HOY).estado(EstadoReserva.ACTIVA).build();
        when(reservas.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(huerfana), a.getArgument(1), 1));

        ReservaResponse r = servicio.listar(null, null, null, 0, 10).contenido().get(0);

        assertThat(r.producto()).isNull();
        assertThat(r.cliente()).isNull();
        assertThat(r.usuario()).isNull();
        verify(usuarios, never()).findById(any());
    }
}