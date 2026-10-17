/*
 * Nombre: MonedaService.java
 * Descripción: Monedas y tasas de cambio (RF-23, RN-17): catálogo ISO 4217, moneda funcional COP, registro inmutable de tasas con vigencia y fuente, tasa vigente para documentos y conversión entre monedas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.Moneda;
import com.ferreteria.inventario.domain.model.TasaCambio;
import com.ferreteria.inventario.dto.ConversionMonedaResponse;
import com.ferreteria.inventario.dto.MonedaRequest;
import com.ferreteria.inventario.dto.MonedaResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TasaCambioRequest;
import com.ferreteria.inventario.dto.TasaCambioResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.MonedaRepository;
import com.ferreteria.inventario.repository.TasaCambioRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La moneda funcional (COP) es la de inventario, costos, cierres y auditoría. Un documento en otra moneda guarda
 * la tasa aplicada (pesos por unidad) y sus importes en ambas monedas. Las tasas no se editan ni se borran:
 * una corrección es una tasa nueva (la vigente es la de fecha más reciente no posterior a la fecha de referencia
 * y, el mismo día, la última registrada).
 */
@Service
@RequiredArgsConstructor
public class MonedaService {

    /** Código de la moneda funcional cuando la tabla no la define (compatibilidad). */
    public static final String FUNCIONAL_POR_DEFECTO = "COP";
    /** Escala del precio unitario convertido. */
    public static final int ESCALA_PRECIO = 4;

    private final MonedaRepository monedas;
    private final TasaCambioRepository tasas;
    private final UsuarioRepository usuarios;
    private final KardexService kardex;
    private final EventoAuditoriaService eventos;

    /**
     * Tasa aplicable a un documento.
     *
     * @param moneda moneda del documento
     * @param tasa pesos colombianos por 1 unidad (1 en la funcional)
     * @param fechaTasa fecha de vigencia de la tasa (nula en la funcional)
     */
    public record Cambio(String moneda, BigDecimal tasa, LocalDate fechaTasa) {
        /** @return true si el documento está en la moneda funcional */
        public boolean esFuncional() {
            return tasa.compareTo(BigDecimal.ONE) == 0 && fechaTasa == null;
        }
    }

    /**
     * Monedas con su tasa vigente hoy.
     *
     * @param soloActivas filtrar las activas
     * @return monedas
     */
    @Transactional(readOnly = true)
    public List<MonedaResponse> listar(boolean soloActivas) {
        LocalDate hoy = kardex.hoy();
        return monedas.findAllByOrderByFuncionalDescCodigoAsc().stream()
                .filter(m -> !soloActivas || m.isActiva())
                .map(m -> aDto(m, m.isFuncional() ? Optional.empty() : tasas
                        .findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(m.getCodigo(), hoy)))
                .toList();
    }

    /**
     * Código de la moneda funcional.
     *
     * @return código (COP)
     */
    @Transactional(readOnly = true)
    public String funcional() {
        return monedas.findFirstByFuncionalTrue().map(Moneda::getCodigo).orElse(FUNCIONAL_POR_DEFECTO);
    }

    /**
     * Tasa vigente hoy para emitir un documento en la moneda indicada.
     *
     * @param codigo moneda (nula o vacía = funcional)
     * @return cambio a aplicar
     * @throws ApiException 422 MONEDA_INVALIDA si no existe o está inactiva; 422 SIN_TASA_CAMBIO si no hay tasa vigente
     */
    @Transactional(readOnly = true)
    public Cambio cambioParaDocumento(String codigo) {
        String funcional = funcional();
        if (codigo == null || codigo.isBlank() || codigo.trim().equalsIgnoreCase(funcional)) {
            return new Cambio(funcional, BigDecimal.ONE, null);
        }
        Moneda m = activa(codigo);
        TasaCambio t = tasas.findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(
                        m.getCodigo(), kardex.hoy())
                .orElseThrow(() -> ApiException.regla("SIN_TASA_CAMBIO", "error.moneda.sin.tasa", m.getCodigo()));
        return new Cambio(m.getCodigo(), t.getTasa(), t.getFechaVigencia());
    }

