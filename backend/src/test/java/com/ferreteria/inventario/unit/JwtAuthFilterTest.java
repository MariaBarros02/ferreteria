
package com.ferreteria.inventario.unit;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.domain.model.Rol;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.security.JwtAuthFilter;
import com.ferreteria.inventario.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private Claims claims;
    @Mock private FilterChain chain;
    @Mock private Rol rol;

    private JwtAuthFilter filtro;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filtro = new JwtAuthFilter(jwtService, usuarioRepository);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinCabeceraAuthorizationContinuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, usuarioRepository);
        verify(chain).doFilter(request, response);
    }

    @Test
    void cabeceraConEsquemaIncorrectoContinuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, usuarioRepository);
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenValidoYUsuarioActivoEstableceAutenticacion() throws Exception {
        Usuario usuario = usuarioActivo();

        when(jwtService.validar("token-valido")).thenReturn(claims);
        when(usuarioRepository.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = response();
        when(claims.getSubject()).thenReturn("ana@example.com");
        when(rol.getNombre()).thenReturn("Administrador");
        filtro.doFilter(request, response, chain);

        var autenticacion = SecurityContextHolder.getContext().getAuthentication();

        assertThat(autenticacion).isNotNull();
        assertThat(autenticacion.getPrincipal()).isEqualTo("ana@example.com");
        assertThat(autenticacion.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMINISTRADOR");

        verify(jwtService).validar("token-valido");
        verify(usuarioRepository).findByCorreoIgnoreCase("ana@example.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenValidoPeroUsuarioInexistenteNoAutentica() throws Exception {
        when(jwtService.validar("token-valido")).thenReturn(claims);
        when(usuarioRepository.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.empty());
        when(claims.getSubject()).thenReturn("ana@example.com");
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenValidoPeroUsuarioInactivoNoAutentica() throws Exception {
        Usuario usuario = usuarioActivo();
        usuario.setEstado(estadoNoActivo());

        when(jwtService.validar("token-valido")).thenReturn(claims);
        when(usuarioRepository.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));
        when(claims.getSubject()).thenReturn("ana@example.com");
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenInvalidoContinuaSinAutenticar() throws Exception {
        when(jwtService.validar("token-invalido"))
                .thenThrow(new MalformedJwtException("Token mal formado"));

        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer token-invalido");
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(usuarioRepository);
        verify(chain).doFilter(request, response);
    }

    @Test
    void autenticacionExistenteEvitaValidarElTokenOtraVez() throws Exception {
        var autenticacionPrevia = new UsernamePasswordAuthenticationToken(
                "usuario-previo",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENTAS")));

        SecurityContextHolder.getContext().setAuthentication(autenticacionPrevia);

        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = response();

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(autenticacionPrevia);

        verifyNoInteractions(jwtService, usuarioRepository);
        verify(chain).doFilter(request, response);
    }

    private Usuario usuarioActivo() {
        return Usuario.builder()
                .id(1)
                .nombre("Ana Pérez")
                .correo("ana@example.com")
                .passwordHash("$2a$hash")
                .rol(rol)
                .estado(EstadoCuenta.ACTIVO)
                .build();
    }

    private EstadoCuenta estadoNoActivo() {
        return Arrays.stream(EstadoCuenta.values())
                .filter(estado -> estado != EstadoCuenta.ACTIVO)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "EstadoCuenta debe tener al menos un estado distinto de ACTIVO"));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/productos");
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}