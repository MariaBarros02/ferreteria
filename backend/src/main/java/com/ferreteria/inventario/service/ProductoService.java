/*
 * Nombre: ProductoService.java
 * Descripción: Casos de uso del catálogo de productos: CRUD, búsqueda por código de barras y configuración de unidades (RF-01, RF-02).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.ConversionRequest;
import com.ferreteria.inventario.dto.ConversionResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.ProductoResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
        aplicar(p, req);
        return ProductoResponse.de(productos.save(p));
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
     * Elimina un producto sin existencias ni movimientos. Si tiene historial la base de datos lo impide (409).
     *
     * @param id identificador
     */
    @Transactional
    public void eliminar(Long id) {
        buscar(id);
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
        return reqs.stream().map(r -> conversiones.save(ConversionUnidad.builder().idProducto(id)
                .unidadPresentacion(r.unidadPresentacion().trim()).factorAUnidadBase(r.factor())
                .precisionDecimales(r.precisionDecimales()).build())).map(ConversionResponse::de).toList();
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

    private Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Producto"));
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

    private void aplicar(Producto p, ProductoRequest r) {
        p.setNombre(r.nombre().trim());
        p.setMarca(r.marca().trim());
        p.setPresentacion(r.presentacion().trim());
        p.setCodigoBarras(r.codigoBarras().trim());
        p.setUnidadBase(r.unidadBase().trim());
        p.setTipoRastreo(r.tipoRastreo());
        p.setTieneVencimiento(r.tieneVencimiento());
        p.setTieneGarantia(r.tieneGarantia());
    }
}
