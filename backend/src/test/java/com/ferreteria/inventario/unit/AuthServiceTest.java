
package com.ferreteria.inventario.unit;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.domain.model.Rol;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.LoginRequest;
import com.ferreteria.inventario.dto.LoginResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.security.JwtService;
import com.ferreteria.inventario.security.LoginAttemptService;
import com.ferreteria.inventario.security.Roles;
import com.ferreteria.inventario.service.AuthService;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwt;
    @Mock private LoginAttemptService intentos;
    @Mock private Rol rol;

    private AuthService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new AuthService(usuarios, encoder, jwt, intentos);


        usuario = Usuario.builder()
                .id(1)
                .nombre("Ana Pérez")
                .correo("ana@example.com")
                .passwordHash("$2a$hash")
                .rol(rol)
                .estado(EstadoCuenta.ACTIVO)
                .build();
    }

    @Test
    void loginExitosoDevuelveTokenYRegistraExito() {
        when(intentos.estaBloqueado("ana@example.com"))
                .thenReturn(false);

        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));

        when(encoder.matches("Clave123!", "$2a$hash"))
                .thenReturn(true);

        // Configura el nombre del rol para evitar el NullPointerException.
        when(rol.getNombre()).thenReturn("Administrador");

        when(jwt.generar("ana@example.com", Roles.ADMINISTRADOR))
                .thenReturn("jwt-valido");

        when(jwt.getVigenciaSegundos()).thenReturn(1800L);

        LoginResponse respuesta = servicio.login(
                new LoginRequest(" ana@example.com ", "Clave123!"));
        assertThat(respuesta.token()).isEqualTo("jwt-valido");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(1800L);
        assertThat(respuesta.usuario().correo()).isEqualTo("ana@example.com");
        assertThat(respuesta.usuario().rol()).isEqualTo("Administrador");

        verify(intentos).registrarExito("ana@example.com");
        verify(intentos, never()).registrarFallo(anyString());
    }

    @Test
    void loginRechazaCuentaBloqueadaAntesDeBuscarUsuario() {
        when(intentos.estaBloqueado("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                servicio.login(new LoginRequest("ana@example.com", "Clave123!")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verifyNoInteractions(usuarios, encoder, jwt);
        verify(intentos, never()).registrarFallo(anyString());
    }

    @Test
    void loginRechazaUsuarioInexistenteYRegistraFallo() {
        when(intentos.estaBloqueado("nadie@example.com")).thenReturn(false);
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio.login(new LoginRequest("nadie@example.com", "Clave123!")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(intentos).registrarFallo("nadie@example.com");
        verifyNoInteractions(encoder, jwt);
    }

    @Test
    void loginRechazaContrasenaIncorrectaYRegistraFallo() {
        when(intentos.estaBloqueado("ana@example.com")).thenReturn(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));
        when(encoder.matches("incorrecta", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() ->
                servicio.login(new LoginRequest("ana@example.com", "incorrecta")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(intentos).registrarFallo("ana@example.com");
        verify(intentos, never()).registrarExito(anyString());
        verifyNoInteractions(jwt);
    }

    @Test
    void loginRechazaCuentaInactiva() {
        usuario.setEstado(estadoNoActivo());

        when(intentos.estaBloqueado("ana@example.com")).thenReturn(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));
        when(encoder.matches("Clave123!", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() ->
                servicio.login(new LoginRequest("ana@example.com", "Clave123!")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(intentos, never()).registrarExito(anyString());
        verifyNoInteractions(jwt);
    }

        @Test
        void renovarGeneraNuevoTokenParaUsuarioActivo() {
        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));

        // Configura el nombre del rol.
        when(rol.getNombre()).thenReturn("Administrador");

        when(jwt.generar("ana@example.com", Roles.ADMINISTRADOR))
                .thenReturn("jwt-renovado");

        when(jwt.getVigenciaSegundos()).thenReturn(900L);

        LoginResponse respuesta = servicio.renovar("ana@example.com");

        assertThat(respuesta.token()).isEqualTo("jwt-renovado");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(900L);

        verify(jwt).generar("ana@example.com", Roles.ADMINISTRADOR);
        }

    @Test
    void renovarRechazaUsuarioInexistente() {
        when(usuarios.findByCorreoIgnoreCase("ausente@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.renovar("ausente@example.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(jwt);
    }

    @Test
    void renovarRechazaUsuarioInactivo() {
        usuario.setEstado(estadoNoActivo());

        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.renovar("ana@example.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(jwt);
    }

    private EstadoCuenta estadoNoActivo() {
        return Arrays.stream(EstadoCuenta.values())
                .filter(estado -> estado != EstadoCuenta.ACTIVO)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "EstadoCuenta debe tener al menos un estado distinto de ACTIVO"));
    }
}