/*
 * Nombre: LoteService.java
 * Descripción: Gestión de lotes: ingreso con fecha de vencimiento, asignación FEFO en salidas, reintegro y descuento sin lote definido (RN-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Lote;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.LoteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de lotes. Convención de concurrencia: todo cambio en los lotes de un producto se hace después de
 * bloquear su fila de inventario ({@link KardexService#bloquear(Long)}), de modo que las operaciones sobre los
 * lotes de un mismo producto quedan serializadas.
 * <p>
 * Invariante: suma(lote.disponible) ≤ inventario.disponible + inventario.reservado. La diferencia es existencia
 * "sin lote" (inventario anterior a la gestión de lotes o entradas sin lote), cuya fecha de vencimiento no se
 * conoce y nunca se inventa.
 */
@Service
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository lotes;

    /**
     * Cantidad tomada de un lote.
     *
     * @param lote lote
     * @param cantidad cantidad
     */
    public record Asignacion(Lote lote, BigDecimal cantidad) { }

    /**
     * Indica si el producto se rastrea por lote.
     *
     * @param p producto
     * @return {@code true} si su rastreo es por lote
     */
    public static boolean porLote(Producto p) {
        return p.getTipoRastreo() == TipoRastreo.LOTE;
    }

    /**
     * Existencia vendible o reservada que no pertenece a ningún lote.
     *
     * @param p producto
     * @param inv saldo actual (antes de aplicar la operación)
     * @return cantidad sin lote (nunca negativa)
     */
    public BigDecimal sinLote(Producto p, Inventario inv) {
        return inv.getDisponible().add(inv.getReservado()).subtract(lotes.sumaDisponible(p.getId())).max(BigDecimal.ZERO);
    }

    /**
     * Asigna una salida vendible en orden FEFO (primero el lote que vence antes), sin usar lotes vencidos;
     * si los lotes no alcanzan, usa la existencia sin lote.
     *
     * @param p producto
     * @param inv saldo bloqueado (antes de aplicar la salida)
     * @param cantidad cantidad en unidad base
     * @param hoy fecha de referencia
     * @return asignaciones (vacía si el producto no se rastrea por lote o se usó solo existencia sin lote)
     * @throws ApiException 409 STOCK_VENCIDO si la cantidad solo está cubierta por lotes vencidos
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Asignacion> asignarSalida(Producto p, Inventario inv, BigDecimal cantidad, LocalDate hoy) {
        if (!porLote(p)) {
            return List.of();
        }
        BigDecimal libre = sinLote(p, inv);
        List<Asignacion> r = new ArrayList<>();
        BigDecimal pendiente = cantidad;
        for (Lote l : lotes.vendiblesParaActualizar(p.getId(), hoy)) {
            if (pendiente.signum() <= 0) {
                break;
            }
            BigDecimal q = l.getDisponible().min(pendiente);
            l.setDisponible(l.getDisponible().subtract(q));
            lotes.save(l);
            r.add(new Asignacion(l, q));
            pendiente = pendiente.subtract(q);
        }
        if (pendiente.signum() > 0 && pendiente.compareTo(libre) > 0) {
            BigDecimal vendible = cantidad.subtract(pendiente).add(libre);
            throw ApiException.conflicto("STOCK_VENCIDO", "error.stock.vencido", p.getNombre(),
                    KardexService.texto(vendible), KardexService.texto(cantidad));
        }
        return r;
    }

    /**
     * Descuenta una salida cuando el usuario no indicó lote (ajustes o conteos): primero la existencia sin lote,
     * luego los lotes vencidos y por último los vigentes en orden FEFO.
     *
     * @param p producto
     * @param inv saldo bloqueado (antes de aplicar la salida)
     * @param cantidad cantidad
     * @return asignaciones a lotes
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Asignacion> descontarSinLoteDefinido(Producto p, Inventario inv, BigDecimal cantidad) {
        if (!porLote(p)) {
            return List.of();
        }
        BigDecimal pendiente = cantidad.subtract(sinLote(p, inv)).max(BigDecimal.ZERO);
        List<Asignacion> r = new ArrayList<>();
        for (Lote l : lotes.deProducto(p.getId())) {
            if (pendiente.signum() <= 0) {
                break;
            }
            BigDecimal q = l.getDisponible().min(pendiente);
            if (q.signum() > 0) {
                l.setDisponible(l.getDisponible().subtract(q));
                lotes.save(l);
                r.add(new Asignacion(l, q));
                pendiente = pendiente.subtract(q);
            }
        }
        return r;
    }

    /**
     * Ingresa unidades a un lote (lo crea si no existe).
     *
     * @param p producto
     * @param codigo código del lote
     * @param vencimiento fecha de vencimiento (obligatoria si el producto vence)
     * @param cantidad cantidad
     * @param ahora fecha de ingreso
     * @return lote actualizado
     * @throws ApiException 422 si el lote ya existe con otra fecha de vencimiento
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Lote ingresar(Producto p, String codigo, LocalDate vencimiento, BigDecimal cantidad, LocalDateTime ahora) {
        String cod = codigo.trim();
        Lote l = lotes.findByIdProductoAndCodigoIgnoreCase(p.getId(), cod).orElse(null);
        if (l == null) {
            l = Lote.builder().idProducto(p.getId()).codigo(cod).fechaVencimiento(vencimiento)
                    .disponible(BigDecimal.ZERO).danado(BigDecimal.ZERO).fechaIngreso(ahora).build();
        } else if (!Objects.equals(l.getFechaVencimiento(), vencimiento)) {
            throw ApiException.regla("LOTE_FECHA_DISTINTA", "error.lote.fecha.distinta", cod,
                    String.valueOf(l.getFechaVencimiento()));
        }
        l.setDisponible(l.getDisponible().add(cantidad));
        return lotes.save(l);
    }

    /**
     * Suma o resta unidades a un lote validando que no quede negativo.
     *
     * @param l lote
     * @param deltaDisponible cambio en unidades aptas
     * @param deltaDanado cambio en unidades no aptas
     * @return lote actualizado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Lote ajustar(Lote l, BigDecimal deltaDisponible, BigDecimal deltaDanado) {
        BigDecimal d = l.getDisponible().add(deltaDisponible);
        BigDecimal x = l.getDanado().add(deltaDanado);
        if (d.signum() < 0 || x.signum() < 0) {
            throw ApiException.conflicto("SALDO_LOTE_INSUFICIENTE", "error.lote.saldo", l.getCodigo(),
                    KardexService.texto(deltaDisponible.signum() < 0 ? l.getDisponible() : l.getDanado()));
        }
        l.setDisponible(d);
        l.setDanado(x);
        return lotes.save(l);
    }

    /**
     * Obtiene y valida que el lote pertenezca al producto.
     *
     * @param idLote lote
     * @param idProducto producto esperado
     * @return lote bloqueado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Lote delProducto(Long idLote, Long idProducto) {
        Lote l = lotes.findForUpdate(idLote).orElseThrow(() -> ApiException.noEncontrado("Lote"));
        if (!l.getIdProducto().equals(idProducto)) {
            throw ApiException.regla("LOTE_INVALIDO", "error.lote.invalido", l.getCodigo());
        }
        return l;
    }
}
