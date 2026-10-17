/*
 * Nombre: VentaConsultaService.java
 * Descripción: Consulta de ventas (RF-18, RF-19): listado con filtros y detalle completo a partir de datos persistidos, con devoluciones, trazabilidad y validación de exportación del comprobante.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.model.Factura;
import com.ferreteria.inventario.domain.model.LineaVenta;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.domain.model.Venta;
import com.ferreteria.inventario.dto.*;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.DocumentoDevolucionRepository;
import com.ferreteria.inventario.repository.FacturaRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.repository.VentaRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas de ventas. El detalle y el comprobante se construyen solo con datos persistidos de la venta
 * (precio aplicado y totales guardados), nunca con el formulario ni con precios actuales.
 */
@Service
@RequiredArgsConstructor
public class VentaConsultaService {

    /** Leyenda del comprobante cuando no hay integración DIAN validada. */
    public static final String LEYENDA_INTERNA = "Comprobante interno de venta. No constituye factura electrónica de "
            + "venta ante la DIAN: la numeración fiscal es simulada hasta integrar un proveedor tecnológico autorizado.";
    /** Leyenda cuando la numeración es fiscalmente válida. */
    public static final String LEYENDA_FISCAL = "Representación gráfica de la factura electrónica de venta.";

    private final VentaRepository ventas;
    private final FacturaRepository facturas;
    private final TerceroRepository terceros;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final DocumentoDevolucionRepository documentosDevolucion;
    private final DevolucionService devoluciones;
    private final EventoAuditoriaService eventos;
    private final AuditoriaService auditoria;
    private final FacturacionPort facturacion;
    private final ParametrosNegocio parametros;

    /**
     * Filtros del listado de ventas.
     *
     * @param q texto contenido en el número interno
     * @param clienteId cliente
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param estado estado del documento
     */
    public record Filtros(String q, Long clienteId, LocalDate desde, LocalDate hasta, EstadoDocumento estado) { }

    /**
     * Listado paginado con filtros, más recientes primero.
     *
     * @param f filtros
     * @param pagina página
     * @param tamano tamaño
     * @return página de ventas
     */
    @Transactional(readOnly = true)
    public PaginaResponse<VentaResumenResponse> listar(Filtros f, int pagina, int tamano) {
        var page = ventas.findAll(especificacion(f, parametros.getZonaHoraria()), PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> clientes = new HashMap<>();
        Map<Integer, String> correos = new HashMap<>();
        return PaginaResponse.de(page, v -> {
            Factura fa = facturas.findByIdVenta(v.getId()).orElse(null);
            String cliente = clientes.computeIfAbsent(v.getIdCliente(),
                    k -> terceros.findById(k).map(TerceroResponse::nombreDe).orElse(""));
            String correo = v.getIdUsuario() == null ? null : correos.computeIfAbsent(v.getIdUsuario(),
                    k -> usuarios.findById(k).map(Usuario::getCorreo).orElse(""));
            VentaResponse r = VentaResponse.de(v, fa);
            return new VentaResumenResponse(v.getId(), v.getNumeroInterno(), fa == null ? null : fa.getNumeracionDian(),
                    v.getFecha(), v.getIdCliente(), cliente, correo, v.getEstado(), r.total(),
                    !documentosDevolucion.findByIdVentaOrderByIdAsc(v.getId()).isEmpty(), r.moneda(), r.totalMoneda());
        });
    }

    /**
     * Detalle completo de una venta (RF-18).
     *
     * @param id venta
     * @return detalle
     */
    @Transactional(readOnly = true)
    public VentaDetalleResponse detalle(Long id) {
        Venta v = ventas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Venta"));
        Factura f = facturas.findByIdVenta(v.getId()).orElse(null);
        Tercero c = terceros.findById(v.getIdCliente()).orElse(null);
        Usuario u = v.getIdUsuario() == null ? null : usuarios.findById(v.getIdUsuario()).orElse(null);
        Map<Long, VentaResponse.InfoProducto> info = new HashMap<>();
        for (LineaVenta l : v.getLineas()) {
            info.computeIfAbsent(l.getIdProducto(), k -> productos.findById(k)
                    .map(p -> new VentaResponse.InfoProducto(p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(),
                            p.getCodigoBarras(), p.getUnidadBase())).orElse(null));
        }
        boolean fiscal = facturacion.esValidaFiscalmente();
        VentaResponse venta = VentaResponse.de(v, f, c == null ? null : TerceroResponse.nombreDe(c),
                u == null ? null : u.getCorreo(), info, devoluciones.devueltoPorLinea(v), fiscal);
        List<String> motivos = new ArrayList<>();
        if (v.getLineas().isEmpty()) {
            motivos.add("La venta no tiene líneas.");
        }
        if (f == null || f.getNumeracionDian() == null) {
            motivos.add("La venta no tiene factura emitida.");
        }
        if (c == null) {
            motivos.add("No se encontraron los datos del cliente.");
        }
        if (v.getEstado() != EstadoDocumento.ACTIVO) {
            motivos.add("La venta está anulada.");
        }
        var movs = auditoria.consultarFiltrado(new AuditoriaService.Filtros(null, null, null, v.getNumeroInterno(), null, null),
                parametros.getZonaHoraria(), 0, 100).contenido().stream().filter(m -> v.getNumeroInterno().equals(m.documentoRelacionado())).toList();
        return new VentaDetalleResponse(venta, c == null ? null : TerceroResponse.de(c), u == null ? null : u.getNombre(),
                u == null ? null : u.getCorreo(), empresa(), devoluciones.deVenta(v.getId()),
                eventos.deEntidad("VENTA", v.getId()), movs, motivos.isEmpty(), motivos,
                fiscal ? LEYENDA_FISCAL : LEYENDA_INTERNA);
    }

    /**
     * Datos del negocio emisor.
     *
     * @return empresa
     */
    public EmpresaResponse empresa() {
        return new EmpresaResponse(parametros.getEmpresaNombre(), vacioANulo(parametros.getEmpresaNit()),
                vacioANulo(parametros.getEmpresaDireccion()), vacioANulo(parametros.getEmpresaCiudad()),
                vacioANulo(parametros.getEmpresaTelefono()), vacioANulo(parametros.getEmpresaCorreo()));
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static Specification<Venta> especificacion(Filtros f, java.time.ZoneId zona) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(cb.lower(root.<String>get("numeroInterno")),
                        "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%"));
            }
            if (f.clienteId() != null) {
                ps.add(cb.equal(root.get("idCliente"), f.clienteId()));
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
