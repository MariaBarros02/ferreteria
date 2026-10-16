/*
 * Nombre: CierreService.java
 * Descripción: Cierre mensual (RF-08): resumen del período, conciliación saldo vs. movimientos, validación de pendientes, constancia del cierre, reapertura auditada y bloqueo de movimientos en meses cerrados.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoCierre;
import com.ferreteria.inventario.domain.model.CierrePeriodo;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.CierreResponse;
import com.ferreteria.inventario.dto.CierreResumenResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.CierrePeriodoRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alcance implementado (documentado): cierre <b>operativo mensual de inventario y ventas</b>. No es un cierre
 * contable ni de caja (el sistema no gestiona pagos ni contabilidad). Mientras un mes está cerrado no se
 * registran movimientos de inventario con fecha de ese mes ({@link KardexService#verificarPeriodoAbierto()}).
 * Para cerrar no deben quedar ajustes ni conteos sin decidir del período; si la conciliación saldo/movimientos
 * muestra diferencias, el cierre exige una observación que las explique.
 */
@Service
@RequiredArgsConstructor
public class CierreService {

    private final CierrePeriodoRepository cierres;
    private final UsuarioRepository usuarios;
    private final JdbcTemplate jdbc;
    private final KardexService kardex;
    private final EventoAuditoriaService eventos;
    private final ParametrosNegocio parametros;
    private final ObjectMapper json;

    /**
     * Resumen de un período.
     *
     * @param periodo AAAA-MM
     * @return resumen
     */
    @Transactional(readOnly = true)
    public CierreResumenResponse resumen(String periodo) {
        YearMonth mes = mes(periodo);
        LocalDateTime desde = Fechas.inicioUtc(mes.atDay(1), parametros.getZonaHoraria());
        LocalDateTime hasta = Fechas.finExclusivoUtc(mes.atEndOfMonth(), parametros.getZonaHoraria());

        Map<String, Object> v = jdbc.queryForMap("""
                select count(*) as n, coalesce(sum(subtotal), 0) as s, coalesce(sum(total_descuentos), 0) as d,
                       coalesce(sum(total_impuestos), 0) as i, coalesce(sum(total), 0) as t
                from venta where estado = 'activo' and fecha >= ? and fecha < ?""", desde, hasta);
        Map<String, Object> dc = jdbc.queryForMap("""
                select count(*) as n, coalesce(sum(total), 0) as t from documento_devolucion
                where tipo = 'cliente' and estado = 'activo' and fecha >= ? and fecha < ?""", desde, hasta);
        Map<String, Object> dp = jdbc.queryForMap("""
                select count(*) as n, coalesce(sum(total), 0) as t from documento_devolucion
                where tipo = 'proveedor' and estado = 'activo' and fecha >= ? and fecha < ?""", desde, hasta);
        Map<String, Object> co = jdbc.queryForMap("""
                select count(distinct d.id_documento_ingreso) as n,
                       coalesce(sum(r.cantidad_recibida * coalesce(r.costo_unitario, lc.costo_pactado)), 0) as t
                from recepcion r
                join documento_ingreso d on d.id_documento_ingreso = r.id_documento_ingreso
                join linea_compra lc on lc.id_linea_compra = r.id_linea_compra
                where d.estado = 'activo' and d.fecha >= ? and d.fecha < ?""", desde, hasta);
        List<CierreResumenResponse.Movimientos> movs = jdbc.queryForList("""
                select tipo::text as tipo, estado_inventario as estado, count(*) as n, coalesce(sum(cantidad), 0) as u
                from movimiento_inventario where fecha_hora >= ? and fecha_hora < ?
                group by 1, 2 order by 1, 2""", desde, hasta).stream()
                .map(r -> new CierreResumenResponse.Movimientos(str(r.get("tipo")), str(r.get("estado")),
                        num(r.get("n")).longValue(), dec(r.get("u")))).toList();
        List<CierreResumenResponse.Diferencia> difs = jdbc.queryForList("""
                select i.id_producto as id, p.nombre || ' ' || p.marca || ' ' || p.presentacion as producto,
                       i.disponible as registrado, coalesce(sum(m.cantidad), 0) as esperado
                from inventario i
                join producto p on p.id_producto = i.id_producto
                left join movimiento_inventario m on m.id_producto = i.id_producto and m.estado_inventario = 'disponible'
                group by i.id_producto, p.nombre, p.marca, p.presentacion, i.disponible
                having i.disponible <> coalesce(sum(m.cantidad), 0)
                order by i.id_producto limit 50""").stream()
                .map(r -> new CierreResumenResponse.Diferencia(num(r.get("id")).longValue(), str(r.get("producto")),
                        dec(r.get("registrado")), dec(r.get("esperado")))).toList();
        long ajustes = contar("select count(*) from ajuste_inventario where estado = 'pendiente' and fecha_solicitud < ?", hasta);
        long conteos = contar("select count(*) from sesion_conteo where estado in ('abierta', 'cerrada') and fecha_inicio < ?", hasta);
        long precios = contar("select count(*) from precio where estado_aprobacion = 'propuesto' and fecha_propuesta < ?", hasta);
        long reservas = jdbc.queryForObject("select count(*) from reserva where estado = 'activa' and fecha_limite < ?",
                Long.class, kardex.hoy());
        List<String> bloqueantes = new ArrayList<>();
        if (ajustes > 0) {
            bloqueantes.add("Hay " + ajustes + " ajuste(s) de inventario pendiente(s) de aprobación.");
        }
        if (conteos > 0) {
            bloqueantes.add("Hay " + conteos + " sesión(es) de conteo físico sin aprobar o rechazar.");
        }
        CierreResponse cierre = cierres.findByPeriodo(mes.atDay(1)).map(this::aDto).orElse(null);
        String estado = cierre == null ? "ABIERTO" : cierre.estado() == EstadoCierre.CERRADO ? "CERRADO" : "REABIERTO";
        return new CierreResumenResponse(mes.toString(), mes.atDay(1), mes.atEndOfMonth(), estado,
                num(v.get("n")).longValue(), dec(v.get("s")), dec(v.get("d")), dec(v.get("i")), dec(v.get("t")),
                num(dc.get("n")).longValue(), dec(dc.get("t")), num(dp.get("n")).longValue(), dec(dp.get("t")),
                num(co.get("n")).longValue(), Montos.redondear(dec(co.get("t"))), movs, difs,
                new CierreResumenResponse.Pendientes(ajustes, conteos, precios, reservas), bloqueantes, cierre);
    }

