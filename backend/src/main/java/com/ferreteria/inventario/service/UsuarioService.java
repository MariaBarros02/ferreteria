/*
 * Nombre: UsuarioService.java
 * Descripción: Administración de usuarios y roles (RF-09): alta, cambio de rol y estado, restablecimiento de contraseña con política mínima, protección del último administrador y auditoría de cambios de permisos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.domain.model.Permiso;
import com.ferreteria.inventario.domain.model.Rol;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.RolResponse;
import com.ferreteria.inventario.dto.UsuarioRequest;
import com.ferreteria.inventario.dto.UsuarioResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.PermisoRepository;
import com.ferreteria.inventario.repository.RolRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las contraseñas se guardan solo como hash BCrypt y nunca se registran en la auditoría. Un administrador no
 * puede desactivarse ni quitarse el rol a sí mismo, y siempre debe quedar al menos un administrador activo.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    /** Política mínima: 10 a 128 caracteres con al menos una mayúscula, una minúscula y un número. */
    static final Pattern POLITICA = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{10,128}$");
    private static final String ADMIN = "Administrador";

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final PasswordEncoder encoder;
    private final EventoAuditoriaService eventos;

    /**
     * Listado paginado.
     *
     * @param q texto (nombre o correo)
     * @param pagina página
     * @param tamano tamaño
     * @return página de usuarios
     */
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(String q, int pagina, int tamano) {
        return PaginaResponse.de(usuarios.buscar(q == null ? "" : q.trim(), PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), 100), Sort.by("nombre"))), UsuarioResponse::de);
    }

    /**
     * Crea un usuario.
     *
     * @param req datos (con contraseña inicial)
     * @param actor administrador
     * @return usuario creado
     */
    @Transactional
    public UsuarioResponse crear(UsuarioRequest req, Actor actor) {
        String correo = req.correo().trim().toLowerCase();
        if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
            throw ApiException.conflicto("CORREO_DUPLICADO", "error.correo.duplicado", correo);
        }
        validarClave(req.password());
        Rol rol = rol(req.rol());
        Usuario u = usuarios.save(Usuario.builder().nombre(req.nombre().trim()).correo(correo)
                .passwordHash(encoder.encode(req.password())).rol(rol).estado(req.estado()).build());
        eventos.registrar(actor, Auditables.USUARIOS, Auditables.CREAR, "USUARIO", u.getId(),
                "Usuario creado: " + correo + " con rol " + rol.getNombre(),
                Map.of("correo", correo, "rol", rol.getNombre(), "estado", req.estado().getValor()));
        return UsuarioResponse.de(u);
    }

    /**
     * Actualiza nombre, rol y estado (no la contraseña).
     *
     * @param id usuario
     * @param req datos
     * @param actor administrador
     * @return usuario actualizado
     */
    @Transactional
    public UsuarioResponse actualizar(Integer id, UsuarioRequest req, Actor actor) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        String correo = req.correo().trim().toLowerCase();
        usuarios.findByCorreoIgnoreCase(correo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflicto("CORREO_DUPLICADO", "error.correo.duplicado", correo);
        });
        Rol nuevoRol = rol(req.rol());
        boolean eraAdminActivo = ADMIN.equalsIgnoreCase(u.getRol().getNombre()) && u.getEstado() == EstadoCuenta.ACTIVO;
        boolean seguiraAdminActivo = ADMIN.equalsIgnoreCase(nuevoRol.getNombre()) && req.estado() == EstadoCuenta.ACTIVO;
        if (eraAdminActivo && !seguiraAdminActivo) {
            if (u.getId().equals(actor.idUsuario())) {
                throw ApiException.conflicto("AUTO_DEGRADACION", "error.usuario.propio");
            }
            if (usuarios.countByRolNombreIgnoreCaseAndEstado(ADMIN, EstadoCuenta.ACTIVO) <= 1) {
                throw ApiException.conflicto("ULTIMO_ADMINISTRADOR", "error.usuario.ultimo.admin");
            }
        }
        Map<String, Object> antes = resumen(u);
        String rolAnterior = u.getRol().getNombre();
        EstadoCuenta estadoAnterior = u.getEstado();
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setRol(nuevoRol);
        u.setEstado(req.estado());
        u = usuarios.save(u);
        String accion = !rolAnterior.equals(nuevoRol.getNombre()) ? Auditables.CAMBIO_ROL
                : estadoAnterior != req.estado() ? (req.estado() == EstadoCuenta.ACTIVO ? Auditables.ACTIVAR : Auditables.DESACTIVAR)
                : Auditables.ACTUALIZAR;
        eventos.registrar(actor, Auditables.USUARIOS, accion, "USUARIO", u.getId(), "Usuario actualizado: " + correo,
                Map.of("antes", antes, "despues", resumen(u)));
        return UsuarioResponse.de(u);
    }

    /**
     * Restablece la contraseña de un usuario.
     *
     * @param id usuario
     * @param password nueva contraseña
     * @param actor administrador
     */
    @Transactional
    public void restablecerClave(Integer id, String password, Actor actor) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        validarClave(password);
        u.setPasswordHash(encoder.encode(password));
        usuarios.save(u);
        eventos.registrar(actor, Auditables.USUARIOS, Auditables.RESTABLECER_CLAVE, "USUARIO", u.getId(),
                "Contraseña restablecida para " + u.getCorreo(), null);
    }

    /**
     * Roles con su matriz de permisos.
     *
     * @return roles
     */
    @Transactional(readOnly = true)
    public List<RolResponse> roles() {
        return roles.findAll(Sort.by("id")).stream().map(r -> {
            Map<String, String> m = new LinkedHashMap<>();
            for (Permiso p : permisos.findByIdRolOrderByModuloAsc(r.getId())) {
                m.put(p.getModulo(), p.getNivelAcceso());
            }
            return new RolResponse(r.getId(), r.getNombre(), m);
        }).toList();
    }

    private Rol rol(String nombre) {
        return roles.findByNombreIgnoreCase(nombre.trim())
                .orElseThrow(() -> ApiException.regla("ROL_INVALIDO", "error.rol.invalido", nombre));
    }

    /**
     * Valida la política de contraseñas.
     *
     * @param password contraseña
     */
    static void validarClave(String password) {
        if (password == null || !POLITICA.matcher(password).matches()) {
            throw ApiException.regla("CLAVE_DEBIL", "error.clave.debil");
        }
    }

    private static Map<String, Object> resumen(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", u.getNombre());
        m.put("correo", u.getCorreo());
        m.put("rol", u.getRol().getNombre());
        m.put("estado", u.getEstado().getValor());
        return m;
    }
}
