/*
 * Nombre: InventarioService.java
 * Descripción: Consultas y transiciones de inventario (RF-05): existencias por estado, alertas de vencimiento/agotados/stock bajo, lotes, kardex, cambio disponible↔dañado y baja de lotes vencidos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Lote;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.AlertasInventarioResponse;
import com.ferreteria.inventario.dto.CambioEstadoRequest;
import com.ferreteria.inventario.dto.ExistenciaResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.LoteResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.LoteRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las alertas se calculan con la fecha real del negocio y un umbral configurable
 * ({@code app.inventario.dias-alerta-vencimiento}, se puede indicar otro por consulta). Las unidades vencidas o
 * reservadas nunca se presentan como disponibles para la venta.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final LoteRepository lotes;
    private final PrecioService precios;
    private final KardexService kardex;
    private final LoteService loteService;
    private final EventoAuditoriaService eventos;
    private final ParametrosNegocio parametros;

    /**
     * Existencias paginadas con alertas.
     *
     * @param q texto de búsqueda
     * @param dias umbral de vencimiento (nulo = configurado)
     * @param pagina página
     * @param tamano tamaño
     * @return página de existencias
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ExistenciaResponse> existencias(String q, Integer dias, int pagina, int tamano) {
        int umbral = umbral(dias);
        LocalDate hoy = kardex.hoy();
        var page = productos.buscar(q == null ? "" : q.trim(), PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), 100), Sort.by("nombre", "marca", "presentacion")));
        return PaginaResponse.de(page, p -> existencia(p, hoy, umbral));
    }

    /**
     * Alertas de inventario.
     *
     * @param dias umbral (nulo = configurado)
     * @return alertas
     */
    @Transactional(readOnly = true)
    public AlertasInventarioResponse alertas(Integer dias) {
        int umbral = umbral(dias);
        LocalDate hoy = kardex.hoy();
        Map<Long, String> nombres = new HashMap<>();
        List<LoteResponse> vencidos = new ArrayList<>();
        List<LoteResponse> porVencer = new ArrayList<>();
        for (Lote l : lotes.conVencimientoHasta(hoy.plusDays(umbral))) {
            LoteResponse r = LoteResponse.de(l, nombres.computeIfAbsent(l.getIdProducto(), this::nombre), hoy, umbral);
            if ("VENCIDO".equals(r.estado())) {
                vencidos.add(r);
            } else {
                porVencer.add(r);
            }
        }
        List<ExistenciaResponse> agotados = new ArrayList<>();
        for (Inventario i : inventarios.agotados()) {
            productos.findById(i.getIdProducto()).filter(Producto::isActivo)
                    .ifPresent(p -> agotados.add(existencia(p, hoy, umbral)));
        }
        List<ExistenciaResponse> bajos = productos.conStockBajo().stream()
                .filter(p -> inventarios.findById(p.getId()).map(i -> i.getDisponible().signum() > 0).orElse(false))
                .map(p -> existencia(p, hoy, umbral)).toList();
        return new AlertasInventarioResponse(hoy, umbral, vencidos, porVencer, agotados, bajos);
    }

    /**
     * Lotes de un producto con su estado de vencimiento.
     *
     * @param idProducto producto
     * @return lotes
     */
    @Transactional(readOnly = true)
    public List<LoteResponse> lotes(Long idProducto) {
        Producto p = productos.findById(idProducto).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        LocalDate hoy = kardex.hoy();
        int umbral = parametros.getDiasAlertaVencimiento();
        return lotes.deProducto(p.getId()).stream().map(l -> LoteResponse.de(l, p.getNombre(), hoy, umbral)).toList();
    }

    /**
     * Mueve unidades entre disponible y no apto (RN-04): marcar como dañado o recuperar.
     *
     * @param req datos validados
     * @param actor usuario de inventario
     * @return saldos actualizados
     */
    @Transactional(rollbackFor = Exception.class)
    public InventarioResponse cambiarEstado(CambioEstadoRequest req, Actor actor) {
        boolean valido = (req.desde() == EstadoInventario.DISPONIBLE && req.hacia() == EstadoInventario.DANADO)
                || (req.desde() == EstadoInventario.DANADO && req.hacia() == EstadoInventario.DISPONIBLE);
        if (!valido) {
            throw ApiException.regla("TRANSICION_INVALIDA", "error.transicion.invalida", req.desde().getValor(),
                    req.hacia().getValor());
        }
        Producto p = productos.findById(req.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        Inventario inv = kardex.bloquear(p.getId());
        if (KardexService.saldo(inv, req.desde()).compareTo(req.cantidad()) < 0) {
            throw kardex.insuficiente(p.getId(), req.desde(), KardexService.saldo(inv, req.desde()), req.cantidad());
        }
        Long idLote = null;
        if (req.idLote() != null) {
            Lote l = loteService.delProducto(req.idLote(), p.getId());
            if (req.hacia() == EstadoInventario.DISPONIBLE && l.getFechaVencimiento() != null
                    && l.getFechaVencimiento().isBefore(kardex.hoy())) {
                throw ApiException.regla("LOTE_VENCIDO", "error.lote.vencido", l.getCodigo(), String.valueOf(l.getFechaVencimiento()));
            }
            BigDecimal q = req.cantidad();
            loteService.ajustar(l, req.desde() == EstadoInventario.DISPONIBLE ? q.negate() : q,
                    req.desde() == EstadoInventario.DISPONIBLE ? q : q.negate());
            idLote = l.getId();
        } else if (req.desde() == EstadoInventario.DISPONIBLE) {
            loteService.descontarSinLoteDefinido(p, inv, req.cantidad());
        }
        String doc = "CE-" + p.getId() + "-" + kardex.ahora().toLocalDate();
        kardex.mover(inv, req.desde(), req.cantidad().negate(), TipoMovimiento.CAMBIO_ESTADO, actor, doc, req.motivo().trim(), idLote, null);
        kardex.mover(inv, req.hacia(), req.cantidad(), TipoMovimiento.CAMBIO_ESTADO, actor, doc, req.motivo().trim(), idLote, null);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("producto", p.getNombre());
        d.put("desde", req.desde().getValor());
        d.put("hacia", req.hacia().getValor());
        d.put("cantidad", req.cantidad());
        d.put("motivo", req.motivo().trim());
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.CAMBIO_ESTADO, "PRODUCTO", p.getId(),
                "Cambio de estado de " + p.getNombre() + ": " + req.desde().getValor() + " → " + req.hacia().getValor(), d);
        return InventarioResponse.de(inv);
    }

    /**
     * Da de baja el saldo disponible de un lote vencido: pasa a no apto solo ese lote (RN-05).
     *
     * @param idLote lote vencido
     * @param actor usuario de inventario
     * @return lote actualizado
     */
    @Transactional(rollbackFor = Exception.class)
    public LoteResponse darDeBajaLote(Long idLote, Actor actor) {
        Lote previo = lotes.findById(idLote).orElseThrow(() -> ApiException.noEncontrado("Lote"));
        Producto p = productos.findById(previo.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        Inventario inv = kardex.bloquear(p.getId());
        Lote l = loteService.delProducto(idLote, p.getId());
        LocalDate hoy = kardex.hoy();
        if (l.getFechaVencimiento() == null || !l.getFechaVencimiento().isBefore(hoy)) {
            throw ApiException.regla("LOTE_NO_VENCIDO", "error.lote.no.vencido", l.getCodigo());
        }
        BigDecimal q = l.getDisponible();
        if (q.signum() == 0) {
            throw ApiException.regla("LOTE_SIN_SALDO", "error.lote.sin.saldo", l.getCodigo());
        }
        if (inv.getDisponible().compareTo(q) < 0) {
            // Parte del lote está reservada: hay que liberar o cancelar las reservas antes de la baja.
            throw ApiException.conflicto("LOTE_RESERVADO", "error.lote.reservado", l.getCodigo());
        }
        loteService.ajustar(l, q.negate(), q);
        String doc = "BAJA-" + l.getCodigo();
        String motivo = "Baja por vencimiento del lote " + l.getCodigo() + " (" + l.getFechaVencimiento() + ")";
        kardex.mover(inv, EstadoInventario.DISPONIBLE, q.negate(), TipoMovimiento.BAJA_VENCIMIENTO, actor, doc, motivo, l.getId(), null);
        kardex.mover(inv, EstadoInventario.DANADO, q, TipoMovimiento.BAJA_VENCIMIENTO, actor, doc, motivo, l.getId(), null);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.BAJA_VENCIMIENTO, "LOTE", l.getId(), motivo,
                Map.of("producto", p.getNombre(), "lote", l.getCodigo(), "cantidad", q));
        return LoteResponse.de(l, p.getNombre(), hoy, parametros.getDiasAlertaVencimiento());
    }

    private ExistenciaResponse existencia(Producto p, LocalDate hoy, int umbral) {
        Inventario i = inventarios.findById(p.getId()).orElse(Inventario.builder().idProducto(p.getId())
                .disponible(BigDecimal.ZERO).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build());
        boolean porLote = LoteService.porLote(p);
        BigDecimal vencido = porLote ? lotes.sumaVencida(p.getId(), hoy) : BigDecimal.ZERO;
        BigDecimal porVencer = porLote ? lotes.sumaPorVencer(p.getId(), hoy, hoy.plusDays(umbral)) : BigDecimal.ZERO;
        BigDecimal total = i.getDisponible().add(i.getReservado()).add(i.getDanado());
        List<String> alertas = new ArrayList<>();
        if (i.getDisponible().signum() == 0) {
            alertas.add("AGOTADO");
        } else if (p.getStockMinimo() != null && i.getDisponible().compareTo(p.getStockMinimo()) <= 0) {
            alertas.add("STOCK_BAJO");
        }
        if (vencido.signum() > 0) {
            alertas.add("VENCIDO");
        }
        if (porVencer.signum() > 0) {
            alertas.add("POR_VENCER");
        }
        BigDecimal valor = p.getCostoPromedio() == null ? null : Montos.redondear(total.multiply(p.getCostoPromedio()));
        return new ExistenciaResponse(p.getId(), p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(),
                p.getCodigoBarras(), p.getCategoria(), p.getUnidadBase(), p.getTipoRastreo(), p.isActivo(),
                i.getDisponible(), i.getReservado(), i.getDanado(), total, vencido, porVencer, p.getStockMinimo(),
                p.getCostoPromedio(), precios.vigente(p.getId()).orElse(null), valor, alertas);
    }

    private String nombre(Long idProducto) {
        return productos.findById(idProducto).map(p -> p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion())
                .orElse(String.valueOf(idProducto));
    }

    private int umbral(Integer dias) {
        if (dias == null) {
            return parametros.getDiasAlertaVencimiento();
        }
        return Math.min(Math.max(dias, 0), 365);
    }
}
