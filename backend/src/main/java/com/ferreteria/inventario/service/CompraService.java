/*
 * Nombre: CompraService.java
 * Descripción: Compras y recepción de mercancía (RF-03, RF-17): registro del pedido en la moneda del proveedor con tasa pactada (RF-23), recepciones parciales con documento de ingreso, lotes y vencimientos, costo promedio ponderado en COP y anulaciones.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.Compra;
import com.ferreteria.inventario.domain.model.DocumentoIngreso;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.LineaCompra;
import com.ferreteria.inventario.domain.model.Lote;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Recepcion;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.CompraRequest;
import com.ferreteria.inventario.dto.CompraResponse;
import com.ferreteria.inventario.dto.CompraResumenResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.RecepcionRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.CompraRepository;
import com.ferreteria.inventario.repository.DocumentoIngresoRepository;
import com.ferreteria.inventario.repository.LineaCompraRepository;
import com.ferreteria.inventario.repository.LoteRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.RecepcionRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Ciclo de compras. Una compra representa lo pedido y no afecta el inventario; cada recepción genera un documento
 * de ingreso con consecutivo propio y aumenta el inventario solo por lo efectivamente recibido (RF-17). Las
 * líneas se bloquean para impedir recibir más de lo pedido aun con recepciones simultáneas, y la clave de
 * idempotencia impide contabilizar dos veces la misma recepción.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compras;
    private final LineaCompraRepository lineas;
    private final DocumentoIngresoRepository documentos;
    private final RecepcionRepository recepciones;
    private final TerceroRepository terceros;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final LoteRepository lotes;
    private final UnidadService unidades;
    private final KardexService kardex;
    private final LoteService loteService;
    private final PrecioService precioService;
    private final EventoAuditoriaService eventos;
    private final com.ferreteria.inventario.config.ParametrosNegocio parametros;
    private final MonedaService monedas;

    /**
     * Filtros del listado.
     *
     * @param q texto contenido en el número de la compra
     * @param proveedorId proveedor
     * @param estado activo o anulado
     * @param desde fecha inicial
     * @param hasta fecha final
     */
    public record Filtros(String q, Long proveedorId, EstadoDocumento estado, LocalDate desde, LocalDate hasta) { }

    /**
     * Registra una compra (lo pedido). No modifica el inventario.
     *
     * @param req datos validados
     * @param actor usuario de compras
     * @return compra creada (o la existente si la clave de operación ya se usó)
     */
    @Transactional
    public CompraResponse crear(CompraRequest req, Actor actor) {
        if (req.idOperacion() != null) {
            Optional<Compra> previa = compras.findByIdOperacion(req.idOperacion());
            if (previa.isPresent()) {
                return detalle(previa.get());
            }
        }
        Tercero prov = terceros.findById(req.proveedorId()).orElseThrow(() -> ApiException.noEncontrado("Proveedor"));
        if (!prov.isProveedor()) {
            throw ApiException.regla("PROVEEDOR_INVALIDO", "error.proveedor.invalido", prov.getNumeroIdentificacion());
        }
        MonedaService.Cambio cambio = monedas.cambioParaDocumento(req.moneda());
        Compra c = Compra.builder().numero(String.format("OC-%06d", compras.siguienteNumero()))
                .moneda(cambio.moneda()).tasaCambio(cambio.tasa())
                .idProveedor(prov.getId()).fecha(kardex.ahora()).estado(EstadoDocumento.ACTIVO)
                .idUsuario(actor.idUsuario()).observaciones(limpiar(req.observaciones()))
                .idOperacion(req.idOperacion()).build();
        for (CompraRequest.Linea l : req.lineas()) {
            Producto p = productos.findById(l.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            if (!p.isActivo()) {
                throw ApiException.regla("PRODUCTO_INACTIVO", "error.producto.inactivo", p.getNombre());
            }
            UnidadService.Conversion conv = unidades.aUnidadBase(p, l.cantidad(), l.unidad());
            // El costo llega en la moneda de la compra; el costo pactado (base del costo promedio) se guarda en COP.
            BigDecimal costoMoneda = UnidadService.porUnidadBase(l.costoUnitario(), conv.factor());
            c.getLineas().add(LineaCompra.builder().compra(c).idProducto(p.getId())
                    .cantidadPedida(conv.cantidadBase()).cantidadRecibida(BigDecimal.ZERO)
                    .costoMoneda(costoMoneda)
                    .costoPactado(cambio.esFuncional() ? costoMoneda : MonedaService.costoFuncional(costoMoneda, cambio.tasa()))
                    .unidad(conv.unidad()).cantidadPresentacion(conv.unidad() == null ? null : l.cantidad())
                    .costoPresentacion(conv.unidad() == null ? null : l.costoUnitario())
                    .porcentajeIva(nz(l.porcentajeIva())).porcentajeDescuento(nz(l.porcentajeDescuento())).build());
        }
        c = compras.save(c);
        CompraResponse r = detalle(c);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("proveedor", r.proveedor());
        d.put("lineas", r.lineas().size());
        d.put("total", r.total());
        d.put("moneda", r.moneda());
        if (!cambio.esFuncional()) {
            d.put("tasaCambio", cambio.tasa());
            d.put("fechaTasa", String.valueOf(cambio.fechaTasa()));
            d.put("totalCop", r.totalFuncional());
        }
        eventos.registrar(actor, Auditables.COMPRAS, Auditables.CREAR, "COMPRA", c.getNumero(),
                "Compra registrada " + c.getNumero() + " a " + r.proveedor(), d);
        return r;
    }

    /**
     * Registra una recepción (parcial o total) y su documento de ingreso.
     *
     * @param idCompra compra
     * @param req líneas recibidas, factura del proveedor, lotes y vencimientos
     * @param actor usuario que recibe
     * @return compra actualizada
     */
    @Transactional(rollbackFor = Exception.class)
    public CompraResponse recibir(Long idCompra, RecepcionRequest req, Actor actor) {
        if (req.idOperacion() != null) {
            Optional<DocumentoIngreso> previo = documentos.findByIdOperacion(req.idOperacion());
            if (previo.isPresent()) {
                // Reintento de la misma recepción: no se vuelve a sumar inventario (RF-17, RNF-02).
                return obtener(previo.get().getIdCompra());
            }
        }
        Compra c = compras.findForUpdate(idCompra).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() != EstadoDocumento.ACTIVO) {
            throw ApiException.conflicto("COMPRA_ANULADA", "error.compra.anulada", c.getNumero());
        }
        LocalDateTime ahora = kardex.ahora();
        LocalDate hoy = kardex.hoy();
        DocumentoIngreso doc = documentos.save(DocumentoIngreso.builder()
                .consecutivo(String.format("ING-%06d", documentos.siguienteNumero())).estado(EstadoDocumento.ACTIVO)
                .facturaProveedor(req.facturaProveedor().trim()).fecha(ahora).idCompra(c.getId())
                .idUsuario(actor.idUsuario()).observaciones(limpiar(req.observaciones()))
                .idOperacion(req.idOperacion()).build());
        List<Map<String, Object>> detalle = new ArrayList<>();
        // Orden por producto para bloquear inventarios siempre en el mismo orden (evita interbloqueos).
        List<RecepcionRequest.Linea> ordenadas = new ArrayList<>(req.lineas());
        Map<Long, LineaCompra> porId = new HashMap<>();
        for (RecepcionRequest.Linea l : ordenadas) {
            LineaCompra lc = porId.computeIfAbsent(l.idLineaCompra(),
                    k -> lineas.findForUpdate(k).orElseThrow(() -> ApiException.noEncontrado("Línea de compra")));
            if (!lc.getCompra().getId().equals(c.getId())) {
                throw ApiException.regla("LINEA_AJENA", "error.linea.ajena", String.valueOf(l.idLineaCompra()), c.getNumero());
            }
        }
        ordenadas.sort((a, b) -> porId.get(a.idLineaCompra()).getIdProducto()
                .compareTo(porId.get(b.idLineaCompra()).getIdProducto()));
        for (RecepcionRequest.Linea l : ordenadas) {
            LineaCompra lc = porId.get(l.idLineaCompra());
            Producto p = productos.findById(lc.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            BigDecimal pendiente = lc.getCantidadPedida().subtract(lc.getCantidadRecibida());
            if (l.cantidad().compareTo(pendiente) > 0) {
                throw ApiException.conflicto("RECEPCION_EXCEDE", "error.recepcion.excede", p.getNombre(),
                        KardexService.texto(pendiente), KardexService.texto(l.cantidad()));
            }
            Inventario inv = kardex.bloquear(p.getId());
            BigDecimal existencias = inv.getDisponible().add(inv.getReservado()).add(inv.getDanado());
            Lote lote = null;
            if (LoteService.porLote(p)) {
                if (l.codigoLote() == null || l.codigoLote().isBlank()) {
                    throw ApiException.regla("LOTE_OBLIGATORIO", "error.lote.obligatorio", p.getNombre());
                }
                if (p.isTieneVencimiento() && l.fechaVencimiento() == null) {
                    throw ApiException.regla("VENCIMIENTO_OBLIGATORIO", "error.vencimiento.obligatorio", p.getNombre());
                }
                if (l.fechaVencimiento() != null && l.fechaVencimiento().isBefore(hoy)) {
                    throw ApiException.regla("LOTE_VENCIDO", "error.lote.vencido", l.codigoLote(),
                            String.valueOf(l.fechaVencimiento()));
                }
                lote = loteService.ingresar(p, l.codigoLote(), p.isTieneVencimiento() ? l.fechaVencimiento() : null,
                        l.cantidad(), ahora);
            }
            MovimientoInventario mov = kardex.mover(inv, EstadoInventario.DISPONIBLE, l.cantidad(), TipoMovimiento.COMPRA,
                    actor, doc.getConsecutivo(), "Recepción de " + c.getNumero() + " (factura " + doc.getFacturaProveedor() + ")",
                    lote == null ? null : lote.getId(), null);
            BigDecimal costoNeto = lc.getCostoPactado().multiply(BigDecimal.ONE.subtract(
                    lc.getPorcentajeDescuento().divide(new BigDecimal("100"))));
            p.setCostoPromedio(CalculadoraPrecio.promedioPonderado(existencias, p.getCostoPromedio(), l.cantidad(), costoNeto));
            productos.save(p);
            lc.setCantidadRecibida(lc.getCantidadRecibida().add(l.cantidad()));
            lineas.save(lc);
            recepciones.save(Recepcion.builder().idLineaCompra(lc.getId()).idDocumentoIngreso(doc.getId())
                    .cantidadRecibida(l.cantidad()).fecha(ahora).idLote(lote == null ? null : lote.getId())
                    .costoUnitario(costoNeto.setScale(4, java.math.RoundingMode.HALF_UP)).idMovimiento(mov.getId()).build());
            precioService.proponerAutomatico(p, actor);
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("producto", p.getNombre());
            x.put("cantidad", l.cantidad());
            x.put("lote", lote == null ? null : lote.getCodigo());
            x.put("vencimiento", lote == null ? null : lote.getFechaVencimiento());
            x.put("costoPromedio", p.getCostoPromedio());
            detalle.add(x);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("compra", c.getNumero());
        d.put("facturaProveedor", doc.getFacturaProveedor());
        d.put("lineas", detalle);
        eventos.registrar(actor, Auditables.COMPRAS, Auditables.RECEPCION, "DOCUMENTO_INGRESO", doc.getConsecutivo(),
                "Recepción " + doc.getConsecutivo() + " de la compra " + c.getNumero(), d);
        return detalle(c);
    }

    /**
     * Anula un documento de ingreso: revierte las cantidades recibidas con movimientos de nota de ajuste
     * vinculados a los originales (RN-16) y conserva el consecutivo (RN-09). Falla si la mercancía ya salió.
     *
     * @param idDocumento documento de ingreso
     * @param motivo motivo obligatorio
     * @param actor usuario
     * @return compra actualizada
     */
    @Transactional(rollbackFor = Exception.class)
    public CompraResponse anularIngreso(Long idDocumento, String motivo, Actor actor) {
        DocumentoIngreso doc = documentos.findForUpdate(idDocumento).orElseThrow(() -> ApiException.noEncontrado("Documento de ingreso"));
        if (doc.getEstado() != EstadoDocumento.ACTIVO) {
            throw ApiException.conflicto("DOCUMENTO_ANULADO", "error.documento.anulado", doc.getConsecutivo());
        }
        if (doc.getIdCompra() != null) {
            compras.findForUpdate(doc.getIdCompra());   // mismo orden de bloqueo que las recepciones
        }
        List<Recepcion> rs = new ArrayList<>(recepciones.findByIdDocumentoIngresoOrderByIdAsc(doc.getId()));
        Map<Long, LineaCompra> porLinea = new HashMap<>();
        for (Recepcion r : rs) {
            porLinea.computeIfAbsent(r.getIdLineaCompra(), k -> lineas.findForUpdate(k).orElseThrow());
        }
        rs.sort((a, b) -> porLinea.get(a.getIdLineaCompra()).getIdProducto()
                .compareTo(porLinea.get(b.getIdLineaCompra()).getIdProducto()));
        for (Recepcion r : rs) {
            LineaCompra lc = porLinea.get(r.getIdLineaCompra());
            Inventario inv = kardex.bloquear(lc.getIdProducto());
            if (r.getIdLote() != null) {
                Lote l = loteService.delProducto(r.getIdLote(), lc.getIdProducto());
                loteService.ajustar(l, r.getCantidadRecibida().negate(), BigDecimal.ZERO);
            }
            kardex.mover(inv, EstadoInventario.DISPONIBLE, r.getCantidadRecibida().negate(), TipoMovimiento.COMPRA, actor,
                    doc.getConsecutivo(), "Anulación de " + doc.getConsecutivo() + ": " + motivo.trim(), r.getIdLote(),
                    r.getIdMovimiento());
            lc.setCantidadRecibida(lc.getCantidadRecibida().subtract(r.getCantidadRecibida()));
            lineas.save(lc);
        }
        doc.setEstado(EstadoDocumento.ANULADO);
        doc.setMotivoAnulacion(motivo.trim());
        doc.setFechaAnulacion(kardex.ahora());
        doc.setIdUsuarioAnulacion(actor.idUsuario());
        documentos.save(doc);
        eventos.registrar(actor, Auditables.COMPRAS, Auditables.ANULAR, "DOCUMENTO_INGRESO", doc.getConsecutivo(),
                "Documento de ingreso anulado " + doc.getConsecutivo(), Map.of("motivo", motivo.trim()));
        return obtener(doc.getIdCompra());
    }

    /**
     * Anula una compra que no tiene mercancía recibida.
     *
     * @param id compra
     * @param motivo motivo obligatorio
     * @param actor usuario
     * @return compra anulada
     */
    @Transactional
    public CompraResponse anular(Long id, String motivo, Actor actor) {
        Compra c = compras.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() != EstadoDocumento.ACTIVO) {
            throw ApiException.conflicto("COMPRA_ANULADA", "error.compra.anulada", c.getNumero());
        }
        boolean recibida = c.getLineas().stream().anyMatch(l -> l.getCantidadRecibida().signum() > 0);
        if (recibida) {
            throw ApiException.conflicto("COMPRA_CON_RECEPCIONES", "error.compra.con.recepciones", c.getNumero());
        }
        c.setEstado(EstadoDocumento.ANULADO);
        c.setMotivoAnulacion(motivo.trim());
        c.setFechaAnulacion(kardex.ahora());
        compras.save(c);
        eventos.registrar(actor, Auditables.COMPRAS, Auditables.ANULAR, "COMPRA", c.getNumero(),
                "Compra anulada " + c.getNumero(), Map.of("motivo", motivo.trim()));
        return detalle(c);
    }

    /**
     * Detalle de una compra.
     *
     * @param id compra
     * @return detalle con documentos de ingreso
     */
    @Transactional(readOnly = true)
    public CompraResponse obtener(Long id) {
        return detalle(compras.findById(id).orElseThrow(() -> ApiException.noEncontrado("Compra")));
    }

    /**
     * Listado paginado con filtros, más recientes primero.
     *
     * @param f filtros
     * @param pagina página
     * @param tamano tamaño
     * @return página de compras
     */
    @Transactional(readOnly = true)
    public PaginaResponse<CompraResumenResponse> listar(Filtros f, int pagina, int tamano) {
        var page = compras.findAll(especificacion(f, parametros.getZonaHoraria()), PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> nombres = new HashMap<>();
        return PaginaResponse.de(page, c -> {
            Montos.Totales t = totales(c.getLineas());
            Montos.Totales tf = totalesFuncionales(c.getLineas());
            String prov = nombres.computeIfAbsent(c.getIdProveedor(),
                    k -> terceros.findById(k).map(TerceroResponse::nombreDe).orElse(""));
            return new CompraResumenResponse(c.getId(), c.getNumero(), prov, c.getFecha(), c.getEstado(),
                    estadoRecepcion(c), c.getLineas().size(), t.total(), moneda(c), tf.total());
        });
    }

    private CompraResponse detalle(Compra c) {
        Tercero prov = terceros.findById(c.getIdProveedor()).orElse(null);
        Map<Long, Producto> ps = new HashMap<>();
        List<CompraResponse.Linea> ls = new ArrayList<>();
        for (LineaCompra l : c.getLineas()) {
            Producto p = ps.computeIfAbsent(l.getIdProducto(), k -> productos.findById(k).orElse(null));
            Montos.Linea m = Montos.linea(l.getCantidadPedida(), costoMoneda(l), l.getPorcentajeDescuento(),
                    l.getPorcentajeIva());
            ls.add(new CompraResponse.Linea(l.getId(), l.getIdProducto(), etiqueta(p), p == null ? null : p.getCodigoBarras(),
                    p == null ? null : p.getUnidadBase(), p == null ? null : p.getTipoRastreo(),
                    p != null && p.isTieneVencimiento(), l.getCantidadPedida(), l.getCantidadRecibida(),
                    l.getCantidadPedida().subtract(l.getCantidadRecibida()), l.getUnidad(), l.getCantidadPresentacion(),
                    l.getCostoPresentacion(), l.getCostoPactado(), l.getPorcentajeIva(), l.getPorcentajeDescuento(),
                    m.subtotal(), m.descuento(), m.impuesto(), m.total(), costoMoneda(l)));
        }
        Montos.Totales t = totales(c.getLineas());
        Montos.Totales tf = totalesFuncionales(c.getLineas());
        List<CompraResponse.Documento> docs = new ArrayList<>();
        Map<Long, LineaCompra> lineasPorId = new HashMap<>();
        c.getLineas().forEach(l -> lineasPorId.put(l.getId(), l));
        for (DocumentoIngreso d : documentos.findByIdCompraOrderByIdDesc(c.getId())) {
            List<CompraResponse.Recepcion> rs = recepciones.findByIdDocumentoIngresoOrderByIdAsc(d.getId()).stream().map(r -> {
                LineaCompra lc = lineasPorId.get(r.getIdLineaCompra());
                Long idProd = lc == null ? null : lc.getIdProducto();
                Producto p = idProd == null ? null : ps.computeIfAbsent(idProd, k -> productos.findById(k).orElse(null));
                Lote lote = r.getIdLote() == null ? null : lotes.findById(r.getIdLote()).orElse(null);
                return new CompraResponse.Recepcion(r.getId(), r.getIdLineaCompra(), idProd, etiqueta(p),
                        r.getCantidadRecibida(), lote == null ? null : lote.getCodigo(),
                        lote == null ? null : lote.getFechaVencimiento(), r.getCostoUnitario());
            }).toList();
            docs.add(new CompraResponse.Documento(d.getId(), d.getConsecutivo(), d.getEstado(), d.getFacturaProveedor(),
                    d.getFecha(), correo(d.getIdUsuario()), d.getObservaciones(), d.getMotivoAnulacion(), rs));
        }
        return new CompraResponse(c.getId(), c.getNumero(), c.getIdProveedor(),
                prov == null ? null : TerceroResponse.nombreDe(prov), prov == null ? null : prov.getNumeroIdentificacion(),
                c.getFecha(), c.getEstado(), estadoRecepcion(c), correo(c.getIdUsuario()), c.getObservaciones(),
                c.getMotivoAnulacion(), ls, t.subtotal(), t.descuentos(), t.impuestos(), t.total(), docs, moneda(c),
                c.getTasaCambio() == null ? BigDecimal.ONE : c.getTasaCambio(), tf.total());
    }

    /**
     * Estado de avance de la recepción.
     *
     * @param c compra
     * @return PENDIENTE, PARCIAL, RECIBIDA o ANULADA
     */
    public static String estadoRecepcion(Compra c) {
        if (c.getEstado() == EstadoDocumento.ANULADO) {
            return "ANULADA";
        }
        boolean todo = !c.getLineas().isEmpty() && c.getLineas().stream()
                .allMatch(l -> l.getCantidadRecibida().compareTo(l.getCantidadPedida()) >= 0);
        if (todo) {
            return "RECIBIDA";
        }
        return c.getLineas().stream().anyMatch(l -> l.getCantidadRecibida().signum() > 0) ? "PARCIAL" : "PENDIENTE";
    }

    /** Totales en la moneda de la compra. */
    private static Montos.Totales totales(List<LineaCompra> ls) {
        return Montos.totales(ls.stream().map(l -> Montos.linea(l.getCantidadPedida(), costoMoneda(l),
                l.getPorcentajeDescuento(), l.getPorcentajeIva())).toList());
    }

    /** Totales equivalentes en COP (costo pactado). */
    private static Montos.Totales totalesFuncionales(List<LineaCompra> ls) {
        return Montos.totales(ls.stream().map(l -> Montos.linea(l.getCantidadPedida(), l.getCostoPactado(),
                l.getPorcentajeDescuento(), l.getPorcentajeIva())).toList());
    }

    /** Costo por unidad base en la moneda de la compra (compras anteriores a V5: el costo en COP). */
    private static BigDecimal costoMoneda(LineaCompra l) {
        return l.getCostoMoneda() != null ? l.getCostoMoneda() : l.getCostoPactado();
    }

    private static String moneda(Compra c) {
        return c.getMoneda() == null ? MonedaService.FUNCIONAL_POR_DEFECTO : c.getMoneda();
    }

    private String correo(Integer idUsuario) {
        return idUsuario == null ? null : usuarios.findById(idUsuario).map(Usuario::getCorreo).orElse(null);
    }

    private static String etiqueta(Producto p) {
        return p == null ? null : p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static Specification<Compra> especificacion(Filtros f, java.time.ZoneId zona) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(cb.lower(root.<String>get("numero")),
                        "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%"));
            }
            if (f.proveedorId() != null) {
                ps.add(cb.equal(root.get("idProveedor"), f.proveedorId()));
            }
            if (f.estado() != null) {
                ps.add(cb.equal(root.get("estado"), f.estado()));
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
