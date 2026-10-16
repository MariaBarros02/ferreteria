/*
 * Nombre: PrecioService.java
 * Descripción: Propuesta, aprobación, rechazo e historial de precios (RF-07, RF-16, RN-07). El precio solo queda vigente tras la aprobación de un usuario distinto al proponente.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.PrecioPropuestaRequest;
import com.ferreteria.inventario.dto.PrecioResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida del precio: propuesto → aprobado | rechazado. Reglas:
 * <ul>
 *   <li>una propuesta nunca cambia el precio de venta (RN-07);</li>
 *   <li>solo Gerencia aprueba o rechaza, y nunca su propia propuesta (segregación de funciones);</li>
 *   <li>el historial conserva costo, porcentaje, precio, usuario y fechas de cada decisión.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PrecioService {

    /** Estado propuesto. */
    public static final String PROPUESTO = "propuesto";
    /** Estado aprobado. */
    public static final String APROBADO = "aprobado";
    /** Estado rechazado. */
    public static final String RECHAZADO = "rechazado";

    private final PrecioRepository precios;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final EventoAuditoriaService eventos;
    private final ParametrosNegocio parametros;
    private final Clock reloj;

    /**
     * Registra una propuesta de precio.
     *
     * @param req datos de la propuesta
     * @param actor usuario que propone
     * @return propuesta creada
     */
    @Transactional
    public PrecioResponse proponer(PrecioPropuestaRequest req, Actor actor) {
        Producto p = productos.findById(req.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        String modo = req.modo() == null ? parametros.getModoPrecioPorDefecto() : req.modo();
        BigDecimal costo = p.getCostoPromedio();
        BigDecimal porcentaje = req.porcentaje() != null ? req.porcentaje() : p.getPorcentajeIncremento();
        BigDecimal propuesto;
        if (CalculadoraPrecio.MANUAL.equals(modo)) {
            propuesto = CalculadoraPrecio.redondear(req.precioManual(), parametros.getMultiploRedondeoPrecio());
            if (porcentaje == null) {
                porcentaje = recargoImplicito(costo, propuesto);
            }
        } else {
            if (costo == null) {
                throw ApiException.regla("SIN_COSTO", "error.precio.sin.costo", p.getNombre());
            }
            if (porcentaje == null) {
                throw ApiException.regla("SIN_PORCENTAJE", "error.precio.sin.porcentaje", p.getNombre());
            }
            propuesto = CalculadoraPrecio.calcular(costo, porcentaje, modo, parametros.getMultiploRedondeoPrecio());
        }
        Precio nuevo = precios.save(Precio.builder().idProducto(p.getId())
                .costoPromedio(costo == null ? BigDecimal.ZERO : costo)
                .porcentajeIncremento(porcentaje).precioPropuesto(propuesto).estadoAprobacion(PROPUESTO)
                .modoCalculo(modo).motivo(limpiar(req.motivo())).usuarioProponente(actor.idUsuario()).build());
        eventos.registrar(actor, Auditables.PRECIOS, Auditables.PROPONER, "PRECIO", nuevo.getId(),
                "Precio propuesto para " + p.getNombre() + ": " + propuesto.stripTrailingZeros().toPlainString(),
                detalle(nuevo, p));
        return aDto(nuevo, p, new HashMap<>());
    }

    /**
     * Genera la propuesta automática tras una recepción cuando el producto tiene porcentaje de incremento
     * configurado. No crea duplicados si ya hay una propuesta pendiente con el mismo valor.
     *
     * @param p producto con su costo promedio actualizado
     * @param actor usuario de la recepción
     * @return propuesta creada, si se creó
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Precio> proponerAutomatico(Producto p, Actor actor) {
        if (p.getPorcentajeIncremento() == null || p.getCostoPromedio() == null) {
            return Optional.empty();
        }
        BigDecimal propuesto = CalculadoraPrecio.calcular(p.getCostoPromedio(), p.getPorcentajeIncremento(),
                CalculadoraPrecio.RECARGO, parametros.getMultiploRedondeoPrecio());
        boolean repetida = precios.findByIdProductoAndEstadoAprobacion(p.getId(), PROPUESTO).stream()
                .anyMatch(x -> x.getPrecioPropuesto().compareTo(propuesto) == 0);
        Optional<BigDecimal> vigente = vigente(p.getId());
        if (repetida || vigente.map(v -> v.compareTo(propuesto) == 0).orElse(false)) {
            return Optional.empty();
        }
        Precio nuevo = precios.save(Precio.builder().idProducto(p.getId()).costoPromedio(p.getCostoPromedio())
                .porcentajeIncremento(p.getPorcentajeIncremento()).precioPropuesto(propuesto)
                .estadoAprobacion(PROPUESTO).modoCalculo(CalculadoraPrecio.RECARGO)
                .motivo("Propuesta automática por recepción (costo promedio actualizado)")
                .usuarioProponente(actor.idUsuario()).build());
        eventos.registrar(actor, Auditables.PRECIOS, Auditables.PROPONER, "PRECIO", nuevo.getId(),
                "Precio propuesto automáticamente para " + p.getNombre(), detalle(nuevo, p));
        return Optional.of(nuevo);
    }

    /**
     * Aprueba una propuesta: su precio pasa a ser el vigente desde ahora.
     *
     * @param id propuesta
     * @param actor usuario que aprueba (Gerencia)
     * @return precio aprobado
     */
    @Transactional
    public PrecioResponse aprobar(Long id, Actor actor) {
        Precio pr = pendiente(id, actor);
        Producto p = productos.findById(pr.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        BigDecimal anterior = vigente(p.getId()).orElse(null);
        LocalDateTime ahora = LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC);
        pr.setEstadoAprobacion(APROBADO);
        pr.setPrecioVigente(pr.getPrecioPropuesto());
        pr.setUsuarioAprobador(actor.idUsuario());
        pr.setFechaDecision(ahora);
        pr.setFechaVigencia(ahora);
        pr = precios.save(pr);
        Map<String, Object> d = detalle(pr, p);
        d.put("precioAnterior", anterior);
        eventos.registrar(actor, Auditables.PRECIOS, Auditables.APROBAR, "PRECIO", pr.getId(),
                "Precio aprobado para " + p.getNombre() + ": " + pr.getPrecioVigente().stripTrailingZeros().toPlainString(), d);
        return aDto(pr, p, new HashMap<>());
    }

    /**
     * Rechaza una propuesta (no afecta el precio vigente).
     *
     * @param id propuesta
     * @param motivo motivo obligatorio
     * @param actor usuario que rechaza (Gerencia)
     * @return propuesta rechazada
     */
    @Transactional
    public PrecioResponse rechazar(Long id, String motivo, Actor actor) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.regla("MOTIVO_OBLIGATORIO", "error.motivo.obligatorio");
        }
        Precio pr = pendiente(id, actor);
        Producto p = productos.findById(pr.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        pr.setEstadoAprobacion(RECHAZADO);
        pr.setUsuarioAprobador(actor.idUsuario());
        pr.setMotivoRechazo(motivo.trim());
        pr.setFechaDecision(LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC));
        pr = precios.save(pr);
        eventos.registrar(actor, Auditables.PRECIOS, Auditables.RECHAZAR, "PRECIO", pr.getId(),
                "Precio rechazado para " + p.getNombre(), detalle(pr, p));
        return aDto(pr, p, new HashMap<>());
    }

    /**
     * Precio vigente de un producto.
     *
     * @param idProducto producto
     * @return precio vigente, si existe
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> vigente(Long idProducto) {
        List<Precio> v = precios.vigentes(idProducto, PageRequest.of(0, 1));
        return v.isEmpty() ? Optional.empty() : Optional.of(v.get(0).getPrecioVigente());
    }

    /**
     * Historial de precios de un producto.
     *
     * @param idProducto producto
     * @param pagina página
     * @param tamano tamaño
     * @return página del historial
     */
    @Transactional(readOnly = true)
    public PaginaResponse<PrecioResponse> historial(Long idProducto, int pagina, int tamano) {
        Producto p = productos.findById(idProducto).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        Page<Precio> page = precios.findByIdProductoOrderByIdDesc(idProducto,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
        Map<Integer, String> correos = new HashMap<>();
        return PaginaResponse.de(page, x -> aDto(x, p, correos));
    }

    /**
     * Propuestas por estado (por defecto, pendientes de aprobación).
     *
     * @param estado propuesto, aprobado o rechazado
     * @param pagina página
     * @param tamano tamaño
     * @return página
     */
    @Transactional(readOnly = true)
    public PaginaResponse<PrecioResponse> porEstado(String estado, int pagina, int tamano) {
        Page<Precio> page = precios.findByEstadoAprobacionOrderByIdDesc(estado == null ? PROPUESTO : estado,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
        Map<Long, Producto> cache = new HashMap<>();
        Map<Integer, String> correos = new HashMap<>();
        return PaginaResponse.de(page, x -> aDto(x, cache.computeIfAbsent(x.getIdProducto(),
                k -> productos.findById(k).orElse(null)), correos));
    }

    private Precio pendiente(Long id, Actor actor) {
        Precio pr = precios.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Precio"));
        if (!PROPUESTO.equals(pr.getEstadoAprobacion())) {
            throw ApiException.conflicto("PRECIO_YA_DECIDIDO", "error.precio.decidido", pr.getEstadoAprobacion());
        }
        if (pr.getUsuarioProponente() != null && pr.getUsuarioProponente().equals(actor.idUsuario())) {
            throw ApiException.conflicto("MISMA_PERSONA", "error.misma.persona");
        }
        return pr;
    }

    private PrecioResponse aDto(Precio x, Producto p, Map<Integer, String> correos) {
        BigDecimal actual = vigente(x.getIdProducto()).orElse(null);
        return new PrecioResponse(x.getId(), x.getIdProducto(),
                p == null ? null : p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(),
                x.getCostoPromedio(), x.getPorcentajeIncremento(), x.getModoCalculo(), x.getPrecioPropuesto(),
                x.getPrecioVigente(), actual, CalculadoraPrecio.margen(x.getCostoPromedio(), x.getPrecioPropuesto()),
                x.getEstadoAprobacion(), correo(x.getUsuarioProponente(), correos), correo(x.getUsuarioAprobador(), correos),
                x.getMotivo(), x.getMotivoRechazo(), x.getFechaPropuesta(), x.getFechaDecision(), x.getFechaVigencia());
    }

    private String correo(Integer id, Map<Integer, String> cache) {
        if (id == null) {
            return null;
        }
        return cache.computeIfAbsent(id, k -> usuarios.findById(k).map(Usuario::getCorreo).orElse(null));
    }

    private static BigDecimal recargoImplicito(BigDecimal costo, BigDecimal precio) {
        if (costo == null || costo.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal r = precio.divide(costo, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        return r.signum() < 0 ? BigDecimal.ZERO : r.min(new BigDecimal("99.9999"));
    }

    private static Map<String, Object> detalle(Precio x, Producto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("producto", p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion());
        m.put("costoPromedio", x.getCostoPromedio());
        m.put("porcentaje", x.getPorcentajeIncremento());
        m.put("modo", x.getModoCalculo());
        m.put("precioPropuesto", x.getPrecioPropuesto());
        m.put("precioVigente", x.getPrecioVigente());
        m.put("estado", x.getEstadoAprobacion());
        return m;
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
