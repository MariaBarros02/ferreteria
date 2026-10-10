
/*
 * Nombre: TerceroServiceTest.java
 * Descripción: Pruebas unitarias del servicio de terceros: CRUD, identificación
 * duplicada, normalización de datos y reglas de persona natural/jurídica.
 * Fecha de creación: 2026-10-09
 * Fecha de actualización: 2026-10-09
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.service.TerceroService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Pruebas unitarias de las reglas de negocio de terceros. */
@ExtendWith(MockitoExtension.class)
class TerceroServiceTest {

    @Mock
    TerceroRepository terceros;

    private TerceroService servicio;

    @BeforeEach
    void preparar() {
        servicio = new TerceroService(terceros);
    }

    private TerceroRequest requestNatural(String identificacion) {
        return new TerceroRequest(
                TipoPersona.NATURAL,
                TipoIdentificacion.CC,
                identificacion,
                " Ana ",
                " María ",
                " Pérez ",
                " Gómez ",
                " Empresa ignorada ",
                " Calle 10 ",
                " Cali ",
                "3001234567",
                " ana@example.com ",
                true,
                false);
    }

    private Tercero tercero(Long id, String identificacion) {
        Tercero t = new Tercero();
        t.setId(id);
        t.setTipoPersona(TipoPersona.NATURAL);
        t.setTipoIdentificacion(TipoIdentificacion.CC);
        t.setNumeroIdentificacion(identificacion);
        t.setPrimerNombre("Ana");
        t.setPrimerApellido("Pérez");
        t.setDireccion("Calle 10");
        t.setCiudad("Cali");
        t.setTelefono("3001234567");
        t.setCorreo("ana@example.com");
        t.setCliente(true);
        t.setProveedor(false);
        return t;
    }

    @Test
    @DisplayName("UT-TER-01 crear normaliza campos y limpia los de persona jurídica")
    void crearNormalizaDatos() {
        when(terceros.findByNumeroIdentificacion("123456"))
                .thenReturn(Optional.empty());
        when(terceros.save(any(Tercero.class))).thenAnswer(invocacion -> {
            Tercero t = invocacion.getArgument(0);
            t.setId(10L);
            return t;
        });

        TerceroResponse respuesta = servicio.crear(requestNatural(" 123456 "));

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.numeroIdentificacion()).isEqualTo("123456");
        assertThat(respuesta.primerNombre()).isEqualTo("Ana");
        assertThat(respuesta.segundoNombre()).isEqualTo("María");
        assertThat(respuesta.primerApellido()).isEqualTo("Pérez");
        assertThat(respuesta.direccion()).isEqualTo("Calle 10");
        assertThat(respuesta.ciudad()).isEqualTo("Cali");
        assertThat(respuesta.correo()).isEqualTo("ana@example.com");
        assertThat(respuesta.razonSocial()).isNull();

