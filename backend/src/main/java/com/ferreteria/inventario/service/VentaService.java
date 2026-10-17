/*
 * Nombre: VentaService.java
 * Descripción: Registro atómico de ventas (RF-04): precio vigente aprobado, conversión de unidades, descuento autorizado, IVA, lotes FEFO, reservas, garantía, moneda del documento con tasa de cambio (RF-23), factura e idempotencia.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.2.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoReserva;
import com.ferreteria.inventario.domain.enums.ModalidadFactura;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el registro de ventas. Las filas de inventario se bloquean ({@code FOR UPDATE}) en orden
 * de producto para evitar interbloqueos; si cualquier línea falla se revierte toda la transacción, de modo
 * que nunca queda una factura sin su salida de inventario consistente.
 * <p>
 * Los importes los calcula siempre el servidor: precio = último precio aprobado vigente (RN-07), descuento por
 * línea limitado por {@code app.ventas.descuento-maximo}, IVA = porcentaje del producto (ver {@link Montos}).
 * <p>
 * Moneda (RF-23, RN-17): si la venta es en otra moneda, el precio en COP se convierte con la tasa vigente
 * ({@link MonedaService#aMonedaDocumento}); los importes se calculan en la moneda del documento y su equivalente
 * en COP ({@link MonedaService#aFuncional}) se guarda en las columnas de siempre, que usan inventario y cierres.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventas;
    private final TerceroRepository terceros;
    private final ProductoRepository productos;
    private final PrecioRepository precios;
    private final FacturaRepository facturas;
    private final UsuarioRepository usuarios;
    private final ReservaRepository reservas;
    private final LineaVentaLoteRepository lineaVentaLotes;
    private final FacturacionPort facturacion;
    private final UnidadService unidades;
    private final KardexService kardex;
    private final LoteService lotes;
    private final EventoAuditoriaService eventos;
    private final ParametrosNegocio parametros;
    private final MonedaService monedas;

    /**
     * Registra una venta y emite su factura.
     *
     * @param req solicitud validada
     * @param correoUsuario correo del usuario autenticado
     * @param ip IP de origen de la petición
     * @return venta con factura (o la ya registrada con la misma clave de operación)
     * @throws ApiException 409 si el saldo es insuficiente, 422 si falta precio vigente o conversión, 404/422 por cliente
     */
    @Transactional(rollbackFor = Exception.class)
    public VentaResponse registrar(VentaRequest req, String correoUsuario, String ip) {
        if (!req.isSinImportesManuales()) {
            throw ApiException.regla("IMPORTES_CALCULADOS", "error.importes.calculados");
        }
        if (req.idOperacion() != null) {
            Optional<Venta> previa = ventas.findByIdOperacion(req.idOperacion());
            if (previa.isPresent()) {
                return respuesta(previa.get());   // reintento: no se crea una segunda venta (RF-11)
            }
        }
        Tercero cliente = terceros.findById(req.clienteId()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        if (!cliente.isCliente()) {
            throw ApiException.regla("CLIENTE_INVALIDO", "error.cliente.invalido", cliente.getNumeroIdentificacion());
        }
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        Actor actor = new Actor(usuario.getId(), usuario.getCorreo(), ip);
        LocalDateTime ahora = kardex.ahora();
        LocalDate hoy = kardex.hoy();
        MonedaService.Cambio cambio = monedas.cambioParaDocumento(req.moneda());

        Venta venta = ventas.save(Venta.builder()
                .numeroInterno(String.format("VTA-%06d", ventas.siguienteNumeroVenta()))
                .idCliente(cliente.getId())
                .fecha(ahora)
                .estado(EstadoDocumento.ACTIVO)
                .idUsuario(usuario.getId())
                .idOperacion(req.idOperacion())
                .moneda(cambio.moneda()).tasaCambio(cambio.tasa())
                .observaciones(req.observaciones() == null || req.observaciones().isBlank() ? null : req.observaciones().trim())
                .build());
        String doc = venta.getNumeroInterno();

        List<VentaRequest.Linea> ordenadas = req.lineas().stream()
                .sorted(Comparator.comparing(VentaRequest.Linea::productoId)).toList();
        Map<LineaVenta, List<LoteService.Asignacion>> asignaciones = new LinkedHashMap<>();
        List<Montos.Linea> importes = new ArrayList<>();
        List<Montos.Linea> importesMoneda = new ArrayList<>();

        for (VentaRequest.Linea l : ordenadas) {
            Producto p = productos.findById(l.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            if (!p.isActivo()) {
                throw ApiException.regla("PRODUCTO_INACTIVO", "error.producto.inactivo", p.getNombre());
            }
            UnidadService.Conversion conv = unidades.aUnidadBase(p, l.cantidad(), l.unidad());
            BigDecimal cantidadBase = conv.cantidadBase();
            BigDecimal descuento = l.porcentajeDescuento() == null ? BigDecimal.ZERO : l.porcentajeDescuento();
            if (descuento.compareTo(parametros.getDescuentoMaximoVenta()) > 0) {
                throw ApiException.regla("DESCUENTO_NO_PERMITIDO", "error.descuento.maximo",
                        KardexService.texto(parametros.getDescuentoMaximoVenta()));
            }
            Precio precio = vigente(p.getId())
                    .orElseThrow(() -> ApiException.regla("PRECIO_NO_VIGENTE", "error.precio.no.vigente", p.getNombre()));

            Inventario inv = kardex.bloquear(p.getId());
            Reserva reserva = l.reservaId() == null ? null : reservaValida(l.reservaId(), p, cliente, cantidadBase);
            if (reserva == null && inv.getDisponible().compareTo(cantidadBase) < 0) {
                throw kardex.insuficiente(p.getId(), EstadoInventario.DISPONIBLE, inv.getDisponible(), cantidadBase);
            }
            List<LoteService.Asignacion> asign = lotes.asignarSalida(p, inv, cantidadBase, hoy);
            EstadoInventario origen = reserva == null ? EstadoInventario.DISPONIBLE : EstadoInventario.RESERVADO;
            registrarSalida(inv, origen, cantidadBase, asign, actor, doc);
            if (reserva != null) {
                consumirReserva(reserva, inv, cantidadBase, venta, actor, doc);
            }

            BigDecimal precioMoneda = MonedaService.aMonedaDocumento(precio.getPrecioVigente(), cambio.tasa());
            Montos.Linea mx = Montos.linea(cantidadBase, precioMoneda, descuento, p.getPorcentajeIva());
            Montos.Linea m = enFuncional(mx, cambio.tasa());
            importes.add(m);
            importesMoneda.add(mx);
            LineaVenta lv = LineaVenta.builder().venta(venta).idProducto(p.getId())
                    .cantidad(cantidadBase).precioAplicado(precio.getPrecioVigente())
                    .unidad(conv.unidad()).cantidadPresentacion(conv.unidad() == null ? null : l.cantidad())
                    .porcentajeDescuento(descuento).porcentajeIva(p.getPorcentajeIva())
                    .subtotal(m.subtotal()).descuento(m.descuento()).impuesto(m.impuesto()).total(m.total())
                    .precioMoneda(precioMoneda).subtotalMoneda(mx.subtotal()).descuentoMoneda(mx.descuento())
                    .impuestoMoneda(mx.impuesto()).totalMoneda(mx.total())
                    .garantiaHasta(p.isTieneGarantia() && p.getMesesGarantia() != null ? hoy.plusMonths(p.getMesesGarantia()) : null)
                    .build();
            venta.getLineas().add(lv);
            asignaciones.put(lv, asign);
        }
        Montos.Totales t = Montos.totales(importes);
        venta.setSubtotal(t.subtotal());
        venta.setTotalDescuentos(t.descuentos());
        venta.setTotalImpuestos(t.impuestos());
        venta.setTotal(t.total());
        Montos.Totales tx = Montos.totales(importesMoneda);
        venta.setSubtotalMoneda(tx.subtotal());
        venta.setDescuentosMoneda(tx.descuentos());
        venta.setImpuestosMoneda(tx.impuestos());
        venta.setTotalMoneda(tx.total());
        venta = ventas.save(venta);
        for (Map.Entry<LineaVenta, List<LoteService.Asignacion>> e : asignaciones.entrySet()) {
            for (LoteService.Asignacion a : e.getValue()) {
                lineaVentaLotes.save(LineaVentaLote.builder()
                        .id(new LineaVentaLoteId(e.getKey().getId(), a.lote().getId())).cantidad(a.cantidad()).build());
            }
        }

        Factura factura = facturas.save(Factura.builder()
                .idVenta(venta.getId())
                .numeracionDian(facturacion.generarNumeracionFiscal(venta))
                .modalidad(ModalidadFactura.ELECTRONICA)
                .impuestos(t.impuestos())
                .descuentos(t.descuentos())
                .fechaEmision(ahora).build());

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("cliente", TerceroResponse.nombreDe(cliente));
        d.put("lineas", venta.getLineas().size());
        d.put("subtotal", t.subtotal());
        d.put("descuentos", t.descuentos());
        d.put("impuestos", t.impuestos());
        d.put("total", t.total());
        d.put("moneda", cambio.moneda());
        if (!cambio.esFuncional()) {
            d.put("tasaCambio", cambio.tasa());
            d.put("fechaTasa", String.valueOf(cambio.fechaTasa()));
            d.put("totalMoneda", tx.total());
        }
        d.put("factura", factura.getNumeracionDian());
        eventos.registrar(actor, Auditables.VENTAS, Auditables.VENTA, "VENTA", venta.getId(),
                "Venta " + doc + " a " + TerceroResponse.nombreDe(cliente), d);
        return respuesta(venta, factura, cliente, usuario.getCorreo());
    }

    /**
     * Consulta una venta con su factura.
     *
     * @param id identificador de la venta
     * @return venta
     */
    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id) {
        return respuesta(ventas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Venta")));
    }

    /**
     * Lista paginada de ventas, más recientes primero.
     *
     * @param pagina página (base 0)
     * @param tamano tamaño, máximo 100
     * @return página de ventas
     */
    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listar(int pagina, int tamano) {
        var page = ventas.findAll(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, this::respuesta);
    }

    /**
     * Convierte la cantidad de la línea a la unidad base del producto (RF-02).
     *
     * @param p producto
     * @param l línea solicitada
     * @return cantidad en unidad base
     */
    BigDecimal aUnidadBase(Producto p, VentaRequest.Linea l) {
        return unidades.aUnidadBase(p, l.cantidad(), l.unidad()).cantidadBase();
    }

    /** Equivalente en COP de los importes de una línea calculados en la moneda del documento. */
    static Montos.Linea enFuncional(Montos.Linea x, BigDecimal tasa) {
        if (tasa.compareTo(BigDecimal.ONE) == 0) {
            return x;
        }
        BigDecimal subtotal = MonedaService.aFuncional(x.subtotal(), tasa);
        BigDecimal descuento = MonedaService.aFuncional(x.descuento(), tasa);
        BigDecimal impuesto = MonedaService.aFuncional(x.impuesto(), tasa);
        return new Montos.Linea(subtotal, descuento, impuesto, subtotal.subtract(descuento).add(impuesto));
    }

    private Optional<Precio> vigente(Long idProducto) {
        List<Precio> v = precios.vigentes(idProducto, PageRequest.of(0, 1));
        return v.isEmpty() ? Optional.empty() : Optional.of(v.get(0));
    }

    /** Un movimiento por lote asignado más uno por la existencia sin lote, para trazar cada lote. */
    private void registrarSalida(Inventario inv, EstadoInventario origen, BigDecimal cantidad,
                                 List<LoteService.Asignacion> asign, Actor actor, String doc) {
        BigDecimal restante = cantidad;
        for (LoteService.Asignacion a : asign) {
            kardex.mover(inv, origen, a.cantidad().negate(), TipoMovimiento.VENTA, actor, doc, "Venta " + doc,
                    a.lote().getId(), null);
            restante = restante.subtract(a.cantidad());
        }
        if (restante.signum() > 0) {
            kardex.mover(inv, origen, restante.negate(), TipoMovimiento.VENTA, actor, doc, "Venta " + doc, null, null);
        }
    }

    private Reserva reservaValida(Long idReserva, Producto p, Tercero cliente, BigDecimal cantidad) {
        Reserva r = reservas.findForUpdate(idReserva).orElseThrow(() -> ApiException.noEncontrado("Reserva"));
        if (r.getEstado() != EstadoReserva.ACTIVA || !r.getIdProducto().equals(p.getId())
                || !r.getIdCliente().equals(cliente.getId()) || cantidad.compareTo(r.getCantidad()) > 0
                || r.getFechaLimite().isBefore(kardex.hoy())) {
            throw ApiException.regla("RESERVA_INVALIDA", "error.reserva.invalida", String.valueOf(idReserva));
        }
        return r;
    }

    /** La reserva se libera por la venta asociada (RN-03); el sobrante vuelve a disponible. */
    private void consumirReserva(Reserva r, Inventario inv, BigDecimal vendido, Venta venta, Actor actor, String doc) {
        BigDecimal sobrante = r.getCantidad().subtract(vendido);
        if (sobrante.signum() > 0) {
            kardex.mover(inv, EstadoInventario.RESERVADO, sobrante.negate(), TipoMovimiento.RESERVA, actor, doc,
                    "Liberación del sobrante de la reserva " + r.getId(), null, null);
            kardex.mover(inv, EstadoInventario.DISPONIBLE, sobrante, TipoMovimiento.RESERVA, actor, doc,
                    "Liberación del sobrante de la reserva " + r.getId(), null, null);
        }
        r.setEstado(EstadoReserva.LIBERADA_VENTA);
        r.setIdVenta(venta.getId());
        r.setFechaLiberacion(kardex.ahora());
        r.setMotivoLiberacion("Venta " + doc);
        reservas.save(r);
    }

    private VentaResponse respuesta(Venta v) {
        Factura f = facturas.findByIdVenta(v.getId()).orElse(null);
        Tercero c = terceros.findById(v.getIdCliente()).orElse(null);
        String correo = v.getIdUsuario() == null ? null : usuarios.findById(v.getIdUsuario()).map(Usuario::getCorreo).orElse(null);
        return respuesta(v, f, c, correo);
    }

    private VentaResponse respuesta(Venta v, Factura f, Tercero c, String correo) {
        Map<Long, VentaResponse.InfoProducto> info = new HashMap<>();
        for (LineaVenta l : v.getLineas()) {
            info.computeIfAbsent(l.getIdProducto(), k -> productos.findById(k)
                    .map(p -> new VentaResponse.InfoProducto(p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(),
                            p.getCodigoBarras(), p.getUnidadBase())).orElse(null));
        }
        return VentaResponse.de(v, f, c == null ? null : TerceroResponse.nombreDe(c), correo, info, Map.of(),
                facturacion.esValidaFiscalmente());
    }
}
