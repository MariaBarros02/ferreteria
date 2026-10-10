/*
 * Nombre: TerceroService.java
 * Descripción: Casos de uso de terceros (clientes/proveedores): CRUD con reglas RN-06 y unicidad de identificación (RF-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.TerceroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de terceros. Persona natural y jurídica se normalizan para no almacenar
 * campos de la otra clase (datos inconsistentes).
 */
@Service
@RequiredArgsConstructor
public class TerceroService {

    private final TerceroRepository terceros;

    /**
     * Crea un tercero.
     *
     * @param req datos validados
     * @return tercero creado
     */
    @Transactional
    public TerceroResponse crear(TerceroRequest req) {
        validarIdentificacion(req.numeroIdentificacion().trim(), null);
        Tercero t = new Tercero();
        aplicar(t, req);
        return TerceroResponse.de(terceros.save(t));
    }

    /**
     * Actualiza un tercero.
     *
     * @param id identificador
     * @param req nuevos datos
     * @return tercero actualizado
     */
    @Transactional
    public TerceroResponse actualizar(Long id, TerceroRequest req) {
        Tercero t = buscar(id);
        validarIdentificacion(req.numeroIdentificacion().trim(), id);
        aplicar(t, req);
        return TerceroResponse.de(terceros.save(t));
    }

    /**
     * Consulta por identificador.
     *
     * @param id identificador
     * @return tercero
     */
    @Transactional(readOnly = true)
    public TerceroResponse obtener(Long id) {
        return TerceroResponse.de(buscar(id));
    }

    /**
     * Lista paginada con filtro de texto.
     *
     * @param q texto opcional
     * @param pageable paginación
     * @return página de terceros
     */
    @Transactional(readOnly = true)
    public PaginaResponse<TerceroResponse> listar(String q, Pageable pageable) {
        return PaginaResponse.de(terceros.buscar(q == null ? "" : q.trim(), pageable), TerceroResponse::de);
    }

    /**
     * Elimina un tercero sin documentos asociados; si tiene historial la base de datos lo impide (409).
     *
     * @param id identificador
     */
    @Transactional
    public void eliminar(Long id) {
        buscar(id);
        terceros.deleteById(id);
        terceros.flush();
    }

    private Tercero buscar(Long id) {
        return terceros.findById(id).orElseThrow(() -> ApiException.noEncontrado("Tercero"));
    }

    private void validarIdentificacion(String numero, Long idActual) {
        terceros.findByNumeroIdentificacion(numero).ifPresent(otro -> {
            if (!otro.getId().equals(idActual)) {
                throw ApiException.conflicto("IDENTIFICACION_DUPLICADA", "error.identificacion.duplicada", numero);
            }
        });
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void aplicar(Tercero t, TerceroRequest r) {
        t.setTipoPersona(r.tipoPersona());
        t.setTipoIdentificacion(r.tipoIdentificacion());
        t.setNumeroIdentificacion(r.numeroIdentificacion().trim());
        boolean natural = r.tipoPersona() == TipoPersona.NATURAL;
        t.setPrimerNombre(natural ? limpiar(r.primerNombre()) : null);
        t.setSegundoNombre(natural ? limpiar(r.segundoNombre()) : null);
        t.setPrimerApellido(natural ? limpiar(r.primerApellido()) : null);
        t.setSegundoApellido(natural ? limpiar(r.segundoApellido()) : null);
        t.setRazonSocial(natural ? null : limpiar(r.razonSocial()));
        t.setDireccion(r.direccion().trim());
        t.setCiudad(r.ciudad().trim());
        t.setTelefono(r.telefono().trim());
        t.setCorreo(r.correo().trim());
        t.setCliente(r.cliente());
        t.setProveedor(r.proveedor());
    }
}
