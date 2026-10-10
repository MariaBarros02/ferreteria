/*
 * Nombre: BaseIT.java
 * Descripción: Base de las pruebas de integración: PostgreSQL real con Testcontainers, Flyway, MockMvc y utilidades de datos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.domain.enums.*;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.security.JwtService;
import com.ferreteria.inventario.security.Roles;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Levanta un único contenedor PostgreSQL compartido por todas las clases
 * {@code *IT}.
 * Requiere Docker disponible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIT {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static {
        PG.start();
    }

    private static final AtomicLong SECUENCIA = new AtomicLong(7_800_000_000_000L);
    private static final AtomicLong SECUENCIA_ID = new AtomicLong(1_000_000L);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        String url = PG.getJdbcUrl();
        r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified");
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UsuarioRepository usuarios;
    @Autowired
    protected RolRepositoryHelper roles;
    @Autowired
    protected ProductoRepository productos;
    @Autowired
    protected InventarioRepository inventarios;
    @Autowired
    protected TerceroRepository terceros;
    @Autowired
    protected PrecioRepository precios;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected JwtService jwt;

    /** Clave de pruebas común. */
    protected static final String CLAVE = "Clave-Segura-123";

    /** Crea (si no existe) un usuario con el rol dado y devuelve un JWT válido. */
    protected String tokenPara(String rolNombre) {
        String correo = rolNombre.toLowerCase() + "@test.com";
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElseGet(() -> usuarios.save(Usuario.builder()
                .nombre("Usuario " + rolNombre).correo(correo).passwordHash(encoder.encode(CLAVE))
                .rol(roles.porNombre(rolNombre)).estado(EstadoCuenta.ACTIVO).build()));
        return jwt.generar(u.getCorreo(), Roles.normalizar(u.getRol().getNombre()));
    }

    /** Crea un usuario con correo explícito o reutiliza el existente. */
    protected Usuario usuario(String correo, String rol, EstadoCuenta estado) {
        return usuarios.findByCorreoIgnoreCase(correo).map(usuarioExistente -> {
            usuarioExistente.setRol(roles.porNombre(rol));
            usuarioExistente.setEstado(estado);
            return usuarios.save(usuarioExistente);
        }).orElseGet(() -> usuarios.save(Usuario.builder().nombre("U " + correo).correo(correo)
                .passwordHash(encoder.encode(CLAVE)).rol(roles.porNombre(rol)).estado(estado).build()));
    }

    /** Crea un producto con saldo y, opcionalmente, precio vigente aprobado. */
    protected Producto productoConStock(String stock, String precioVigente) {
        long n = SECUENCIA.incrementAndGet();
        Producto p = productos.save(Producto.builder().nombre("Prod" + n).marca("Marca").presentacion("P" + n)
                .codigoBarras(String.valueOf(n)).unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build());
        inventarios.save(Inventario.builder().idProducto(p.getId()).disponible(new BigDecimal(stock))
                .reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build());
        if (precioVigente != null) {
            BigDecimal valor = new BigDecimal(precioVigente);

            precios.save(Precio.builder()
                    .idProducto(p.getId())
                    .costoPromedio(BigDecimal.TEN)
                    .porcentajeIncremento(new BigDecimal("0.3"))
                    .precioPropuesto(valor)
                    .precioVigente(valor)
                    .estadoAprobacion("aprobado")
                    .build());
        }
        return p;
    }

    /** Crea un cliente persona natural. */
    protected Tercero cliente() {
        long n = SECUENCIA_ID.incrementAndGet();
        return terceros
                .save(Tercero.builder().tipoPersona(TipoPersona.NATURAL).tipoIdentificacion(TipoIdentificacion.CC)
                        .numeroIdentificacion("CC" + n).primerNombre("Juan").primerApellido("Pérez").direccion("Cra 1")
                        .ciudad("Cali").telefono("3001234567").correo("j" + n + "@x.com").cliente(true).build());
    }

    /** Código de barras único válido de 13 dígitos. */
    protected String codigoUnico() {
        return String.valueOf(SECUENCIA.incrementAndGet());
    }

    /** Saldo disponible actual (lectura directa a la BD). */
    protected BigDecimal disponible(Long idProducto) {
        return jdbc.queryForObject("select disponible from inventario where id_producto = ?", BigDecimal.class,
                idProducto);
    }
}
