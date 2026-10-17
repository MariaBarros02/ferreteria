/*
 * Nombre: AjusteService.java
 * Descripción: Ajustes de inventario (RF-14, RN-08, RN-16): solicitud con motivo, aprobación por un usuario distinto, aplicación atómica con movimiento auditado y nota de ajuste vinculada.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoAprobacion;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoAjuste;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.AjusteRequest;
import com.ferreteria.inventario.dto.AjusteResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Un ajuste nunca cambia el saldo al solicitarse: queda pendiente hasta que otro usuario autorizado (Gerencia o
 * Administrador) lo aprueba. Al aprobar se bloquea el saldo, se valida que no quede negativo y se registra el
 * movimiento en la misma transacción (todo o nada). No existe otra vía para modificar el stock directamente.
 */
@Service
@RequiredArgsConstructor
public class AjusteService {

    private final AjusteInventarioRepository ajustes;
    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final MovimientoInventarioRepository movimientos;
    private final LoteRepository lotes;
    private final SesionConteoRepository sesiones;
    private final ConteoFisicoRepository conteos;
    private final UsuarioRepository usuarios;
    private final KardexService kardex;
    private final LoteService loteService;
    private final EventoAuditoriaService eventos;

    /**
     * Solicita un ajuste (queda pendiente).
     *
     * @param req datos validados
     * @param actor solicitante
     * @return ajuste pendiente
     */
    @Transactional
    public AjusteResponse solicitar(AjusteRequest req, Actor actor) {
        if (req.idOperacion() != null) {
            Optional<AjusteInventario> previo = ajustes.findByIdOperacion(req.idOperacion());
            if (previo.isPresent()) {
                return aDto(previo.get());
            }
        }
        Producto p = productos.findById(req.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        if (req.idLote() != null) {
            Lote l = lotes.findById(req.idLote()).orElseThrow(() -> ApiException.noEncontrado("Lote"));
            if (!l.getIdProducto().equals(p.getId())) {
                throw ApiException.regla("LOTE_INVALIDO", "error.lote.invalido", l.getCodigo());
            }
        }
        if (req.idMovimientoOriginal() != null) {
            MovimientoInventario m = movimientos.findById(req.idMovimientoOriginal())
                    .orElseThrow(() -> ApiException.noEncontrado("Movimiento"));
            if (!m.getIdProducto().equals(p.getId())) {
                throw ApiException.regla("MOVIMIENTO_AJENO", "error.movimiento.ajeno", String.valueOf(m.getId()));
            }
        }
        Inventario inv = inventarios.findById(p.getId()).orElseThrow(() -> ApiException.noEncontrado("Inventario"));
        if (req.tipo() == TipoAjuste.SALIDA && inv.getDisponible().compareTo(req.cantidad()) < 0) {
            throw kardex.insuficiente(p.getId(), EstadoInventario.DISPONIBLE, inv.getDisponible(), req.cantidad());
        }
        AjusteInventario a = ajustes.save(AjusteInventario.builder()
                .numero(String.format("AJ-%06d", ajustes.siguienteNumero())).idProducto(p.getId()).idLote(req.idLote())
                .tipo(req.tipo()).cantidad(req.cantidad()).motivo(req.motivo().trim())
                .observaciones(req.observaciones() == null || req.observaciones().isBlank() ? null : req.observaciones().trim())
                .estado(EstadoAprobacion.PENDIENTE).idUsuarioSolicita(actor.idUsuario()).fechaSolicitud(kardex.ahora())
                .saldoSistema(inv.getDisponible()).idMovimientoOriginal(req.idMovimientoOriginal())
                .idOperacion(req.idOperacion()).build());
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.SOLICITAR, "AJUSTE", a.getNumero(),
                "Ajuste " + a.getNumero() + " solicitado (" + req.tipo().getValor() + " " + KardexService.texto(req.cantidad())
                        + " de " + p.getNombre() + ")", detalle(a, p));
        return aDto(a);
    }

