/*
 * Nombre: ReservaService.java
 * Descripción: Reservas de inventario (RF-05, RN-03): paso de disponible a reservado y liberación solo por venta, cancelación autorizada o vencimiento de la fecha límite.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoReserva;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Reserva;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ReservaRequest;
import com.ferreteria.inventario.dto.ReservaResponse;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.LoteRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.ReservaRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una reserva nunca puede exceder el disponible vendible (sin contar lotes vencidos). No existe liberación
 * "por compra" (RN-03).
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservas;
    private final ProductoRepository productos;
    private final TerceroRepository terceros;
    private final UsuarioRepository usuarios;
    private final LoteRepository lotes;
    private final KardexService kardex;
    private final EventoAuditoriaService eventos;

    /**
     * Crea una reserva.
     *
     * @param req datos validados
     * @param actor usuario de ventas
     * @return reserva activa
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservaResponse crear(ReservaRequest req, Actor actor) {
        Producto p = productos.findById(req.productoId()).filter(Producto::isActivo)
                .orElseThrow(() -> ApiException.noEncontrado("Producto"));
        Tercero c = terceros.findById(req.clienteId()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        if (!c.isCliente()) {
            throw ApiException.regla("CLIENTE_INVALIDO", "error.cliente.invalido", c.getNumeroIdentificacion());
        }
        if (req.fechaLimite().isBefore(kardex.hoy())) {
            throw ApiException.regla("FECHA_LIMITE_INVALIDA", "error.fecha.limite");
        }
        Inventario inv = kardex.bloquear(p.getId());
        BigDecimal vencido = LoteService.porLote(p) ? lotes.sumaVencida(p.getId(), kardex.hoy()) : BigDecimal.ZERO;
        BigDecimal vendible = inv.getDisponible().subtract(vencido).max(BigDecimal.ZERO);
        if (vendible.compareTo(req.cantidad()) < 0) {
            throw kardex.insuficiente(p.getId(), EstadoInventario.DISPONIBLE, vendible, req.cantidad());
        }
        Reserva r = reservas.save(Reserva.builder().idProducto(p.getId()).idCliente(c.getId()).idPedido(req.idPedido().trim())
                .cantidad(req.cantidad()).fechaLimite(req.fechaLimite()).estado(EstadoReserva.ACTIVA)
                .idUsuario(actor.idUsuario()).fechaCreacion(kardex.ahora()).build());
        String doc = "RES-" + r.getId();
        kardex.mover(inv, EstadoInventario.DISPONIBLE, req.cantidad().negate(), TipoMovimiento.RESERVA, actor, doc,
                "Reserva para " + TerceroResponse.nombreDe(c) + " (pedido " + r.getIdPedido() + ")", null, null);
        kardex.mover(inv, EstadoInventario.RESERVADO, req.cantidad(), TipoMovimiento.RESERVA, actor, doc,
                "Reserva para " + TerceroResponse.nombreDe(c) + " (pedido " + r.getIdPedido() + ")", null, null);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.RESERVAR, "RESERVA", r.getId(),
                "Reserva " + doc + " de " + KardexService.texto(req.cantidad()) + " " + p.getNombre(),
                Map.of("cliente", TerceroResponse.nombreDe(c), "pedido", r.getIdPedido(), "fechaLimite", r.getFechaLimite().toString()));
        return aDto(r);
    }

    /**
     * Cancela una reserva activa (liberación manual autorizada).
     *
     * @param id reserva
     * @param motivo motivo
     * @param actor usuario autorizado
     * @return reserva liberada
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservaResponse cancelar(Long id, String motivo, Actor actor) {
        Reserva r = activa(id);
        liberar(r, EstadoReserva.LIBERADA_CANCELACION, motivo.trim(), actor);
        return aDto(r);
    }

    /**
     * Libera las reservas cuya fecha límite ya pasó.
     *
     * @param actor usuario que ejecuta (o el sistema)
     * @return cantidad liberada
     */
    @Transactional(rollbackFor = Exception.class)
    public int liberarVencidas(Actor actor) {
        int n = 0;
        for (Reserva r : reservas.findByEstadoAndFechaLimiteBefore(EstadoReserva.ACTIVA, kardex.hoy())) {
            Reserva bloqueada = reservas.findForUpdate(r.getId()).orElse(null);
            if (bloqueada != null && bloqueada.getEstado() == EstadoReserva.ACTIVA) {
                liberar(bloqueada, EstadoReserva.LIBERADA_VENCIMIENTO, "Vencimiento de la fecha límite " + r.getFechaLimite(), actor);
                n++;
            }
        }
        return n;
    }

    /**
     * Reservas activas cuya fecha límite ya pasó (para la tarea programada).
     *
     * @return identificadores
     */
    @Transactional(readOnly = true)
    public List<Long> idsVencidas() {
        return reservas.findByEstadoAndFechaLimiteBefore(EstadoReserva.ACTIVA, kardex.hoy()).stream().map(Reserva::getId).toList();
    }

    /**
     * Libera una reserva vencida usando como responsable a quien la creó (el movimiento exige un usuario, RF-08).
     *
     * @param id reserva
     * @return 1 si se liberó, 0 si no aplicaba
     */
    @Transactional(rollbackFor = Exception.class)
    public int liberarUna(Long id) {
        Reserva r = reservas.findForUpdate(id).orElse(null);
        if (r == null || r.getEstado() != EstadoReserva.ACTIVA || !r.getFechaLimite().isBefore(kardex.hoy())
                || r.getIdUsuario() == null) {
            return 0;
        }
        String correo = usuarios.findById(r.getIdUsuario()).map(Usuario::getCorreo).orElse(null);
        liberar(r, EstadoReserva.LIBERADA_VENCIMIENTO, "Vencimiento de la fecha límite " + r.getFechaLimite(),
                new Actor(r.getIdUsuario(), correo, null));
        return 1;
    }

    /**
     * Listado con filtros.
     *
     * @param estado estado (opcional)
     * @param productoId producto (opcional)
     * @param clienteId cliente (opcional)
     * @param pagina página
     * @param tamano tamaño
     * @return página de reservas
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> listar(EstadoReserva estado, Long productoId, Long clienteId, int pagina, int tamano) {
        Specification<Reserva> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (estado != null) {
                ps.add(cb.equal(root.get("estado"), estado));
            }
            if (productoId != null) {
                ps.add(cb.equal(root.get("idProducto"), productoId));
            }
            if (clienteId != null) {
                ps.add(cb.equal(root.get("idCliente"), clienteId));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var page = reservas.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, this::aDto);
    }

    private Reserva activa(Long id) {
        Reserva r = reservas.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Reserva"));
        if (r.getEstado() != EstadoReserva.ACTIVA) {
            throw ApiException.conflicto("RESERVA_NO_ACTIVA", "error.reserva.no.activa", String.valueOf(id));
        }
        return r;
    }

    private void liberar(Reserva r, EstadoReserva estado, String motivo, Actor actor) {
        Inventario inv = kardex.bloquear(r.getIdProducto());
        String doc = "RES-" + r.getId();
        kardex.mover(inv, EstadoInventario.RESERVADO, r.getCantidad().negate(), TipoMovimiento.RESERVA, actor, doc, motivo, null, null);
        kardex.mover(inv, EstadoInventario.DISPONIBLE, r.getCantidad(), TipoMovimiento.RESERVA, actor, doc, motivo, null, null);
        r.setEstado(estado);
        r.setFechaLiberacion(kardex.ahora());
        r.setMotivoLiberacion(motivo);
        reservas.save(r);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.LIBERAR, "RESERVA", r.getId(),
                "Reserva " + doc + " liberada (" + estado.getValor() + ")", Map.of("motivo", motivo));
    }

    private ReservaResponse aDto(Reserva r) {
        String producto = productos.findById(r.getIdProducto())
                .map(p -> p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion()).orElse(null);
        String cliente = terceros.findById(r.getIdCliente()).map(TerceroResponse::nombreDe).orElse(null);
        String usuario = r.getIdUsuario() == null ? null : usuarios.findById(r.getIdUsuario()).map(Usuario::getCorreo).orElse(null);
        return new ReservaResponse(r.getId(), r.getIdProducto(), producto, r.getIdCliente(), cliente, r.getIdPedido(),
                r.getCantidad(), r.getFechaLimite(), r.getEstado(), usuario, r.getFechaCreacion(), r.getFechaLiberacion(),
                r.getMotivoLiberacion(), r.getIdVenta());
    }
}
