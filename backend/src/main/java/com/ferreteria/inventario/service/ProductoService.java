/*
 * Nombre: ProductoService.java
 * Descripción: Lógica de negocio del catálogo: unicidad (RN-01), conversiones (RF-02), desactivación, búsqueda para selectores y auditoría de cambios.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.ConversionRequest;
import com.ferreteria.inventario.dto.ConversionResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.ProductoResponse;
import com.ferreteria.inventario.dto.ProductoSeleccionResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.LoteRepository;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio del catálogo. Garantiza la unicidad de la referencia y del código de barras (RN-01).
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final ConversionUnidadRepository conversiones;
    private final PrecioRepository precios;
    private final LoteRepository lotes;
    private final EventoAuditoriaService eventos;
    private final ContextoSolicitud contexto;
    private final ParametrosNegocio parametros;
    private final Clock reloj;

    /**
     * Crea una referencia y su saldo inicial en cero.
     *
     * @param req datos validados
     * @return producto creado
     */
    @Transactional
    public ProductoResponse crear(ProductoRequest req) {
        validarUnicidad(req, null);
        Producto p = new Producto();
        aplicar(p, req);
        p = productos.save(p);
        inventarios.save(Inventario.builder().idProducto(p.getId())
                .disponible(BigDecimal.ZERO).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build());
        eventos.registrar(actor(), Auditables.PRODUCTOS, Auditables.CREAR, "PRODUCTO", p.getId(),
                "Producto creado: " + etiqueta(p), resumen(p));
        return ProductoResponse.de(p);
    }

    /**
     * Actualiza una referencia existente.
     *
     * @param id identificador
     * @param req nuevos datos
     * @return producto actualizado
     */
    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest req) {
        Producto p = buscar(id);
        validarUnicidad(req, id);
        Map<String, Object> antes = resumen(p);
        aplicar(p, req);
        p = productos.save(p);
        eventos.registrar(actor(), Auditables.PRODUCTOS, Auditables.ACTUALIZAR, "PRODUCTO", p.getId(),
                "Producto actualizado: " + etiqueta(p), Map.of("antes", antes, "despues", resumen(p)));
        return ProductoResponse.de(p);
    }

    /**
     * Activa o desactiva una referencia. Una referencia inactiva no aparece en los selectores ni se puede
     * vender ni comprar, pero conserva su historial (eliminación lógica).
     *
     * @param id identificador
     * @param activo nuevo estado
     * @return producto actualizado
     */
    @Transactional
    public ProductoResponse cambiarEstado(Long id, boolean activo) {
        Producto p = buscar(id);
        if (p.isActivo() != activo) {
            p.setActivo(activo);
            p = productos.save(p);
            eventos.registrar(actor(), Auditables.PRODUCTOS, activo ? Auditables.ACTIVAR : Auditables.DESACTIVAR,
                    "PRODUCTO", p.getId(), (activo ? "Producto activado: " : "Producto desactivado: ") + etiqueta(p), null);
        }
        return ProductoResponse.de(p);
    }

    /**
     * Consulta por identificador.
     *
     * @param id identificador
     * @return producto
     */
    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.de(buscar(id));
    }

    /**
     * Consulta por código de barras (RF-01, RF-11): un código conocido siempre recupera la misma referencia.
     *
     * @param codigo código de barras
     * @return producto
     */
    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorCodigo(String codigo) {
        return productos.findByCodigoBarras(codigo.trim()).map(ProductoResponse::de)
                .orElseThrow(() -> ApiException.noEncontrado("Producto"));
    }

    /**
     * Lista paginada con filtro de texto.
     *
     * @param q texto opcional
     * @param pageable paginación
     * @return página de productos
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ProductoResponse> listar(String q, Pageable pageable) {
        return PaginaResponse.de(productos.buscar(q == null ? "" : q.trim(), pageable), ProductoResponse::de);
    }

    /**
     * Búsqueda para selectores: solo referencias activas, con existencias, precio vigente y presentaciones.
     *
     * @param q texto (nombre, marca, presentación, categoría o código)
     * @param tamano máximo de resultados (1–50)
     * @return coincidencias
     */
    @Transactional(readOnly = true)
    public List<ProductoSeleccionResponse> buscarParaSeleccion(String q, int tamano) {
        var pagina = productos.buscarActivos(q == null ? "" : q.trim(),
                PageRequest.of(0, Math.min(Math.max(tamano, 1), 50), org.springframework.data.domain.Sort.by("nombre", "marca", "presentacion")));
        return pagina.getContent().stream().map(this::seleccion).toList();
    }

    /**
     * Catálogo de referencias activas para consulta sin conexión (RF-12): existencias, precio vigente y
     * presentaciones, paginado para sincronizar por partes.
     *
     * @param pagina página
     * @param tamano tamaño (1–500)
     * @return página del catálogo
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ProductoSeleccionResponse> catalogo(int pagina, int tamano) {
        var page = productos.buscarActivos("", PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 500),
                org.springframework.data.domain.Sort.by("id")));
        return PaginaResponse.de(page, this::seleccion);
    }

    /**
     * Datos de selección de un producto por código de barras (lectores, RF-11). Solo referencias activas.
     *
     * @param codigo código leído
     * @return producto
     */
    @Transactional(readOnly = true)
    public ProductoSeleccionResponse seleccionPorCodigo(String codigo) {
        Producto p = productos.findByCodigoBarras(codigo.trim()).filter(Producto::isActivo)
                .orElseThrow(() -> ApiException.noEncontrado("Producto"));
        return seleccion(p);
    }

    /**
     * Elimina un producto sin existencias ni movimientos. Si tiene historial la base de datos lo impide (409):
     * en ese caso debe desactivarse.
     *
     * @param id identificador
     */
    @Transactional
    public void eliminar(Long id) {
        Producto p = buscar(id);
        Inventario inv = inventarios.findById(id).orElse(null);
        if (inv != null && (inv.getDisponible().signum() != 0 || inv.getReservado().signum() != 0
                || inv.getDanado().signum() != 0)) {
            throw ApiException.conflicto("PRODUCTO_CON_EXISTENCIAS", "error.producto.existencias");
        }
        conversiones.deleteByIdProducto(id);
        if (inv != null) {
            inventarios.delete(inv);
        }
        productos.deleteById(id);
        productos.flush();   // fuerza la verificación de claves foráneas dentro de la transacción
        eventos.registrar(actor(), Auditables.PRODUCTOS, Auditables.ELIMINAR, "PRODUCTO", id,
                "Producto eliminado (sin historial): " + etiqueta(p), resumen(p));
    }

    /**
     * Reemplaza las conversiones de unidad de un producto (RF-02).
     *
     * @param id producto
     * @param reqs conversiones (sin presentaciones repetidas)
     * @return conversiones configuradas
     */
    @Transactional
    public List<ConversionResponse> configurarUnidades(Long id, List<ConversionRequest> reqs) {
        Producto p = buscar(id);
        Set<String> vistas = new HashSet<>();
        for (ConversionRequest r : reqs) {
            String clave = r.unidadPresentacion().trim().toLowerCase();
            if (!vistas.add(clave) || clave.equalsIgnoreCase(p.getUnidadBase())) {
                throw ApiException.regla("CONVERSION_INVALIDA", "error.conversion.no.configurada",
                        r.unidadPresentacion(), p.getNombre());
            }
        }
        conversiones.deleteByIdProducto(id);
        conversiones.flush();
        List<ConversionResponse> r = reqs.stream().map(c -> conversiones.save(ConversionUnidad.builder().idProducto(id)
                .unidadPresentacion(c.unidadPresentacion().trim()).factorAUnidadBase(c.factor())
                .precisionDecimales(c.precisionDecimales()).build())).map(ConversionResponse::de).toList();
        eventos.registrar(actor(), Auditables.PRODUCTOS, Auditables.ACTUALIZAR, "PRODUCTO", id,
                "Conversiones de unidad configuradas: " + etiqueta(p), Map.of("conversiones", r));
        return r;
    }

    /**
     * Lista las conversiones de un producto.
     *
     * @param id producto
     * @return conversiones
     */
    @Transactional(readOnly = true)
    public List<ConversionResponse> unidades(Long id) {
        buscar(id);
        return conversiones.findByIdProducto(id).stream().map(ConversionResponse::de).toList();
    }

    /**
     * Saldos por estado de un producto.
     *
     * @param id producto
     * @return saldos
     */
    @Transactional(readOnly = true)
    public InventarioResponse existencias(Long id) {
        buscar(id);
        return inventarios.findById(id).map(InventarioResponse::de)
                .orElseThrow(() -> ApiException.noEncontrado("Inventario"));
    }

    /**
     * Precio vigente aprobado de un producto, si existe.
     *
     * @param idProducto producto
     * @return precio vigente o {@code null}
     */
    @Transactional(readOnly = true)
    public BigDecimal precioVigente(Long idProducto) {
        List<Precio> v = precios.vigentes(idProducto, PageRequest.of(0, 1));
        return v.isEmpty() ? null : v.get(0).getPrecioVigente();
    }

    private ProductoSeleccionResponse seleccion(Producto p) {
        Inventario inv = inventarios.findById(p.getId()).orElse(null);
        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), parametros.getZonaHoraria());
        BigDecimal vencido = LoteService.porLote(p) ? lotes.sumaVencida(p.getId(), hoy) : BigDecimal.ZERO;
        return new ProductoSeleccionResponse(p.getId(), p.getNombre(), p.getMarca(), p.getPresentacion(),
                p.getCodigoBarras(), p.getUnidadBase(), p.getTipoRastreo(), p.isTieneVencimiento(), p.isActivo(),
                p.getPorcentajeIva(), precioVigente(p.getId()),
                inv == null ? BigDecimal.ZERO : inv.getDisponible(), inv == null ? BigDecimal.ZERO : inv.getReservado(),
                vencido, p.getStockMinimo(),
                conversiones.findByIdProducto(p.getId()).stream().map(ConversionResponse::de).toList());
    }

    private Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Producto"));
    }

    private Actor actor() {
        return contexto.actor();
    }

    private void validarUnicidad(ProductoRequest r, Long idActual) {
        productos.findByCodigoBarras(r.codigoBarras().trim()).ifPresent(otro -> {
            if (!otro.getId().equals(idActual)) {
                throw ApiException.conflicto("CODIGO_DUPLICADO", "error.codigo.duplicado", r.codigoBarras());
            }
        });
        productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(
                r.nombre().trim(), r.marca().trim(), r.presentacion().trim()).ifPresent(otro -> {
            if (!otro.getId().equals(idActual)) {
                throw ApiException.conflicto("REFERENCIA_DUPLICADA", "error.referencia.duplicada");
            }
        });
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void aplicar(Producto p, ProductoRequest r) {
        p.setNombre(r.nombre().trim());
        p.setMarca(r.marca().trim());
        p.setPresentacion(r.presentacion().trim());
        p.setCodigoBarras(r.codigoBarras().trim());
        p.setUnidadBase(r.unidadBase().trim());
        p.setTipoRastreo(r.tipoRastreo());
        p.setTieneVencimiento(r.tieneVencimiento());
        p.setTieneGarantia(r.tieneGarantia());
        p.setDescripcion(limpiar(r.descripcion()));
        p.setCategoria(limpiar(r.categoria()));
        p.setPorcentajeIva(r.porcentajeIva() == null ? BigDecimal.ZERO : r.porcentajeIva());
        p.setPorcentajeIncremento(r.porcentajeIncremento());
        p.setStockMinimo(r.stockMinimo());
        p.setMesesGarantia(r.tieneGarantia() ? r.mesesGarantia() : null);
    }

    private static String etiqueta(Producto p) {
        return p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion() + " (" + p.getCodigoBarras() + ")";
    }

    private static Map<String, Object> resumen(Producto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", p.getNombre());
        m.put("marca", p.getMarca());
        m.put("presentacion", p.getPresentacion());
        m.put("codigoBarras", p.getCodigoBarras());
        m.put("unidadBase", p.getUnidadBase());
        m.put("tipoRastreo", String.valueOf(p.getTipoRastreo()));
        m.put("tieneVencimiento", p.isTieneVencimiento());
        m.put("tieneGarantia", p.isTieneGarantia());
        m.put("activo", p.isActivo());
        m.put("porcentajeIva", p.getPorcentajeIva());
        m.put("porcentajeIncremento", p.getPorcentajeIncremento());
        m.put("stockMinimo", p.getStockMinimo());
        return m;
    }
}