    /**
     * Cierra un período (o lo vuelve a cerrar tras una reapertura).
     *
     * @param periodo AAAA-MM
     * @param observaciones observaciones
     * @param actor responsable de cierre (Gerencia)
     * @return cierre registrado
     */
    @Transactional
    public CierreResponse cerrar(String periodo, String observaciones, Actor actor) {
        YearMonth mes = mes(periodo);
        if (mes.isAfter(YearMonth.from(kardex.hoy()))) {
            throw ApiException.regla("PERIODO_FUTURO", "error.periodo.futuro", mes.toString());
        }
        CierrePeriodo existente = cierres.findForUpdate(mes.atDay(1)).orElse(null);
        if (existente != null && existente.getEstado() == EstadoCierre.CERRADO) {
            throw ApiException.conflicto("PERIODO_YA_CERRADO", "error.periodo.ya.cerrado", mes.toString());
        }
        CierreResumenResponse r = resumen(periodo);
        if (!r.bloqueantes().isEmpty()) {
            throw ApiException.conflicto("CIERRE_CON_PENDIENTES", "error.cierre.pendientes", String.join(" ", r.bloqueantes()));
        }
        String obs = observaciones == null || observaciones.isBlank() ? null : observaciones.trim();
        if (!r.diferenciasConciliacion().isEmpty() && obs == null) {
            throw ApiException.regla("CIERRE_REQUIERE_OBSERVACION", "error.cierre.observacion",
                    String.valueOf(r.diferenciasConciliacion().size()));
        }
        CierrePeriodo c = existente != null ? existente : CierrePeriodo.builder().periodo(mes.atDay(1)).build();
        c.setEstado(EstadoCierre.CERRADO);
        c.setFechaCierre(kardex.ahora());
        c.setIdUsuario(actor.idUsuario());
        c.setNumeroVentas((int) r.numeroVentas());
        c.setTotalVentas(Montos.redondear(r.totalVentas()));
        c.setTotalDevoluciones(Montos.redondear(r.totalDevolucionesClientes()));
        c.setTotalComprasRecibidas(r.valorComprasRecibidas());
        c.setNumeroMovimientos((int) r.movimientos().stream().mapToLong(CierreResumenResponse.Movimientos::cantidad).sum());
        c.setObservaciones(obs);
        c.setResumen(aJson(r));
        c = cierres.save(c);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("periodo", mes.toString());
        d.put("ventas", r.numeroVentas());
        d.put("totalVentas", r.totalVentas());
        d.put("diferenciasConciliacion", r.diferenciasConciliacion().size());
        d.put("reapertura", existente != null);
        eventos.registrar(actor, Auditables.CIERRES, Auditables.CERRAR, "CIERRE", mes.toString(),
                "Período " + mes + " cerrado", d);
        return aDto(c);
    }

