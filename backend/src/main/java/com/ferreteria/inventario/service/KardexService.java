/*
 * Nombre: KardexService.java
 * Descripción: Núcleo de movimientos de inventario: bloquea el saldo, valida que no quede negativo, verifica que el período esté abierto y registra el movimiento auditado.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoCierre;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.CierrePeriodoRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única puerta para cambiar saldos de inventario. Garantiza, dentro de la transacción de la operación:
 * <ul>
 *   <li>bloqueo de la fila de inventario ({@code FOR UPDATE}) — RN-11;</li>
 *   <li>ningún saldo negativo — RN-03;</li>
 *   <li>un movimiento auditado por cada cambio de saldo — RF-08, RNF-06;</li>
 *   <li>ningún movimiento en un mes cerrado — RF-08.</li>
 * </ul>
 * Invariante: para cada producto y estado, saldo = suma de los movimientos de ese estado.
 */
@Service
@RequiredArgsConstructor
public class KardexService {

    private final InventarioRepository inventarios;
    private final ProductoRepository productos;
    private final AuditoriaService auditoria;
    private final CierrePeriodoRepository cierres;
    private final ParametrosNegocio parametros;
    private final Clock reloj;

    /**
     * Fecha actual en la zona horaria del negocio.
     *
     * @return hoy
     */
    public LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), parametros.getZonaHoraria());
    }

    /**
     * Instante actual en UTC (formato de almacenamiento).
     *
     * @return ahora
     */
    public LocalDateTime ahora() {
        return LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC);
    }

    /**
     * Bloquea el saldo de un producto hasta el fin de la transacción.
     *
     * @param idProducto producto
     * @return saldo bloqueado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Inventario bloquear(Long idProducto) {
        return inventarios.findForUpdate(idProducto).orElseThrow(() -> ApiException.noEncontrado("Inventario"));
    }

    /**
     * Rechaza la operación si el mes en curso está cerrado.
     *
     * @throws ApiException 409 PERIODO_CERRADO
     */
    public void verificarPeriodoAbierto() {
        YearMonth mes = YearMonth.from(hoy());
        if (cierres.existsByPeriodoAndEstado(mes.atDay(1), EstadoCierre.CERRADO)) {
            throw ApiException.conflicto("PERIODO_CERRADO", "error.periodo.cerrado", mes.toString());
        }
    }

    /**
     * Cambia el saldo de un estado y registra el movimiento.
     *
     * @param inv saldo bloqueado con {@link #bloquear(Long)}
     * @param estado saldo afectado
     * @param delta cantidad con signo (positivo entra, negativo sale)
     * @param tipo tipo de movimiento
     * @param actor usuario responsable
     * @param documento documento relacionado
     * @param motivo motivo
     * @param idLote lote afectado (opcional)
     * @param idMovimientoOriginal movimiento que se rectifica (opcional)
     * @return movimiento registrado
     * @throws ApiException 409 si el saldo quedaría negativo o el período está cerrado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoInventario mover(Inventario inv, EstadoInventario estado, BigDecimal delta, TipoMovimiento tipo,
                                      Actor actor, String documento, String motivo, Long idLote,
                                      Long idMovimientoOriginal) {
        verificarPeriodoAbierto();
        BigDecimal anterior = saldo(inv, estado);
        BigDecimal nuevo = anterior.add(delta);
        if (nuevo.signum() < 0) {
            throw insuficiente(inv.getIdProducto(), estado, anterior, delta.negate());
        }
        asignar(inv, estado, nuevo);
        inventarios.save(inv);
        return auditoria.registrar(tipo, inv.getIdProducto(), delta, actor.idUsuario(), actor.ip(), documento,
                anterior, nuevo, motivo, estado, idLote, idMovimientoOriginal);
    }

    /**
     * Construye el error de saldo insuficiente con el nombre del producto.
     *
     * @param idProducto producto
     * @param estado saldo
     * @param saldo saldo actual
     * @param solicitado cantidad solicitada
     * @return excepción 409
     */
    public ApiException insuficiente(Long idProducto, EstadoInventario estado, BigDecimal saldo, BigDecimal solicitado) {
        String nombre = productos.findById(idProducto).map(Producto::getNombre).orElse(String.valueOf(idProducto));
        if (estado == EstadoInventario.DISPONIBLE) {
            return ApiException.conflicto("SALDO_INSUFICIENTE", "error.saldo.insuficiente", nombre, texto(saldo),
                    texto(solicitado));
        }
        return ApiException.conflicto("SALDO_INSUFICIENTE", "error.saldo.estado.insuficiente", nombre, texto(saldo),
                texto(solicitado), estado.getValor());
    }

    /**
     * Saldo de un estado.
     *
     * @param inv inventario
     * @param estado estado
     * @return saldo
     */
    public static BigDecimal saldo(Inventario inv, EstadoInventario estado) {
        return switch (estado) {
            case DISPONIBLE -> inv.getDisponible();
            case RESERVADO -> inv.getReservado();
            case DANADO -> inv.getDanado();
        };
    }

    private static void asignar(Inventario inv, EstadoInventario estado, BigDecimal valor) {
        switch (estado) {
            case DISPONIBLE -> inv.setDisponible(valor);
            case RESERVADO -> inv.setReservado(valor);
            case DANADO -> inv.setDanado(valor);
            default -> throw new IllegalArgumentException(String.valueOf(estado));
        }
    }

    /**
     * Representación legible de una cantidad (sin ceros a la derecha).
     *
     * @param v cantidad
     * @return texto
     */
    public static String texto(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
