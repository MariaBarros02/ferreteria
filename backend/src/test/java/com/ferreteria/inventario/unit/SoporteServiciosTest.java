/*
 * Nombre: SoporteServiciosTest.java
 * Descripción: Pruebas unitarias de servicios transversales: kardex (saldo y período cerrado), lotes (FEFO e ingreso), auditoría (datos sensibles), usuarios (política y último administrador) e inventario (alertas y baja de lotes vencidos).
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.*;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.AlertasInventarioResponse;
import com.ferreteria.inventario.dto.CambioEstadoRequest;
import com.ferreteria.inventario.dto.UsuarioRequest;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/** Casos UT-KAR-01/02, UT-LOT-01/02, UT-AUD-01, UT-USU-01 … 04, UT-INV-01 … 04. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoporteServiciosTest {

    @Mock InventarioRepository inventarios;
    @Mock ProductoRepository productos;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock LoteRepository lotes;
    @Mock EventoAuditoriaRepository eventosRepo;
    @Mock EventoAuditoriaService eventos;
    @Mock UsuarioRepository usuarios;
    @Mock RolRepository roles;
    @Mock PermisoRepository permisos;
    @Mock PasswordEncoder encoder;
    @Mock PrecioService precios;

    final Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
    KardexService kardex;
    Inventario inv;
    Producto pegante;
    final Actor actor = new Actor(3, "marta.inventario@ferreteria.com", "x");

    @BeforeEach
    void preparar() {
        kardex = new KardexService(inventarios, productos, auditoria, cierres, ParametrosNegocio.porDefecto(), reloj);
        inv = Inventario.builder().idProducto(3L).disponible(new BigDecimal("10")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        pegante = Producto.builder().id(3L).nombre("Pegante").marca("Sika").presentacion("250ml")
                .tipoRastreo(TipoRastreo.LOTE).tieneVencimiento(true).build();
        when(productos.findById(3L)).thenReturn(Optional.of(pegante));
        when(inventarios.findForUpdate(3L)).thenReturn(Optional.of(inv));
        when(inventarios.findById(3L)).thenReturn(Optional.of(inv));
        when(lotes.save(any(Lote.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test @DisplayName("UT-KAR-01 ningún movimiento deja un saldo negativo")
    void saldoNoNegativo() {
        assertThatThrownBy(() -> kardex.mover(inv, EstadoInventario.DANADO, new BigDecimal("-1"), TipoMovimiento.CAMBIO_ESTADO,
                actor, "X", "m", null, null))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SALDO_INSUFICIENTE"));
        assertThat(inv.getDanado()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("UT-KAR-02 con el mes cerrado no se registran movimientos (409 PERIODO_CERRADO)")
    void periodoCerrado() {
        when(cierres.existsByPeriodoAndEstado(LocalDate.of(2026, 10, 1), EstadoCierre.CERRADO)).thenReturn(true);
        assertThatThrownBy(() -> kardex.mover(inv, EstadoInventario.DISPONIBLE, BigDecimal.ONE, TipoMovimiento.AJUSTE,
                actor, "X", "m", null, null))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PERIODO_CERRADO"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("10");
    }

    @Test @DisplayName("UT-LOT-01 un lote existente no puede reingresar con otra fecha de vencimiento")
    void loteFechaDistinta() {
        when(lotes.findByIdProductoAndCodigoIgnoreCase(3L, "L1")).thenReturn(Optional.of(Lote.builder().id(1L)
                .idProducto(3L).codigo("L1").fechaVencimiento(LocalDate.of(2026, 12, 1)).disponible(BigDecimal.ONE)
                .danado(BigDecimal.ZERO).build()));
        LoteService s = new LoteService(lotes);
        assertThatThrownBy(() -> s.ingresar(pegante, "L1", LocalDate.of(2027, 1, 1), BigDecimal.ONE, null))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("LOTE_FECHA_DISTINTA"));
    }

    @Test @DisplayName("UT-LOT-02 salida sin lote indicado: primero existencia sin lote, luego lotes vencidos y FEFO")
    void descuentoSinLote() {
        Lote vencido = Lote.builder().id(1L).idProducto(3L).codigo("V").fechaVencimiento(LocalDate.of(2026, 1, 1))
                .disponible(new BigDecimal("2")).danado(BigDecimal.ZERO).build();
        Lote vigente = Lote.builder().id(2L).idProducto(3L).codigo("A").fechaVencimiento(LocalDate.of(2027, 1, 1))
                .disponible(new BigDecimal("5")).danado(BigDecimal.ZERO).build();
        when(lotes.sumaDisponible(3L)).thenReturn(new BigDecimal("7"));   // 3 unidades sin lote
        when(lotes.deProducto(3L)).thenReturn(List.of(vencido, vigente));
        var asign = new LoteService(lotes).descontarSinLoteDefinido(pegante, inv, new BigDecimal("6"));
        assertThat(vencido.getDisponible()).isEqualByComparingTo("0");
        assertThat(vigente.getDisponible()).isEqualByComparingTo("4");
        assertThat(asign).hasSize(2);
    }

    @Test @DisplayName("UT-AUD-01 el detalle de auditoría nunca guarda contraseñas, tokens ni secretos")
    void auditoriaSinDatosSensibles() {
        EventoAuditoriaService real = new EventoAuditoriaService(eventosRepo, new ObjectMapper(), ParametrosNegocio.porDefecto(), reloj);
        when(eventosRepo.save(any(EventoAuditoria.class))).thenAnswer(a -> a.getArgument(0));
        EventoAuditoria e = real.registrar(actor, "USUARIOS", "CREAR", "USUARIO", 1, "x",
                Map.of("correo", "a@b.com", "password", "Secreta123", "token", "abc", "anidado", Map.of("passwordHash", "h", "rol", "Ventas")));
        assertThat(e.getDetalle()).contains("a@b.com").contains("Ventas")
                .doesNotContain("Secreta123").doesNotContain("abc").doesNotContain("passwordHash");
        assertThat(e.getIdUsuario()).isEqualTo(3);
        assertThat(e.getIp()).isEqualTo("x");
    }

    private UsuarioService usuarioService() {
        return new UsuarioService(usuarios, roles, permisos, encoder, eventos);
    }

    @Test @DisplayName("UT-USU-01 contraseña débil se rechaza y nunca se audita la contraseña")
    void claveDebil() {
        when(roles.findByNombreIgnoreCase("Ventas")).thenReturn(Optional.of(Rol.builder().id(1).nombre("Ventas").build()));
        assertThatThrownBy(() -> usuarioService().crear(new UsuarioRequest("Ana", "ana@x.com", "Ventas", EstadoCuenta.ACTIVO,
                "corta"), actor)).satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CLAVE_DEBIL"));
        when(encoder.encode(any())).thenReturn("$2a$hash");
        when(usuarios.save(any(Usuario.class))).thenAnswer(a -> { Usuario u = a.getArgument(0); u.setId(9); return u; });
        usuarioService().crear(new UsuarioRequest("Ana", "Ana@X.com", "Ventas", EstadoCuenta.ACTIVO, "Segura12345"), actor);
        ArgumentCaptor<Map<String, ?>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(eq(actor), eq("USUARIOS"), eq("CREAR"), eq("USUARIO"), eq(9), any(), detalle.capture());
        assertThat(detalle.getValue().toString()).doesNotContain("Segura12345").contains("ana@x.com");
    }

    @Test @DisplayName("UT-USU-02 no se puede dejar el sistema sin administradores activos")
    void ultimoAdministrador() {
        Rol admin = Rol.builder().id(4).nombre("Administrador").build();
        Rol ventas = Rol.builder().id(1).nombre("Ventas").build();
        Usuario otro = Usuario.builder().id(8).nombre("Otro").correo("otro@x.com").rol(admin).estado(EstadoCuenta.ACTIVO).build();
        when(usuarios.findById(8)).thenReturn(Optional.of(otro));
        when(usuarios.findByCorreoIgnoreCase("otro@x.com")).thenReturn(Optional.of(otro));
        when(roles.findByNombreIgnoreCase("Ventas")).thenReturn(Optional.of(ventas));
        when(usuarios.countByRolNombreIgnoreCaseAndEstado("Administrador", EstadoCuenta.ACTIVO)).thenReturn(1L);
        assertThatThrownBy(() -> usuarioService().actualizar(8, new UsuarioRequest("Otro", "otro@x.com", "Ventas",
                EstadoCuenta.ACTIVO, null), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("ULTIMO_ADMINISTRADOR"));
    }

    @Test @DisplayName("UT-USU-03 un administrador no puede desactivarse a sí mismo")
    void autoDegradacion() {
        Rol admin = Rol.builder().id(4).nombre("Administrador").build();
        Usuario yo = Usuario.builder().id(3).nombre("Yo").correo("yo@x.com").rol(admin).estado(EstadoCuenta.ACTIVO).build();
        when(usuarios.findById(3)).thenReturn(Optional.of(yo));
        when(usuarios.findByCorreoIgnoreCase("yo@x.com")).thenReturn(Optional.of(yo));
        when(roles.findByNombreIgnoreCase("Administrador")).thenReturn(Optional.of(admin));
        when(usuarios.countByRolNombreIgnoreCaseAndEstado("Administrador", EstadoCuenta.ACTIVO)).thenReturn(3L);
        assertThatThrownBy(() -> usuarioService().actualizar(3, new UsuarioRequest("Yo", "yo@x.com", "Administrador",
                EstadoCuenta.INACTIVO, null), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("AUTO_DEGRADACION"));
    }

    @Test @DisplayName("UT-USU-04 el cambio de rol queda auditado como CAMBIO_ROL")
    void cambioRolAuditado() {
        Rol ventas = Rol.builder().id(1).nombre("Ventas").build();
        Rol compras = Rol.builder().id(2).nombre("Compras").build();
        Usuario u = Usuario.builder().id(8).nombre("U").correo("u@x.com").rol(ventas).estado(EstadoCuenta.ACTIVO).build();
        when(usuarios.findById(8)).thenReturn(Optional.of(u));
        when(usuarios.findByCorreoIgnoreCase("u@x.com")).thenReturn(Optional.of(u));
        when(roles.findByNombreIgnoreCase("Compras")).thenReturn(Optional.of(compras));
        when(usuarios.save(any(Usuario.class))).thenAnswer(a -> a.getArgument(0));
        usuarioService().actualizar(8, new UsuarioRequest("U", "u@x.com", "Compras", EstadoCuenta.ACTIVO, null), actor);
        verify(eventos).registrar(eq(actor), eq("USUARIOS"), eq("CAMBIO_ROL"), eq("USUARIO"), eq(8), any(), any());
    }

    private InventarioService inventarioService() {
        return new InventarioService(productos, inventarios, lotes, precios, kardex, new LoteService(lotes), eventos,
                ParametrosNegocio.porDefecto());
    }

    @Test @DisplayName("UT-INV-01 alertas: lotes vencidos y por vencer se calculan con fechas reales y el umbral")
    void alertas() {
        Lote v = Lote.builder().id(1L).idProducto(3L).codigo("V").fechaVencimiento(LocalDate.of(2026, 10, 15))
                .disponible(BigDecimal.ONE).danado(BigDecimal.ZERO).build();
        Lote p = Lote.builder().id(2L).idProducto(3L).codigo("P").fechaVencimiento(LocalDate.of(2026, 11, 10))
                .disponible(BigDecimal.ONE).danado(BigDecimal.ZERO).build();
        when(lotes.conVencimientoHasta(LocalDate.of(2026, 11, 15))).thenReturn(List.of(v, p));
        when(inventarios.agotados()).thenReturn(List.of());
        when(productos.conStockBajo()).thenReturn(List.of());
        AlertasInventarioResponse a = inventarioService().alertas(30);
        assertThat(a.fechaReferencia()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(a.vencidos()).extracting(x -> x.codigo()).containsExactly("V");
        assertThat(a.porVencer()).extracting(x -> x.codigo()).containsExactly("P");
        assertThat(a.porVencer().get(0).diasParaVencer()).isEqualTo(25L);
    }

    @Test @DisplayName("UT-INV-02 baja por vencimiento: solo el lote afectado pasa a no apto")
    void bajaLoteVencido() {
        Lote v = Lote.builder().id(1L).idProducto(3L).codigo("V").fechaVencimiento(LocalDate.of(2026, 10, 1))
                .disponible(new BigDecimal("4")).danado(BigDecimal.ZERO).build();
        when(lotes.findById(1L)).thenReturn(Optional.of(v));
        when(lotes.findForUpdate(1L)).thenReturn(Optional.of(v));
        inventarioService().darDeBajaLote(1L, actor);
        assertThat(v.getDisponible()).isEqualByComparingTo("0");
        assertThat(v.getDanado()).isEqualByComparingTo("4");
        assertThat(inv.getDisponible()).isEqualByComparingTo("6");
        assertThat(inv.getDanado()).isEqualByComparingTo("4");
        verify(auditoria).registrar(eq(TipoMovimiento.BAJA_VENCIMIENTO), eq(3L), eq(new BigDecimal("-4")), any(), any(), any(),
                any(), any(), any(), eq(EstadoInventario.DISPONIBLE), eq(1L), any());
    }

    @Test @DisplayName("UT-INV-03 un lote no vencido no se da de baja por vencimiento")
    void loteNoVencido() {
        Lote l = Lote.builder().id(1L).idProducto(3L).codigo("A").fechaVencimiento(LocalDate.of(2026, 10, 16))
                .disponible(new BigDecimal("4")).danado(BigDecimal.ZERO).build();
        when(lotes.findById(1L)).thenReturn(Optional.of(l));
        when(lotes.findForUpdate(1L)).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> inventarioService().darDeBajaLote(1L, actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("LOTE_NO_VENCIDO"));
        verify(auditoria, never()).registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("UT-INV-04 cambio de estado disponible → dañado mueve ambos saldos; reservado no es destino válido")
    void cambioEstado() {
        when(lotes.sumaDisponible(3L)).thenReturn(BigDecimal.ZERO);
        when(lotes.deProducto(3L)).thenReturn(List.of());
        inventarioService().cambiarEstado(new CambioEstadoRequest(3L, null, EstadoInventario.DISPONIBLE,
                EstadoInventario.DANADO, new BigDecimal("2"), "Golpe"), actor);
        assertThat(inv.getDisponible()).isEqualByComparingTo("8");
        assertThat(inv.getDanado()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> inventarioService().cambiarEstado(new CambioEstadoRequest(3L, null, EstadoInventario.DISPONIBLE,
                EstadoInventario.RESERVADO, BigDecimal.ONE, "x"), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("TRANSICION_INVALIDA"));
    }
}