    /**
     * Reabre un período cerrado (queda constancia del motivo).
     *
     * @param periodo AAAA-MM
     * @param motivo motivo obligatorio
     * @param actor responsable de cierre (Gerencia)
     * @return cierre reabierto
     */
    @Transactional
    public CierreResponse reabrir(String periodo, String motivo, Actor actor) {
        YearMonth mes = mes(periodo);
        CierrePeriodo c = cierres.findForUpdate(mes.atDay(1))
                .orElseThrow(() -> ApiException.noEncontrado("Cierre"));
        if (c.getEstado() != EstadoCierre.CERRADO) {
            throw ApiException.conflicto("PERIODO_NO_CERRADO", "error.periodo.no.cerrado", mes.toString());
        }
        c.setEstado(EstadoCierre.REABIERTO);
        c.setFechaReapertura(kardex.ahora());
        c.setIdUsuarioReapertura(actor.idUsuario());
        c.setMotivoReapertura(motivo.trim());
        c = cierres.save(c);
        eventos.registrar(actor, Auditables.CIERRES, Auditables.REABRIR, "CIERRE", mes.toString(),
                "Período " + mes + " reabierto", Map.of("motivo", motivo.trim()));
        return aDto(c);
    }

    /**
     * Historial de cierres.
     *
     * @return cierres, del más reciente al más antiguo
     */
    @Transactional(readOnly = true)
    public List<CierreResponse> listar() {
        return cierres.findAll(Sort.by(Sort.Direction.DESC, "periodo")).stream().map(this::aDto).toList();
    }

    private CierreResponse aDto(CierrePeriodo c) {
        return new CierreResponse(c.getId(), YearMonth.from(c.getPeriodo()).toString(), c.getEstado(), c.getFechaCierre(),
                correo(c.getIdUsuario()), c.getNumeroVentas(), c.getTotalVentas(), c.getTotalDevoluciones(),
                c.getTotalComprasRecibidas(), c.getNumeroMovimientos(), c.getObservaciones(), c.getFechaReapertura(),
                correo(c.getIdUsuarioReapertura()), c.getMotivoReapertura());
    }

    private String correo(Integer id) {
        return id == null ? null : usuarios.findById(id).map(Usuario::getCorreo).orElse(null);
    }

    private long contar(String sql, LocalDateTime hasta) {
        Long n = jdbc.queryForObject(sql, Long.class, hasta);
        return n == null ? 0 : n;
    }

    private String aJson(CierreResumenResponse r) {
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Interpreta un período AAAA-MM.
     *
     * @param periodo texto
     * @return mes
     */
    static YearMonth mes(String periodo) {
        try {
            return YearMonth.parse(periodo == null ? "" : periodo.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.regla("PERIODO_INVALIDO", "error.periodo.invalido", String.valueOf(periodo));
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }

    private static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) {
            return b;
        }
        return o instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    /**
     * Primer día del mes de una fecha.
     *
     * @param fecha fecha
     * @return primer día del mes
     */
    static LocalDate inicioMes(LocalDate fecha) {
        return fecha.withDayOfMonth(1);
    }
}
