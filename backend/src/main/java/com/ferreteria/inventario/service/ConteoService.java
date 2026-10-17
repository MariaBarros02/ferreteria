/*
 * Nombre: ConteoService.java
 * Descripción: Conteos físicos por sesión (RF-15): registro de cantidades, comparación con el saldo del sistema, cierre, aprobación que genera ajustes y rechazo sin efectos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoConteo;
import com.ferreteria.inventario.domain.enums.EstadoSesionConteo;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.ConteoRegistroRequest;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.SesionConteoRequest;
import com.ferreteria.inventario.dto.SesionConteoResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo: abierta (se registran conteos) → cerrada (espera aprobación) → aprobada (genera ajustes por las
 * diferencias) o rechazada (no altera saldos). Un conteo nunca modifica el inventario por sí solo (RN-08).
 * La diferencia se calcula contra el saldo disponible del sistema en el momento de registrar el conteo, de modo
 * que las ventas posteriores no se confunden con faltantes.
 */
@Service
@RequiredArgsConstructor
public class ConteoService {

    private final SesionConteoRepository sesiones;
    private final ConteoFisicoRepository conteos;
    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final AjusteInventarioRepository ajustes;
    private final UsuarioRepository usuarios;
    private final AjusteService ajusteService;
    private final KardexService kardex;
    private final EventoAuditoriaService eventos;