    /**
     * Aprueba y aplica un ajuste pendiente.
     *
     * @param id ajuste
     * @param actor aprobador (distinto del solicitante)
     * @return ajuste aprobado
     */
    @Transactional(rollbackFor = Exception.class)
    public AjusteResponse aprobar(Long id, Actor actor) {
        AjusteInventario a = pendiente(id);
        if (a.getIdUsuarioSolicita().equals(actor.idUsuario())) {
            throw ApiException.conflicto("MISMA_PERSONA", "error.misma.persona");
        }
        Producto p = productos.findById(a.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        MovimientoInventario mov = aplicar(p, a.getTipo(), a.getCantidad(), a.getIdLote(), a.getNumero(),
                "Ajuste " + a.getNumero() + ": " + a.getMotivo(), a.getIdMovimientoOriginal(), actor, TipoMovimiento.AJUSTE);
        a.setEstado(EstadoAprobacion.APROBADO);
        a.setIdUsuarioDecide(actor.idUsuario());
        a.setFechaDecision(kardex.ahora());
        a.setIdMovimiento(mov.getId());
        a = ajustes.save(a);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.APROBAR, "AJUSTE", a.getNumero(),
                "Ajuste " + a.getNumero() + " aprobado y aplicado", detalle(a, p));
        return aDto(a);
    }

