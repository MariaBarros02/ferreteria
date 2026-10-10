/*
 * Nombre: VentaService.java
 * Descripción: Caso de uso de venta (RF-04): verifica saldo, descuenta de forma atómica, audita y factura; todo o nada (RNF-02, RN-11).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.ModalidadFactura;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.domain.model.Factura;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.LineaVenta;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.domain.model.Venta;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.FacturaRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.repository.VentaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el registro de ventas. Las filas de inventario se bloquean ({@code FOR UPDATE}) en orden
 * de producto para evitar interbloqueos; si cualquier línea falla se revierte toda la transacción, de modo
 * que nunca queda una factura sin su salida de inventario consistente.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventas;
    private final TerceroRepository terceros;
    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final ConversionUnidadRepository conversiones;
    private final PrecioRepository precios;
    private final FacturaRepository facturas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final FacturacionPort facturacion;
    private final Clock reloj;

    /**
     * Registra una venta y emite su factura.
     *
     * @param req solicitud validada
     * @param correoUsuario correo del usuario autenticado
     * @param ip IP de origen de la petición
     * @return venta con factura
     * @throws ApiException 409 si el saldo es insuficiente, 422 si falta precio vigente o conversión, 404/422 por cliente
     */
    @Transactional(rollbackFor = Exception.class)
    public VentaResponse registrar(VentaRequest req, String correoUsuario, String ip) {
        Tercero cliente = terceros.findById(req.clienteId()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        if (!cliente.isCliente()) {
            throw ApiException.regla("CLIENTE_INVALIDO", "error.cliente.invalido", cliente.getNumeroIdentificacion());
        }
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> ApiException.noEncontrado("Usuario"));

        Venta venta = ventas.save(Venta.builder()
                .numeroInterno(String.format("VTA-%06d", ventas.siguienteNumeroVenta()))
                .idCliente(cliente.getId())
                .fecha(LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC))
                .estado(EstadoDocumento.ACTIVO).build());

        List<VentaRequest.Linea> ordenadas = req.lineas().stream()
                .sorted(Comparator.comparing(VentaRequest.Linea::productoId)).toList();

        for (VentaRequest.Linea l : ordenadas) {
            Producto p = productos.findById(l.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            BigDecimal cantidadBase = aUnidadBase(p, l);
            Precio precio = precios.findFirstByIdProductoAndPrecioVigenteIsNotNullOrderByIdDesc(p.getId())
                    .orElseThrow(() -> ApiException.regla("PRECIO_NO_VIGENTE", "error.precio.no.vigente", p.getNombre()));

            Inventario inv = inventarios.findForUpdate(p.getId()).orElseThrow(() -> ApiException.noEncontrado("Inventario"));
            BigDecimal anterior = inv.getDisponible();
            if (anterior.compareTo(cantidadBase) < 0) {
                throw ApiException.conflicto("SALDO_INSUFICIENTE", "error.saldo.insuficiente",
                        p.getNombre(), anterior.stripTrailingZeros().toPlainString(),
                        cantidadBase.stripTrailingZeros().toPlainString());
            }
            BigDecimal nuevo = anterior.subtract(cantidadBase);
            inv.setDisponible(nuevo);
            inventarios.save(inv);

            venta.getLineas().add(LineaVenta.builder().venta(venta).idProducto(p.getId())
                    .cantidad(cantidadBase).precioAplicado(precio.getPrecioVigente()).build());
            auditoria.registrar(TipoMovimiento.VENTA, p.getId(), cantidadBase.negate(), usuario.getId(), ip,
                    venta.getNumeroInterno(), anterior, nuevo, "Venta " + venta.getNumeroInterno());
        }
        venta = ventas.save(venta);

        Factura factura = facturas.save(Factura.builder()
                .idVenta(venta.getId())
                .numeracionDian(facturacion.generarNumeracionFiscal(venta))
                .modalidad(ModalidadFactura.ELECTRONICA)
                .impuestos(req.impuestos() == null ? BigDecimal.ZERO : req.impuestos())
                .descuentos(req.descuentos() == null ? BigDecimal.ZERO : req.descuentos())
                .fechaEmision(LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC)).build());
        return VentaResponse.de(venta, factura);
    }

    /**
     * Consulta una venta con su factura.
     *
     * @param id identificador de la venta
     * @return venta
     */
    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id) {
        Venta v = ventas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Venta"));
        return VentaResponse.de(v, facturas.findByIdVenta(id).orElse(null));
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
        return PaginaResponse.de(page, v -> VentaResponse.de(v, facturas.findByIdVenta(v.getId()).orElse(null)));
    }

    /**
     * Convierte la cantidad de la línea a la unidad base del producto (RF-02).
     *
     * @param p producto
     * @param l línea solicitada
     * @return cantidad en unidad base
     */
    BigDecimal aUnidadBase(Producto p, VentaRequest.Linea l) {
        String unidad = l.unidad();
        if (unidad == null || unidad.isBlank() || unidad.trim().equalsIgnoreCase(p.getUnidadBase())) {
            return l.cantidad();
        }
        ConversionUnidad c = conversiones
                .findByIdProductoAndUnidadPresentacionIgnoreCase(p.getId(), unidad.trim())
                .orElseThrow(() -> ApiException.regla("CONVERSION_NO_CONFIGURADA", "error.conversion.no.configurada",
                        unidad, p.getNombre()));
        int decimales = Math.max(l.cantidad().stripTrailingZeros().scale(), 0);
        if (decimales > c.getPrecisionDecimales()) {
            throw ApiException.regla("PRECISION_INVALIDA", "error.precision.invalida",
                    c.getPrecisionDecimales(), c.getUnidadPresentacion());
        }
        return l.cantidad().multiply(c.getFactorAUnidadBase());
    }
}