        ArgumentCaptor<Tercero> captor = ArgumentCaptor.forClass(Tercero.class);
        verify(terceros).save(captor.capture());
        assertThat(captor.getValue().isCliente()).isTrue();
        assertThat(captor.getValue().isProveedor()).isFalse();
    }

    @Test
    @DisplayName("UT-TER-02 rechaza una identificación perteneciente a otro tercero")
    void identificacionDuplicada() {
        when(terceros.findByNumeroIdentificacion("123456"))
                .thenReturn(Optional.of(tercero(99L, "123456")));

        assertThatThrownBy(() -> servicio.crear(requestNatural("123456")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getCodigo())
                                .isEqualTo("IDENTIFICACION_DUPLICADA"));

        verify(terceros, never()).save(any(Tercero.class));
    }

    @Test
    @DisplayName("UT-TER-03 actualizar permite conservar la identificación propia")
    void actualizarConservaIdentificacionPropia() {
        Tercero existente = tercero(10L, "123456");

        when(terceros.findById(10L)).thenReturn(Optional.of(existente));
        when(terceros.findByNumeroIdentificacion("123456"))
                .thenReturn(Optional.of(existente));
        when(terceros.save(any(Tercero.class))).thenAnswer(i -> i.getArgument(0));

        TerceroResponse respuesta =
                servicio.actualizar(10L, requestNatural("123456"));

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.numeroIdentificacion()).isEqualTo("123456");
        verify(terceros).save(existente);
    }

    @Test
    @DisplayName("UT-TER-04 actualizar rechaza identificación de otro tercero")
    void actualizarIdentificacionDeOtroTercero() {
        Tercero existente = tercero(10L, "111111");
        Tercero otro = tercero(20L, "222222");

        when(terceros.findById(10L)).thenReturn(Optional.of(existente));
        when(terceros.findByNumeroIdentificacion("222222"))
                .thenReturn(Optional.of(otro));

        assertThatThrownBy(() ->
                servicio.actualizar(10L, requestNatural("222222")))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getCodigo())
                                .isEqualTo("IDENTIFICACION_DUPLICADA"));

        verify(terceros, never()).save(any(Tercero.class));
    }

    @Test
    @DisplayName("UT-TER-05 obtener devuelve el tercero encontrado")
    void obtenerExistente() {
        when(terceros.findById(10L))
                .thenReturn(Optional.of(tercero(10L, "123456")));

        TerceroResponse respuesta = servicio.obtener(10L);

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.primerNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("UT-TER-06 consultar un tercero inexistente produce 404")
    void obtenerInexistente() {
        when(terceros.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getStatus().value())
                                .isEqualTo(404));
    }

    @Test
    @DisplayName("UT-TER-07 listar usa cadena vacía cuando el filtro es nulo")
    void listarSinFiltro() {
        Page<Tercero> pagina = new PageImpl<>(
                java.util.List.of(tercero(10L, "123456")),
                PageRequest.of(0, 10),
                1);

        when(terceros.buscar("", PageRequest.of(0, 10)))
                .thenReturn(pagina);

        var respuesta = servicio.listar(null, PageRequest.of(0, 10));

        assertThat(respuesta.contenido()).hasSize(1);
        assertThat(respuesta.totalElementos()).isEqualTo(1);
        verify(terceros).buscar("", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("UT-TER-08 listar elimina espacios del filtro")
    void listarNormalizaFiltro() {
        Page<Tercero> pagina = new PageImpl<>(
                java.util.List.of(tercero(10L, "123456")));

        when(terceros.buscar("Ana", PageRequest.of(0, 10)))
                .thenReturn(pagina);

        var respuesta = servicio.listar(" Ana ", PageRequest.of(0, 10));

        assertThat(respuesta.contenido()).hasSize(1);
        verify(terceros).buscar("Ana", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("UT-TER-09 eliminar borra y sincroniza el repositorio")
    void eliminarExistente() {
        when(terceros.findById(10L))
                .thenReturn(Optional.of(tercero(10L, "123456")));

        servicio.eliminar(10L);

        verify(terceros).deleteById(10L);
        verify(terceros).flush();
    }

    @Test
    @DisplayName("UT-TER-10 no elimina si el tercero no existe")
    void eliminarInexistente() {
        when(terceros.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L))
                .isInstanceOf(ApiException.class);

        verify(terceros, never()).deleteById(any());
        verify(terceros, never()).flush();
    }

    @Test
    @DisplayName("UT-TER-11 persona jurídica limpia los campos de persona natural")
    void crearPersonaJuridica() {
        TerceroRequest request = new TerceroRequest(
                TipoPersona.JURIDICA,
                TipoIdentificacion.NIT,
                "900123456",
                "Nombre ignorado",
                "Segundo ignorado",
                "Apellido ignorado",
                "Otro apellido ignorado",
                " Ferretería Central ",
                " Avenida 5 ",
                " Cali ",
                "6021234567",
                "ventas@example.com",
                true,
                true);

        when(terceros.findByNumeroIdentificacion("900123456"))
                .thenReturn(Optional.empty());
        when(terceros.save(any(Tercero.class))).thenAnswer(i -> {
            Tercero t = i.getArgument(0);
            t.setId(30L);
            return t;
        });

        TerceroResponse respuesta = servicio.crear(request);

        assertThat(respuesta.razonSocial()).isEqualTo("Ferretería Central");
        assertThat(respuesta.primerNombre()).isNull();
        assertThat(respuesta.primerApellido()).isNull();
        assertThat(respuesta.cliente()).isTrue();
        assertThat(respuesta.proveedor()).isTrue();
    }
}