    /**
     * Rechaza un ajuste pendiente (no altera saldos).
     *
     * @param id ajuste
     * @param motivo motivo del rechazo
     * @param actor usuario que rechaza
     * @return ajuste rechazado
     */
    @Transactional
    public AjusteResponse rechazar(Long id, String motivo, Actor actor) {
        AjusteInventario a = pendiente(id);
        a.setEstado(EstadoAprobacion.RECHAZADO);
        a.setIdUsuarioDecide(actor.idUsuario());
        a.setFechaDecision(kardex.ahora());
        a.setMotivoRechazo(motivo.trim());
        a = ajustes.save(a);
        Producto p = productos.findById(a.getIdProducto()).orElse(null);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.RECHAZAR, "AJUSTE", a.getNumero(),
                "Ajuste " + a.getNumero() + " rechazado", p == null ? Map.of("motivo", motivo.trim()) : detalle(a, p));
        return aDto(a);
    }

    /**
     * Crea y aplica un ajuste ya autorizado por la aprobación de un conteo físico (RF-15).
     *
     * @param p producto
     * @param conteo conteo que lo origina
     * @param sesion sesión de conteo
     * @param actor aprobador del conteo
     * @return ajuste aplicado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AjusteInventario aplicarPorConteo(Producto p, ConteoFisico conteo, SesionConteo sesion, Actor actor) {
        TipoAjuste tipo = conteo.getDiferencia().signum() > 0 ? TipoAjuste.ENTRADA : TipoAjuste.SALIDA;
        BigDecimal cantidad = conteo.getDiferencia().abs();
        String numero = String.format("AJ-%06d", ajustes.siguienteNumero());
        // El movimiento nace con tipo "conteo": los movimientos no se editan después de creados (RN-16).
        MovimientoInventario mov = aplicar(p, tipo, cantidad, null, numero,
                "Diferencia del conteo " + sesion.getNumero(), null, actor, TipoMovimiento.CONTEO);
        return ajustes.save(AjusteInventario.builder().numero(numero).idProducto(p.getId()).tipo(tipo).cantidad(cantidad)
                .motivo("Diferencia del conteo físico " + sesion.getNumero()).estado(EstadoAprobacion.APROBADO)
                .idUsuarioSolicita(sesion.getIdUsuario()).fechaSolicitud(kardex.ahora()).idUsuarioDecide(actor.idUsuario())
                .fechaDecision(kardex.ahora()).saldoSistema(conteo.getCantidadSistema()).idMovimiento(mov.getId())
                .idConteo(conteo.getId()).build());
    }

    /**
     * Listado con filtros.
     *
     * @param estado estado (opcional)
     * @param productoId producto (opcional)
     * @param pagina página
     * @param tamano tamaño
     * @return página de ajustes
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AjusteResponse> listar(EstadoAprobacion estado, Long productoId, int pagina, int tamano) {
        Specification<AjusteInventario> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (estado != null) {
                ps.add(cb.equal(root.get("estado"), estado));
            }
            if (productoId != null) {
                ps.add(cb.equal(root.get("idProducto"), productoId));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var page = ajustes.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, this::aDto);
    }

    /**
     * Detalle de un ajuste.
     *
     * @param id ajuste
     * @return ajuste
     */
    @Transactional(readOnly = true)
    public AjusteResponse obtener(Long id) {
        return aDto(ajustes.findById(id).orElseThrow(() -> ApiException.noEncontrado("Ajuste")));
    }

    /**
     * Aplica el cambio de saldo de un ajuste: bloquea, valida lote y saldo, y registra el movimiento.
     */
    private MovimientoInventario aplicar(Producto p, TipoAjuste tipo, BigDecimal cantidad, Long idLote, String numero,
                                         String motivo, Long idOriginal, Actor actor, TipoMovimiento tipoMovimiento) {
        Inventario inv = kardex.bloquear(p.getId());
        BigDecimal delta = tipo == TipoAjuste.ENTRADA ? cantidad : cantidad.negate();
        if (delta.signum() < 0 && inv.getDisponible().compareTo(cantidad) < 0) {
            throw kardex.insuficiente(p.getId(), EstadoInventario.DISPONIBLE, inv.getDisponible(), cantidad);
        }
        Long loteMovimiento = idLote;
        if (idLote != null) {
            Lote l = loteService.delProducto(idLote, p.getId());
            loteService.ajustar(l, delta, BigDecimal.ZERO);
        } else if (delta.signum() < 0) {
            List<LoteService.Asignacion> asign = loteService.descontarSinLoteDefinido(p, inv, cantidad);
            if (asign.size() == 1) {
                loteMovimiento = asign.get(0).lote().getId();
            }
        }
        return kardex.mover(inv, EstadoInventario.DISPONIBLE, delta, tipoMovimiento, actor, numero, motivo,
                loteMovimiento, idOriginal);
    }

    private AjusteInventario pendiente(Long id) {
        AjusteInventario a = ajustes.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Ajuste"));
        if (a.getEstado() != EstadoAprobacion.PENDIENTE) {
            throw ApiException.conflicto("AJUSTE_YA_DECIDIDO", "error.ajuste.decidido", a.getNumero());
        }
        return a;
    }

    private AjusteResponse aDto(AjusteInventario a) {
        Producto p = productos.findById(a.getIdProducto()).orElse(null);
        String lote = a.getIdLote() == null ? null : lotes.findById(a.getIdLote()).map(Lote::getCodigo).orElse(null);
        BigDecimal actual = inventarios.findById(a.getIdProducto()).map(Inventario::getDisponible).orElse(null);
        String conteo = null;
        if (a.getIdConteo() != null) {
            conteo = conteos.findById(a.getIdConteo()).flatMap(c -> c.getIdSesion() == null ? Optional.empty()
                    : sesiones.findById(c.getIdSesion())).map(SesionConteo::getNumero).orElse(null);
        }
        return new AjusteResponse(a.getId(), a.getNumero(), a.getIdProducto(),
                p == null ? null : p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(), lote, a.getTipo(),
                a.getCantidad(), a.getMotivo(), a.getObservaciones(), a.getEstado(), correo(a.getIdUsuarioSolicita()),
                a.getFechaSolicitud(), correo(a.getIdUsuarioDecide()), a.getFechaDecision(), a.getMotivoRechazo(),
                a.getSaldoSistema(), actual, a.getIdMovimiento(), a.getIdMovimientoOriginal(), conteo);
    }

    private String correo(Integer id) {
        return id == null ? null : usuarios.findById(id).map(Usuario::getCorreo).orElse(null);
    }

    private static Map<String, Object> detalle(AjusteInventario a, Producto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("producto", p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion());
        m.put("tipo", a.getTipo().getValor());
        m.put("cantidad", a.getCantidad());
        m.put("motivo", a.getMotivo());
        m.put("saldoSistema", a.getSaldoSistema());
        m.put("estado", a.getEstado().getValor());
        m.put("movimientoOriginal", a.getIdMovimientoOriginal());
        return m;
    }
}
