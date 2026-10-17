/*
 * Nombre: DevolucionService.java
 * Descripción: Devoluciones de cliente y a proveedor (RF-13): documento independiente vinculado al original, validación de cantidades acumuladas, destino según el estado del producto, lotes e importe.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoProductoDevuelto;
import com.ferreteria.inventario.domain.enums.TipoDevolucion;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.DevolucionClienteRequest;
import com.ferreteria.inventario.dto.DevolucionProveedorRequest;
import com.ferreteria.inventario.dto.DevolucionResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devoluciones. Reglas (RF-13, RN-08):
 * <ul>
 *   <li>la devolución es un documento propio; la venta/factura original no se modifica;</li>
 *   <li>lo devuelto acumulado nunca supera lo vendido (o lo recibido, en compras); la venta se bloquea para
 *       que dos devoluciones simultáneas no superen el límite;</li>
 *   <li>solo el producto "apto" vuelve a disponible; dañado o vencido va al saldo no apto (nunca se reintegra
 *       automáticamente); un producto apto de un lote ya vencido también va a no apto;</li>
 *   <li>el importe es la parte proporcional del total persistido de la línea (precio, descuento e IVA de la venta).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DevolucionService {

    private final DocumentoDevolucionRepository documentos;
    private final DevolucionRepository lineasDevolucion;
    private final VentaRepository ventas;
    private final CompraRepository compras;
    private final LineaCompraRepository lineasCompra;
    private final LineaVentaLoteRepository lineaVentaLotes;
    private final LoteRepository lotes;
    private final ProductoRepository productos;
    private final TerceroRepository terceros;
    private final UsuarioRepository usuarios;
    private final KardexService kardex;
    private final LoteService loteService;
    private final EventoAuditoriaService eventos;
    private final com.ferreteria.inventario.config.ParametrosNegocio parametros;

    /**
     * Filtros del listado.
     *
     * @param tipo cliente o proveedor
     * @param q texto contenido en el número
     * @param ventaId venta
     * @param compraId compra
     * @param desde fecha inicial
     * @param hasta fecha final
     */
    public record Filtros(TipoDevolucion tipo, String q, Long ventaId, Long compraId, LocalDate desde, LocalDate hasta) { }

    private record Parte(Long idLote, BigDecimal cantidad) { }

    /**
     * Registra una devolución de cliente.
     *
     * @param req datos validados
     * @param actor usuario responsable
     * @return devolución registrada (o la existente si la clave de operación ya se usó)
     */
    @Transactional(rollbackFor = Exception.class)
    public DevolucionResponse registrarCliente(DevolucionClienteRequest req, Actor actor) {
        Optional<DocumentoDevolucion> previa = previa(req.idOperacion());
        if (previa.isPresent()) {
            return aDto(previa.get());
        }
        Venta v = ventas.findForUpdate(req.ventaId()).orElseThrow(() -> ApiException.noEncontrado("Venta"));
        if (v.getEstado() != EstadoDocumento.ACTIVO) {
            throw ApiException.conflicto("VENTA_ANULADA", "error.venta.anulada", v.getNumeroInterno());
        }
        Map<Long, LineaVenta> porId = new HashMap<>();
        v.getLineas().forEach(l -> porId.put(l.getId(), l));
        DocumentoDevolucion doc = nuevoDocumento(TipoDevolucion.CLIENTE, v.getId(), null, req.motivo(), req.tratamiento(),
                req.observaciones(), req.idOperacion(), actor);
        LocalDate hoy = kardex.hoy();
        Map<Long, BigDecimal> enEsta = new HashMap<>();
        Map<String, BigDecimal> enEstaPorLote = new HashMap<>();
        List<DevolucionClienteRequest.Linea> ordenadas = new ArrayList<>(req.lineas());
        for (DevolucionClienteRequest.Linea l : ordenadas) {
            if (!porId.containsKey(l.idLineaVenta())) {
                throw ApiException.regla("LINEA_AJENA", "error.linea.ajena", String.valueOf(l.idLineaVenta()), v.getNumeroInterno());
            }
        }
        ordenadas.sort(Comparator.comparing(l -> porId.get(l.idLineaVenta()).getIdProducto()));
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (DevolucionClienteRequest.Linea l : ordenadas) {
            LineaVenta lv = porId.get(l.idLineaVenta());
            Producto p = productos.findById(lv.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            BigDecimal previo = lineasDevolucion.devueltoDeLineaVenta(lv.getId(), EstadoDocumento.ACTIVO)
                    .add(enEsta.getOrDefault(lv.getId(), BigDecimal.ZERO));
            if (previo.add(l.cantidad()).compareTo(lv.getCantidad()) > 0) {
                throw ApiException.conflicto("DEVOLUCION_EXCEDE", "error.devolucion.excede", p.getNombre(),
                        KardexService.texto(lv.getCantidad()), KardexService.texto(previo), KardexService.texto(l.cantidad()));
            }
            enEsta.merge(lv.getId(), l.cantidad(), BigDecimal::add);
            Inventario inv = kardex.bloquear(p.getId());
            EstadoInventario destino = l.estadoProducto() == EstadoProductoDevuelto.APTO
                    ? EstadoInventario.DISPONIBLE : EstadoInventario.DANADO;
            BigDecimal totalLinea = lv.getTotal() != null ? lv.getTotal()
                    : Montos.redondear(lv.getCantidad().multiply(lv.getPrecioAplicado()));
            for (Parte parte : partesCliente(p, lv, l.cantidad(), enEstaPorLote)) {
                Lote lote = parte.idLote() == null ? null : loteService.delProducto(parte.idLote(), p.getId());
                boolean loteVencido = lote != null && lote.getFechaVencimiento() != null
                        && lote.getFechaVencimiento().isBefore(hoy);
                EstadoInventario dest = destino == EstadoInventario.DISPONIBLE && loteVencido ? EstadoInventario.DANADO : destino;
                if (lote != null) {
                    loteService.ajustar(lote, dest == EstadoInventario.DISPONIBLE ? parte.cantidad() : BigDecimal.ZERO,
                            dest == EstadoInventario.DANADO ? parte.cantidad() : BigDecimal.ZERO);
                }
                MovimientoInventario mov = kardex.mover(inv, dest, parte.cantidad(), TipoMovimiento.DEVOLUCION_CLIENTE, actor,
                        doc.getNumero(), "Devolución de " + v.getNumeroInterno() + ": " + req.motivo().trim(),
                        parte.idLote(), null);
                BigDecimal valor = Montos.proporcional(totalLinea, lv.getCantidad(), parte.cantidad());
                lineasDevolucion.save(Devolucion.builder().tipo(TipoDevolucion.CLIENTE)
                        .idDocumentoOrigen(v.getNumeroInterno()).idProducto(p.getId()).cantidad(parte.cantidad())
                        .motivo(req.motivo().trim()).idMovimiento(mov.getId()).idDocumentoDevolucion(doc.getId())
                        .idLineaVenta(lv.getId()).idLote(parte.idLote()).estadoProducto(l.estadoProducto())
                        .valor(valor).build());
                total = total.add(valor);
            }
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("producto", p.getNombre());
            x.put("cantidad", l.cantidad());
            x.put("estadoProducto", l.estadoProducto().getValor());
            x.put("destino", destino.getValor());
            detalle.add(x);
        }
        doc.setTotal(total);
        doc = documentos.save(doc);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("venta", v.getNumeroInterno());
        d.put("tratamiento", req.tratamiento().getValor());
        d.put("total", total);
        d.put("lineas", detalle);
        eventos.registrar(actor, Auditables.VENTAS, Auditables.DEVOLUCION, "DEVOLUCION", doc.getNumero(),
                "Devolución " + doc.getNumero() + " de la venta " + v.getNumeroInterno(), d);
        eventos.registrar(actor, Auditables.VENTAS, Auditables.DEVOLUCION, "VENTA", v.getId(),
                "Devolución " + doc.getNumero() + " registrada sobre esta venta", Map.of("devolucion", doc.getNumero()));
        return aDto(doc);
    }

    /**
     * Registra una devolución a proveedor (desde disponible o desde no apto, RN-04).
     *
     * @param req datos validados
     * @param actor usuario responsable
     * @return devolución registrada
     */
    @Transactional(rollbackFor = Exception.class)
    public DevolucionResponse registrarProveedor(DevolucionProveedorRequest req, Actor actor) {
        Optional<DocumentoDevolucion> previa = previa(req.idOperacion());
        if (previa.isPresent()) {
            return aDto(previa.get());
        }
        Compra c = compras.findForUpdate(req.compraId()).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() != EstadoDocumento.ACTIVO) {
            throw ApiException.conflicto("COMPRA_ANULADA", "error.compra.anulada", c.getNumero());
        }
        DocumentoDevolucion doc = nuevoDocumento(TipoDevolucion.PROVEEDOR, null, c.getId(), req.motivo(), req.tratamiento(),
                req.observaciones(), req.idOperacion(), actor);
        Map<Long, LineaCompra> porId = new HashMap<>();
        for (DevolucionProveedorRequest.Linea l : req.lineas()) {
            LineaCompra lc = porId.computeIfAbsent(l.idLineaCompra(),
                    k -> lineasCompra.findForUpdate(k).orElseThrow(() -> ApiException.noEncontrado("Línea de compra")));
            if (!lc.getCompra().getId().equals(c.getId())) {
                throw ApiException.regla("LINEA_AJENA", "error.linea.ajena", String.valueOf(l.idLineaCompra()), c.getNumero());
            }
            if (l.origen() == EstadoInventario.RESERVADO) {
                throw ApiException.regla("ORIGEN_INVALIDO", "error.devolucion.origen");
            }
        }
        List<DevolucionProveedorRequest.Linea> ordenadas = new ArrayList<>(req.lineas());
        ordenadas.sort(Comparator.comparing(l -> porId.get(l.idLineaCompra()).getIdProducto()));
        Map<Long, BigDecimal> enEsta = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (DevolucionProveedorRequest.Linea l : ordenadas) {
            LineaCompra lc = porId.get(l.idLineaCompra());
            Producto p = productos.findById(lc.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            BigDecimal previo = lineasDevolucion.devueltoDeLineaCompra(lc.getId(), EstadoDocumento.ACTIVO)
                    .add(enEsta.getOrDefault(lc.getId(), BigDecimal.ZERO));
            if (previo.add(l.cantidad()).compareTo(lc.getCantidadRecibida()) > 0) {
                throw ApiException.conflicto("DEVOLUCION_EXCEDE", "error.devolucion.excede", p.getNombre(),
                        KardexService.texto(lc.getCantidadRecibida()), KardexService.texto(previo), KardexService.texto(l.cantidad()));
            }
            enEsta.merge(lc.getId(), l.cantidad(), BigDecimal::add);
            Inventario inv = kardex.bloquear(p.getId());
            Long idLote = null;
            if (l.idLote() != null) {
                Lote lote = loteService.delProducto(l.idLote(), p.getId());
                loteService.ajustar(lote, l.origen() == EstadoInventario.DISPONIBLE ? l.cantidad().negate() : BigDecimal.ZERO,
                        l.origen() == EstadoInventario.DANADO ? l.cantidad().negate() : BigDecimal.ZERO);
                idLote = lote.getId();
            } else if (LoteService.porLote(p)) {
                BigDecimal sinLote = l.origen() == EstadoInventario.DISPONIBLE ? loteService.sinLote(p, inv)
                        : inv.getDanado().subtract(lotes.sumaDanado(p.getId())).max(BigDecimal.ZERO);
                if (l.cantidad().compareTo(sinLote) > 0) {
                    throw ApiException.regla("LOTE_OBLIGATORIO", "error.lote.obligatorio", p.getNombre());
                }
            }
            MovimientoInventario mov = kardex.mover(inv, l.origen(), l.cantidad().negate(), TipoMovimiento.DEVOLUCION_PROVEEDOR,
                    actor, doc.getNumero(), "Devolución al proveedor de " + c.getNumero() + ": " + req.motivo().trim(),
                    idLote, null);
            BigDecimal valor = Montos.linea(l.cantidad(), lc.getCostoPactado(), lc.getPorcentajeDescuento(),
                    lc.getPorcentajeIva()).total();
            lineasDevolucion.save(Devolucion.builder().tipo(TipoDevolucion.PROVEEDOR).idDocumentoOrigen(c.getNumero())
                    .idProducto(p.getId()).cantidad(l.cantidad()).motivo(req.motivo().trim()).idMovimiento(mov.getId())
                    .idDocumentoDevolucion(doc.getId()).idLineaCompra(lc.getId()).idLote(idLote).origenEstado(l.origen())
                    .valor(valor).build());
            total = total.add(valor);
        }
        doc.setTotal(total);
        doc = documentos.save(doc);
        eventos.registrar(actor, Auditables.COMPRAS, Auditables.DEVOLUCION, "DEVOLUCION", doc.getNumero(),
                "Devolución a proveedor " + doc.getNumero() + " de la compra " + c.getNumero(),
                Map.of("compra", c.getNumero(), "total", total, "tratamiento", req.tratamiento().getValor()));
        return aDto(doc);
    }

    /**
     * Detalle de una devolución.
     *
     * @param id identificador
     * @return devolución
     */
    @Transactional(readOnly = true)
    public DevolucionResponse obtener(Long id) {
        return aDto(documentos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Devolución")));
    }

    /**
     * Devoluciones de una venta.
     *
     * @param idVenta venta
     * @return devoluciones
     */
    @Transactional(readOnly = true)
    public List<DevolucionResponse> deVenta(Long idVenta) {
        return documentos.findByIdVentaOrderByIdAsc(idVenta).stream().map(this::aDto).toList();
    }

    /**
     * Cantidad devuelta por línea de una venta (documentos activos).
     *
     * @param v venta
     * @return mapa línea → cantidad devuelta
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> devueltoPorLinea(Venta v) {
        Map<Long, BigDecimal> r = new HashMap<>();
        for (LineaVenta l : v.getLineas()) {
            r.put(l.getId(), lineasDevolucion.devueltoDeLineaVenta(l.getId(), EstadoDocumento.ACTIVO));
        }
        return r;
    }

    /**
     * Listado con filtros.
     *
     * @param f filtros
     * @param pagina página
     * @param tamano tamaño
     * @return página de devoluciones
     */
    @Transactional(readOnly = true)
    public PaginaResponse<DevolucionResponse> listar(Filtros f, int pagina, int tamano) {
        var page = documentos.findAll(especificacion(f, parametros.getZonaHoraria()), PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, this::aDto);
    }

    /** Reparte la cantidad entre los lotes de los que salió la venta (hasta lo no devuelto de cada lote). */
    private List<Parte> partesCliente(Producto p, LineaVenta lv, BigDecimal cantidad, Map<String, BigDecimal> enEstaPorLote) {
        if (!LoteService.porLote(p)) {
            return List.of(new Parte(null, cantidad));
        }
        List<Parte> r = new ArrayList<>();
        BigDecimal pendiente = cantidad;
        for (LineaVentaLote a : lineaVentaLotes.findById_IdLineaVenta(lv.getId())) {
            if (pendiente.signum() <= 0) {
                break;
            }
            String clave = lv.getId() + ":" + a.getId().getIdLote();
            BigDecimal ya = lineasDevolucion.devueltoDeLineaVentaYLote(lv.getId(), a.getId().getIdLote(), EstadoDocumento.ACTIVO)
                    .add(enEstaPorLote.getOrDefault(clave, BigDecimal.ZERO));
            BigDecimal libre = a.getCantidad().subtract(ya);
            if (libre.signum() > 0) {
                BigDecimal q = libre.min(pendiente);
                r.add(new Parte(a.getId().getIdLote(), q));
                enEstaPorLote.merge(clave, q, BigDecimal::add);
                pendiente = pendiente.subtract(q);
            }
        }
        if (pendiente.signum() > 0) {
            r.add(new Parte(null, pendiente));   // vendido de existencia sin lote
        }
        return r;
    }

    private Optional<DocumentoDevolucion> previa(String idOperacion) {
        return idOperacion == null ? Optional.empty() : documentos.findByIdOperacion(idOperacion);
    }

    private DocumentoDevolucion nuevoDocumento(TipoDevolucion tipo, Long idVenta, Long idCompra, String motivo,
                                               com.ferreteria.inventario.domain.enums.TratamientoDevolucion tratamiento,
                                               String observaciones, String idOperacion, Actor actor) {
        LocalDateTime ahora = kardex.ahora();
        return documentos.save(DocumentoDevolucion.builder()
                .numero(String.format("DEV-%06d", documentos.siguienteNumero())).tipo(tipo).idVenta(idVenta)
                .idCompra(idCompra).fecha(ahora).idUsuario(actor.idUsuario()).motivo(motivo.trim())
                .tratamiento(tratamiento).total(BigDecimal.ZERO).estado(EstadoDocumento.ACTIVO)
                .observaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim())
                .idOperacion(idOperacion).build());
    }

    /**
     * Convierte a DTO.
     *
     * @param d documento
     * @return DTO con líneas
     */
    public DevolucionResponse aDto(DocumentoDevolucion d) {
        String ventaNumero = null;
        String compraNumero = null;
        String tercero = null;
        if (d.getIdVenta() != null) {
            Venta v = ventas.findById(d.getIdVenta()).orElse(null);
            if (v != null) {
                ventaNumero = v.getNumeroInterno();
                tercero = terceros.findById(v.getIdCliente()).map(TerceroResponse::nombreDe).orElse(null);
            }
        }
        if (d.getIdCompra() != null) {
            Compra c = compras.findById(d.getIdCompra()).orElse(null);
            if (c != null) {
                compraNumero = c.getNumero();
                tercero = terceros.findById(c.getIdProveedor()).map(TerceroResponse::nombreDe).orElse(null);
            }
        }
        Map<Long, String> nombres = new HashMap<>();
        List<DevolucionResponse.Linea> ls = lineasDevolucion.findByIdDocumentoDevolucionOrderByIdAsc(d.getId()).stream()
                .map(x -> new DevolucionResponse.Linea(x.getId(), x.getIdProducto(),
                        nombres.computeIfAbsent(x.getIdProducto(), k -> productos.findById(k)
                                .map(p -> p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion()).orElse(null)),
                        x.getCantidad(), x.getEstadoProducto(), x.getOrigenEstado(),
                        x.getIdLote() == null ? null : lotes.findById(x.getIdLote()).map(Lote::getCodigo).orElse(null),
                        x.getValor(), x.getIdLineaVenta(), x.getIdLineaCompra()))
                .toList();
        String usuario = usuarios.findById(d.getIdUsuario()).map(Usuario::getCorreo).orElse(null);
        return new DevolucionResponse(d.getId(), d.getNumero(), d.getTipo(), d.getIdVenta(), ventaNumero, d.getIdCompra(),
                compraNumero, tercero, d.getFecha(), usuario, d.getMotivo(), d.getTratamiento(), d.getTotal(), d.getEstado(),
                d.getObservaciones(), ls);
    }

    static Specification<DocumentoDevolucion> especificacion(Filtros f, java.time.ZoneId zona) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.tipo() != null) {
                ps.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(cb.lower(root.<String>get("numero")),
                        "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%"));
            }
            if (f.ventaId() != null) {
                ps.add(cb.equal(root.get("idVenta"), f.ventaId()));
            }
            if (f.compraId() != null) {
                ps.add(cb.equal(root.get("idCompra"), f.compraId()));
            }
            if (f.desde() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("fecha"), Fechas.inicioUtc(f.desde(), zona)));
            }
            if (f.hasta() != null) {
                ps.add(cb.lessThan(root.<LocalDateTime>get("fecha"), Fechas.finExclusivoUtc(f.hasta(), zona)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