    /**
     * Abre una sesión de conteo.
     *
     * @param req observaciones
     * @param actor responsable
     * @return sesión abierta
     */
    @Transactional
    public SesionConteoResponse abrir(SesionConteoRequest req, Actor actor) {
        SesionConteo s = sesiones.save(SesionConteo.builder()
                .numero(String.format("CF-%06d", sesiones.siguienteNumero())).estado(EstadoSesionConteo.ABIERTA)
                .observaciones(req == null || req.observaciones() == null || req.observaciones().isBlank() ? null
                        : req.observaciones().trim())
                .idUsuario(actor.idUsuario()).fechaInicio(kardex.ahora()).build());
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.CREAR, "CONTEO", s.getNumero(),
                "Sesión de conteo " + s.getNumero() + " abierta", null);
        return aDto(s);
    }

    /**
     * Registra (o reemplaza) el conteo de un producto en una sesión abierta.
     *
     * @param idSesion sesión
     * @param req producto y cantidad contada
     * @param actor usuario que cuenta
     * @return sesión actualizada
     */
    @Transactional
    public SesionConteoResponse registrar(Long idSesion, ConteoRegistroRequest req, Actor actor) {
        SesionConteo s = enEstado(idSesion, EstadoSesionConteo.ABIERTA);
        Producto p = productos.findById(req.productoId()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        BigDecimal sistema = inventarios.findById(p.getId()).map(Inventario::getDisponible)
                .orElseThrow(() -> ApiException.noEncontrado("Inventario"));
        ConteoFisico c = conteos.findByIdSesionAndIdProducto(s.getId(), p.getId()).orElseGet(() -> ConteoFisico.builder()
                .idSesion(s.getId()).idProducto(p.getId()).estado(EstadoConteo.PENDIENTE).build());
        c.setCantidadContada(req.cantidadContada());
        c.setCantidadSistema(sistema);
        c.setDiferencia(req.cantidadContada().subtract(sistema));
        c.setFecha(kardex.ahora());
        c.setIdUsuario(actor.idUsuario());
        c.setObservacion(req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().trim());
        conteos.save(c);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("producto", p.getNombre());
        d.put("contado", req.cantidadContada());
        d.put("sistema", sistema);
        d.put("diferencia", c.getDiferencia());
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.CONTEO, "CONTEO", s.getNumero(),
                "Conteo de " + p.getNombre() + " en " + s.getNumero(), d);
        return aDto(s);
    }

    /**
     * Cierra el registro de la sesión (queda pendiente de aprobación).
     *
     * @param idSesion sesión
     * @param actor usuario
     * @return sesión cerrada
     */
    @Transactional
    public SesionConteoResponse cerrar(Long idSesion, Actor actor) {
        SesionConteo s = enEstado(idSesion, EstadoSesionConteo.ABIERTA);
        if (conteos.findByIdSesionOrderByIdAsc(s.getId()).isEmpty()) {
            throw ApiException.regla("CONTEO_VACIO", "error.conteo.vacio", s.getNumero());
        }
        s.setEstado(EstadoSesionConteo.CERRADA);
        s.setFechaCierre(kardex.ahora());
        sesiones.save(s);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.CERRAR, "CONTEO", s.getNumero(),
                "Sesión de conteo " + s.getNumero() + " cerrada para aprobación", null);
        return aDto(s);
    }

    /**
     * Aprueba la sesión: cada diferencia distinta de cero se convierte en un ajuste aplicado (RF-14, RF-15).
     *
     * @param idSesion sesión cerrada
     * @param actor aprobador (distinto de quien abrió la sesión)
     * @return sesión aprobada
     */
    @Transactional(rollbackFor = Exception.class)
    public SesionConteoResponse aprobar(Long idSesion, Actor actor) {
        SesionConteo s = enEstado(idSesion, EstadoSesionConteo.CERRADA);
        if (s.getIdUsuario().equals(actor.idUsuario())) {
            throw ApiException.conflicto("MISMA_PERSONA", "error.misma.persona");
        }
        List<ConteoFisico> lista = new ArrayList<>(conteos.findByIdSesionOrderByIdAsc(s.getId()));
        lista.sort(Comparator.comparing(ConteoFisico::getIdProducto));
        int ajustados = 0;
        for (ConteoFisico c : lista) {
            if (c.getDiferencia().signum() != 0) {
                Producto p = productos.findById(c.getIdProducto()).orElseThrow(() -> ApiException.noEncontrado("Producto"));
                AjusteInventario a = ajusteService.aplicarPorConteo(p, c, s, actor);
                c.setIdMovimientoAjuste(a.getIdMovimiento());
                ajustados++;
            }
            c.setEstado(EstadoConteo.APROBADO);
            conteos.save(c);
        }
        s.setEstado(EstadoSesionConteo.APROBADA);
        s.setIdUsuarioDecide(actor.idUsuario());
        s.setFechaDecision(kardex.ahora());
        sesiones.save(s);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.APROBAR, "CONTEO", s.getNumero(),
                "Sesión de conteo " + s.getNumero() + " aprobada; ajustes generados: " + ajustados,
                Map.of("ajustes", ajustados));
        return aDto(s);
    }

    /**
     * Rechaza la sesión: no se modifica ningún saldo.
     *
     * @param idSesion sesión cerrada
     * @param motivo motivo
     * @param actor usuario
     * @return sesión rechazada
     */
    @Transactional
    public SesionConteoResponse rechazar(Long idSesion, String motivo, Actor actor) {
        SesionConteo s = enEstado(idSesion, EstadoSesionConteo.CERRADA);
        for (ConteoFisico c : conteos.findByIdSesionOrderByIdAsc(s.getId())) {
            c.setEstado(EstadoConteo.RECHAZADO);
            conteos.save(c);
        }
        s.setEstado(EstadoSesionConteo.RECHAZADA);
        s.setIdUsuarioDecide(actor.idUsuario());
        s.setFechaDecision(kardex.ahora());
        s.setMotivoRechazo(motivo.trim());
        sesiones.save(s);
        eventos.registrar(actor, Auditables.INVENTARIO, Auditables.RECHAZAR, "CONTEO", s.getNumero(),
                "Sesión de conteo " + s.getNumero() + " rechazada", Map.of("motivo", motivo.trim()));
        return aDto(s);
    }

    /**
     * Detalle de una sesión.
     *
     * @param id sesión
     * @return sesión con conteos
     */
    @Transactional(readOnly = true)
    public SesionConteoResponse obtener(Long id) {
        return aDto(sesiones.findById(id).orElseThrow(() -> ApiException.noEncontrado("Sesión de conteo")));
    }

    /**
     * Historial de sesiones.
     *
     * @param estado filtro opcional
     * @param pagina página
     * @param tamano tamaño
     * @return página de sesiones
     */
    @Transactional(readOnly = true)
    public PaginaResponse<SesionConteoResponse> listar(EstadoSesionConteo estado, int pagina, int tamano) {
        var p = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        var page = estado == null ? sesiones.findAll(p) : sesiones.findByEstado(estado, p);
        return PaginaResponse.de(page, this::aDto);
    }

    private SesionConteo enEstado(Long id, EstadoSesionConteo esperado) {
        SesionConteo s = sesiones.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Sesión de conteo"));
        if (s.getEstado() != esperado) {
            throw ApiException.conflicto("CONTEO_ESTADO_INVALIDO", "error.conteo.estado", s.getNumero(),
                    s.getEstado().getValor());
        }
        return s;
    }

    private SesionConteoResponse aDto(SesionConteo s) {
        List<SesionConteoResponse.Linea> ls = conteos.findByIdSesionOrderByIdAsc(s.getId()).stream().map(c -> {
            Producto p = productos.findById(c.getIdProducto()).orElse(null);
            String ajuste = ajustes.findFirstByIdConteo(c.getId()).map(AjusteInventario::getNumero).orElse(null);
            BigDecimal actual = inventarios.findById(c.getIdProducto()).map(Inventario::getDisponible).orElse(null);
            return new SesionConteoResponse.Linea(c.getId(), c.getIdProducto(),
                    p == null ? null : p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion(),
                    p == null ? null : p.getCodigoBarras(), c.getCantidadSistema(), c.getCantidadContada(),
                    c.getDiferencia(), actual, c.getEstado(), c.getObservacion(), ajuste);
        }).toList();
        int conDif = (int) ls.stream().filter(l -> l.diferencia().signum() != 0).count();
        return new SesionConteoResponse(s.getId(), s.getNumero(), s.getEstado(), s.getObservaciones(),
                correo(s.getIdUsuario()), s.getFechaInicio(), s.getFechaCierre(), correo(s.getIdUsuarioDecide()),
                s.getFechaDecision(), s.getMotivoRechazo(), ls.size(), conDif, ls);
    }

    private String correo(Integer id) {
        return id == null ? null : usuarios.findById(id).map(Usuario::getCorreo).orElse(null);
    }
}