    /**
     * Registra una tasa de cambio (inmutable).
     *
     * @param codigo moneda
     * @param req tasa, fecha de vigencia y fuente
     * @param actor usuario (Gerencia o Administrador)
     * @return tasa registrada
     */
    @Transactional
    public TasaCambioResponse registrarTasa(String codigo, TasaCambioRequest req, Actor actor) {
        Moneda m = existente(codigo);
        if (m.isFuncional()) {
            throw ApiException.regla("MONEDA_FUNCIONAL", "error.moneda.funcional", m.getCodigo());
        }
        if (req.fechaVigencia().isAfter(kardex.hoy())) {
            throw ApiException.regla("TASA_FUTURA", "error.moneda.tasa.futura", String.valueOf(req.fechaVigencia()));
        }
        TasaCambio t = tasas.save(TasaCambio.builder().codigoMoneda(m.getCodigo())
                .tasa(req.tasa().setScale(6, RoundingMode.HALF_UP)).fechaVigencia(req.fechaVigencia())
                .fuente(req.fuente() == null || req.fuente().isBlank() ? null : req.fuente().trim())
                .idUsuario(actor.idUsuario()).fechaRegistro(kardex.ahora()).build());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("moneda", m.getCodigo());
        d.put("tasa", t.getTasa());
        d.put("fechaVigencia", String.valueOf(t.getFechaVigencia()));
        d.put("fuente", t.getFuente());
        eventos.registrar(actor, Auditables.MONEDAS, Auditables.TASA_CAMBIO, "TASA_CAMBIO", t.getId(),
                "Tasa " + m.getCodigo() + " = " + t.getTasa().stripTrailingZeros().toPlainString() + " COP desde "
                        + t.getFechaVigencia(), d);
        return aDto(t);
    }

    /**
     * Historial de tasas de una moneda.
     *
     * @param codigo moneda
     * @param pagina página
     * @param tamano tamaño
     * @return página de tasas
     */
    @Transactional(readOnly = true)
    public PaginaResponse<TasaCambioResponse> historial(String codigo, int pagina, int tamano) {
        Moneda m = existente(codigo);
        return PaginaResponse.de(tasas.findByCodigoMonedaOrderByFechaVigenciaDescIdDesc(m.getCodigo(),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100))), this::aDto);
    }

    /**
     * Registra una moneda adicional (inactiva hasta que se active y tenga tasa).
     *
     * @param req datos ISO 4217
     * @param actor administrador
     * @return moneda
     */
    @Transactional
    public MonedaResponse crear(MonedaRequest req, Actor actor) {
        String codigo = req.codigo().trim().toUpperCase(Locale.ROOT);
        if (monedas.existsById(codigo)) {
            throw ApiException.conflicto("MONEDA_DUPLICADA", "error.moneda.duplicada", codigo);
        }
        Moneda m = monedas.save(Moneda.builder().codigo(codigo).nombre(req.nombre().trim()).simbolo(req.simbolo().trim())
                .decimales((short) req.decimales()).activa(true).funcional(false).build());
        eventos.registrar(actor, Auditables.MONEDAS, Auditables.CREAR, "MONEDA", codigo, "Moneda registrada " + codigo,
                Map.of("nombre", m.getNombre(), "simbolo", m.getSimbolo(), "decimales", (int) m.getDecimales()));
        return aDto(m, Optional.empty());
    }

    /**
     * Activa o desactiva una moneda. La funcional no se desactiva; los documentos existentes conservan su moneda.
     *
     * @param codigo moneda
     * @param activa nuevo estado
     * @param actor usuario
     * @return moneda
     */
    @Transactional
    public MonedaResponse cambiarEstado(String codigo, boolean activa, Actor actor) {
        Moneda m = existente(codigo);
        if (m.isFuncional() && !activa) {
            throw ApiException.regla("MONEDA_FUNCIONAL", "error.moneda.funcional", m.getCodigo());
        }
        m.setActiva(activa);
        m = monedas.save(m);
        eventos.registrar(actor, Auditables.MONEDAS, activa ? Auditables.ACTIVAR : Auditables.DESACTIVAR, "MONEDA",
                m.getCodigo(), (activa ? "Moneda activada " : "Moneda desactivada ") + m.getCodigo(), Map.of("activa", activa));
        return aDto(m, Optional.empty());
    }

    /**
     * Convierte un monto entre dos monedas con las tasas vigentes hoy (a través de la moneda funcional).
     *
     * @param monto monto
     * @param desde moneda de origen
     * @param hacia moneda de destino
     * @return conversión con las tasas utilizadas
     */
    @Transactional(readOnly = true)
    public ConversionMonedaResponse convertir(BigDecimal monto, String desde, String hacia) {
        Cambio a = cambioParaDocumento(desde);
        Cambio b = cambioParaDocumento(hacia);
        int decimales = monedas.findById(b.moneda()).map(m -> (int) m.getDecimales()).orElse(2);
        BigDecimal resultado = monto.multiply(a.tasa()).divide(b.tasa(), decimales, RoundingMode.HALF_UP);
        return new ConversionMonedaResponse(monto, a.moneda(), b.moneda(), resultado, a.tasa(), b.tasa(), kardex.hoy());
    }

    /**
     * Precio unitario en la moneda del documento a partir del precio en COP.
     *
     * @param precioFuncional precio en COP
     * @param tasa pesos por unidad de la moneda del documento
     * @return precio convertido (4 decimales, HALF_UP)
     */
    public static BigDecimal aMonedaDocumento(BigDecimal precioFuncional, BigDecimal tasa) {
        return precioFuncional.divide(tasa, ESCALA_PRECIO, RoundingMode.HALF_UP);
    }

    /**
     * Equivalente en COP de un importe en la moneda del documento.
     *
     * @param importe importe en la moneda del documento
     * @param tasa pesos por unidad
     * @return importe en COP (2 decimales, HALF_UP)
     */
    public static BigDecimal aFuncional(BigDecimal importe, BigDecimal tasa) {
        return Montos.redondear(importe.multiply(tasa));
    }

    /**
     * Costo por unidad en COP (4 decimales) a partir del costo en la moneda de la compra.
     *
     * @param costo costo en la moneda de la compra
     * @param tasa pesos por unidad
     * @return costo en COP
     */
    public static BigDecimal costoFuncional(BigDecimal costo, BigDecimal tasa) {
        return costo.multiply(tasa).setScale(ESCALA_PRECIO, RoundingMode.HALF_UP);
    }

    private Moneda existente(String codigo) {
        String c = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return monedas.findById(c).orElseThrow(() -> ApiException.noEncontrado("Moneda"));
    }

    private Moneda activa(String codigo) {
        String c = codigo.trim().toUpperCase(Locale.ROOT);
        Moneda m = monedas.findById(c).orElseThrow(() -> ApiException.regla("MONEDA_INVALIDA", "error.moneda.invalida", c));
        if (!m.isActiva()) {
            throw ApiException.regla("MONEDA_INVALIDA", "error.moneda.invalida", c);
        }
        return m;
    }

    private MonedaResponse aDto(Moneda m, Optional<TasaCambio> t) {
        return new MonedaResponse(m.getCodigo(), m.getNombre(), m.getSimbolo(), m.getDecimales(), m.isActiva(),
                m.isFuncional(), m.isFuncional() ? BigDecimal.ONE : t.map(TasaCambio::getTasa).orElse(null),
                t.map(TasaCambio::getFechaVigencia).orElse(null), t.map(TasaCambio::getFuente).orElse(null));
    }

    private TasaCambioResponse aDto(TasaCambio t) {
        String correo = t.getIdUsuario() == null ? null
                : usuarios.findById(t.getIdUsuario()).map(u -> u.getCorreo()).orElse(null);
        return new TasaCambioResponse(t.getId(), t.getCodigoMoneda(), t.getTasa(), t.getFechaVigencia(), t.getFuente(),
                correo, t.getFechaRegistro());
    }
}
