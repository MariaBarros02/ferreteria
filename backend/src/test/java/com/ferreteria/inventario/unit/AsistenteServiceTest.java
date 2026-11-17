/*
 * Nombre: AsistenteServiceTest.java
 * Descripción: Pruebas unitarias del asistente virtual con la base de conocimiento real: bienvenida por rol, preguntas frecuentes, avisos de permiso, enlaces permitidos, consultas de precio y existencias (por código o nombre), tasas de cambio, aclaraciones, idioma inglés y errores.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.dto.AsistenteMensajeRequest;
import com.ferreteria.inventario.dto.AsistenteRespuesta;
import com.ferreteria.inventario.dto.MonedaResponse;
import com.ferreteria.inventario.dto.ProductoSeleccionResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.service.AsistenteService;
import com.ferreteria.inventario.service.MonedaService;
import com.ferreteria.inventario.service.ProductoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsistenteServiceTest {

    private static final String TALADRO_COD = "7701234500015";

    @Mock private ProductoService productos;
    @Mock private MonedaService monedas;

    private AsistenteService servicio;

    @BeforeEach
    void setUp() {
        // Igual que la aplicación: el ObjectMapper rechaza campos desconocidos; el servicio usa una copia permisiva.
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        servicio = new AsistenteService(mapper, productos, monedas);
    }

    private static ProductoSeleccionResponse taladro() {
        return new ProductoSeleccionResponse(1L, "Taladro Percutor", "Bosch", "650W", TALADRO_COD, "unidad",
                TipoRastreo.SERIE, false, true, new BigDecimal("19"), new BigDecimal("450000.00"), new BigDecimal("12.0000"),
                new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("3"), List.of());
    }

    private static ProductoSeleccionResponse tornillo() {
        return new ProductoSeleccionResponse(2L, "Tornillo Drywall", "Ferrolux", "Caja x 100", "7701234500022", "caja",
                TipoRastreo.NINGUNO, false, true, BigDecimal.ZERO, null, new BigDecimal("1.5"), BigDecimal.ZERO,
                new BigDecimal("0.5"), new BigDecimal("5"), List.of());
    }

    private static List<MonedaResponse> monedasRegistradas() {
        return List.of(
                new MonedaResponse("COP", "Peso colombiano", "$", (short) 0, true, true, BigDecimal.ONE, null, null),
                new MonedaResponse("USD", "Dólar estadounidense", "US$", (short) 2, true, false, new BigDecimal("4012.5"),
                        LocalDate.of(2026, 11, 10), "TRM Banco de la República"),
                new MonedaResponse("EUR", "Euro", "€", (short) 2, true, false, null, null, null),
                new MonedaResponse("BRL", "Real brasileño", "R$", (short) 2, false, false, null, null, null));
    }

    private AsistenteRespuesta preguntar(String texto, String rol, String idioma) {
        return servicio.responder(new AsistenteMensajeRequest(texto, null), rol, idioma);
    }

    private AsistenteRespuesta tema(String nodo, String rol, String idioma) {
        return servicio.responder(new AsistenteMensajeRequest(null, nodo), rol, idioma);
    }

    private static List<String> ids(AsistenteRespuesta r) {
        return r.opciones().stream().map(AsistenteRespuesta.Opcion::id).toList();
    }

    // ------------------------------------------------------------------ base y bienvenida

    @Test @DisplayName("UT-ASI-01 la base de conocimiento real carga y es válida (aunque el ObjectMapper rechace campos desconocidos)")
    void baseReal() {
        assertThat(servicio.motor().version()).isEqualTo("1.0.0");
        assertThat(servicio.motor().nodo("saludo")).isPresent();
        assertThat(servicio.motor().nodo("venta-registrar")).isPresent();
    }

    @Test @DisplayName("UT-ASI-02 bienvenida con los temas permitidos para el rol, sin temas ocultos")
    void bienvenidaPorRol() {
        AsistenteRespuesta ventas = servicio.inicio("VENTAS", "es");
        assertThat(ventas.tipo()).isEqualTo(AsistenteRespuesta.BIENVENIDA);
        assertThat(ventas.nodo()).isEqualTo("saludo");
        assertThat(ventas.texto()).startsWith("¡Hola! Soy el asistente virtual");
        assertThat(ids(ventas)).containsExactly("ventas", "compras", "inventario", "devoluciones", "monedas", "importacion",
                "cuenta", "consulta-productos");
        assertThat(ventas.enlace()).isNull();
        assertThat(ids(servicio.inicio("GERENCIA", "es"))).contains("precios", "cierres-auditoria")
                .doesNotContain("saludo", "gracias");
    }

    @Test @DisplayName("UT-ASI-03 bienvenida y títulos en inglés")
    void bienvenidaIngles() {
        AsistenteRespuesta r = servicio.inicio("VENTAS", "en");
        assertThat(r.texto()).startsWith("Hi! I am the hardware store's virtual assistant");
        assertThat(r.opciones().get(0)).isEqualTo(new AsistenteRespuesta.Opcion("ventas", "Sales"));
    }

    // ------------------------------------------------------------------ preguntas frecuentes y permisos

    @Test @DisplayName("UT-ASI-04 pregunta frecuente con enlace a la pantalla y sin aviso de permiso para el rol autorizado")
    void preguntaFrecuente() {
        AsistenteRespuesta r = preguntar("¿Cómo registro una venta?", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.RESPUESTA);
        assertThat(r.nodo()).isEqualTo("venta-registrar");
        assertThat(r.texto()).startsWith("1. Abre Ventas → Nueva venta.").doesNotContain("Ten en cuenta");
        assertThat(r.enlace()).isEqualTo(new AsistenteRespuesta.Enlace("/ventas/nueva", "Abrir Nueva venta"));
        verifyNoInteractions(productos, monedas);
    }

    @Test @DisplayName("UT-ASI-05 rol sin permiso: misma explicación, aviso con los roles autorizados y sin enlace")
    void sinPermiso() {
        AsistenteRespuesta r = preguntar("¿Cómo registro una venta?", "GERENCIA", "es");
        assertThat(r.nodo()).isEqualTo("venta-registrar");
        assertThat(r.texto()).contains("Ten en cuenta: esta operación la realizan los roles Ventas y Administrador. "
                + "Tu rol no tiene permiso para hacerla");
        assertThat(r.enlace()).isNull();
    }

    @Test @DisplayName("UT-ASI-06 aviso en singular para un único rol y en inglés")
    void avisoSingularEIngles() {
        assertThat(tema("precio-aprobar", "VENTAS", "es").texto())
                .contains("Ten en cuenta: esta operación la realiza el rol Gerencia.");
        assertThat(tema("precio-aprobar", "VENTAS", "en").texto())
                .contains("Please note: this operation is performed by the Management role.");
        assertThat(tema("venta-registrar", "GERENCIA", "en").texto())
                .contains("performed by the Sales and Administrator roles.");
        assertThat(tema("precios", "INVENTARIO", "es").texto()).doesNotContain("Ten en cuenta");
    }

    @Test @DisplayName("UT-ASI-07 tema con subtemas: opciones en orden; un tema sin permiso solo informa y oculta su enlace")
    void temaConSubtemas() {
        AsistenteRespuesta r = tema("ventas", "VENTAS", "es");
        assertThat(ids(r)).containsExactly("venta-registrar", "venta-lector", "venta-descuento", "venta-comprobante",
                "venta-moneda", "venta-saldo", "venta-corregir");
        assertThat(r.enlace().ruta()).isEqualTo("/ventas");
        AsistenteRespuesta precios = tema("precios", "VENTAS", "es");
        assertThat(precios.texto()).doesNotContain("Ten en cuenta");
        assertThat(precios.enlace()).isNull();
        assertThat(tema("importacion", "GERENCIA", "es").enlace()).isNull();          // el enlace tiene sus propios roles
        assertThat(tema("importacion", "COMPRAS", "es").enlace().ruta()).isEqualTo("/importacion");
    }

    @Test @DisplayName("UT-ASI-08 el tema elegido tiene prioridad sobre el texto; tema inexistente → 404")
    void temaElegido() {
        AsistenteRespuesta r = servicio.responder(new AsistenteMensajeRequest("precio del taladro", "cuenta-bloqueo"),
                "VENTAS", "es");
        assertThat(r.nodo()).isEqualTo("cuenta-bloqueo");
        verifyNoInteractions(productos);
        assertThatThrownBy(() -> tema("no-existe", "VENTAS", "es"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-ASI-09 mensaje vacío (nulo, en blanco o tema en blanco) → 422 MENSAJE_VACIO")
    void mensajeVacio() {
        for (AsistenteMensajeRequest req : List.of(new AsistenteMensajeRequest(null, null),
                new AsistenteMensajeRequest("   ", null), new AsistenteMensajeRequest("  ", "  "))) {
            assertThatThrownBy(() -> servicio.responder(req, "VENTAS", "es"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("MENSAJE_VACIO"));
        }
    }

    @Test @DisplayName("UT-ASI-10 saludo y agradecimiento; sin coincidencia se sugieren los temas principales")
    void saludoGraciasYNoEntendido() {
        AsistenteRespuesta hola = preguntar("Hola, buenos días", "VENTAS", "es");
        assertThat(hola.tipo()).isEqualTo(AsistenteRespuesta.BIENVENIDA);
        assertThat(ids(hola)).contains("ventas").doesNotContain("precios");
        assertThat(preguntar("muchas gracias", "VENTAS", "es").nodo()).isEqualTo("gracias");
        AsistenteRespuesta nada = preguntar("asdf qwer", "VENTAS", "es");
        assertThat(nada.tipo()).isEqualTo(AsistenteRespuesta.NO_ENTENDIDO);
        assertThat(nada.texto()).startsWith("No encontré una respuesta para eso.");
        assertThat(ids(nada)).isEqualTo(ids(servicio.inicio("VENTAS", "es")));
        assertThat(preguntar("zzz", "VENTAS", "en").texto()).startsWith("I could not find an answer for that.");
    }

    @Test @DisplayName("UT-ASI-11 empate entre temas → aclaración con las opciones empatadas")
    void aclaracion() {
        AsistenteRespuesta r = preguntar("precio del iva", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.ACLARACION);
        assertThat(ids(r)).containsExactly("venta-descuento", "precios");
        assertThat(r.texto()).contains("varios temas");
        verify(productos).buscarParaSeleccion("iva", AsistenteService.MAX_PRODUCTOS + 1);
    }

    @Test @DisplayName("UT-ASI-12 preguntas frecuentes variadas llegan al tema correcto")
    void temasVariados() {
        assertThat(preguntar("me sale saldo insuficiente", "VENTAS", "es").nodo()).isEqualTo("venta-saldo");
        assertThat(preguntar("¿es factura electrónica?", "VENTAS", "es").nodo()).isEqualTo("venta-comprobante");
        assertThat(preguntar("me equivoqué en una venta, ¿la puedo borrar?", "VENTAS", "es").nodo()).isEqualTo("venta-corregir");
        assertThat(preguntar("llegó el pedido del proveedor", "COMPRAS", "es").nodo()).isEqualTo("compra-recibir");
        assertThat(preguntar("olvidé mi contraseña", "VENTAS", "es").nodo()).isEqualTo("cuenta-contrasena");
        assertThat(preguntar("¿qué hago si se va el internet?", "VENTAS", "es").nodo()).isEqualTo("inventario-sin-conexion");
        assertThat(preguntar("I forgot my password", "VENTAS", "en").nodo()).isEqualTo("cuenta-contrasena");
    }

    // ------------------------------------------------------------------ datos: productos

    @Test @DisplayName("UT-ASI-13 código de barras: precio vigente y existencias con formato colombiano")
    void productoPorCodigo() {
        when(productos.seleccionPorCodigo(TALADRO_COD)).thenReturn(taladro());
        AsistenteRespuesta r = preguntar("el código " + TALADRO_COD, "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.PRODUCTO);
        assertThat(r.texto()).isEqualTo("Taladro Percutor · Bosch · 650W (código " + TALADRO_COD + ")\n"
                + "Precio vigente: $ 450.000 COP · Disponible: 12 unidad · Reservado: 2");
        assertThat(r.enlace()).isEqualTo(new AsistenteRespuesta.Enlace("/productos", "Ir a Productos"));
        verify(productos, never()).buscarParaSeleccion(anyString(), anyInt());
    }

    @Test @DisplayName("UT-ASI-14 código inexistente o inactivo → aviso sin error")
    void codigoInexistente() {
        when(productos.seleccionPorCodigo("7709999999999")).thenThrow(ApiException.noEncontrado("Producto"));
        AsistenteRespuesta r = preguntar("7709999999999", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.PRODUCTO);
        assertThat(r.texto()).startsWith("No encontré un producto activo con el código 7709999999999.");
        assertThat(preguntar("7709999999999", "VENTAS", "en").texto()).startsWith("I could not find an active product");
    }

    @Test @DisplayName("UT-ASI-15 búsqueda por nombre: prueba el singular, sin precio, vencido sin baja y bajo el mínimo")
    void productoPorNombre() {
        when(productos.buscarParaSeleccion("tornillo", 4)).thenReturn(List.of(tornillo()));
        AsistenteRespuesta r = preguntar("¿cuántos tornillos hay?", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.PRODUCTO);
        assertThat(r.texto()).isEqualTo("Tornillo Drywall · Ferrolux · Caja x 100 (código 7701234500022)\n"
                + "Sin precio aprobado · Disponible: 1,5 caja · Reservado: 0 · Vencido sin baja: 0,5 · Por debajo del stock mínimo");
        InOrder orden = inOrder(productos);
        orden.verify(productos).buscarParaSeleccion("tornillos", 4);
        orden.verify(productos).buscarParaSeleccion("tornillo", 4);
        assertThat(preguntar("how many tornillos are in stock?", "VENTAS", "en").texto())
                .contains("No approved price · Available: 1.5 caja · Reserved: 0 · Expired, not written off: 0.5 · Below minimum stock");
    }

    @Test @DisplayName("UT-ASI-16 varios resultados: máximo tres, con viñetas y aviso de más coincidencias")
    void variosResultados() {
        when(productos.buscarParaSeleccion("taladro", 4)).thenReturn(List.of(taladro(), taladro(), taladro(), taladro()));
        AsistenteRespuesta r = preguntar("precio del taladro", "VENTAS", "es");
        assertThat(r.texto()).startsWith("Encontré estos productos:\n• Taladro Percutor")
                .endsWith("Hay más coincidencias: búscalas en Productos.");
        assertThat(r.texto().split("• ", -1)).hasSize(4);
    }

    @Test @DisplayName("UT-ASI-17 producto inexistente → aviso con la palabra buscada; un término que es tema no se trata como producto")
    void productoInexistente() {
        AsistenteRespuesta r = preguntar("precio del martillo", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.PRODUCTO);
        assertThat(r.texto()).startsWith("No encontré productos activos que coincidan con «martillo».");
        assertThat(ids(r)).containsExactly("consulta-productos");
        assertThat(preguntar("precio", "VENTAS", "es").nodo()).isEqualTo("precios");   // sin término: tema
        verify(productos, never()).buscarParaSeleccion("precio", 4);
    }

    @Test @DisplayName("UT-ASI-18 las preguntas de procedimiento no consultan datos")
    void procedimientoNoConsulta() {
        assertThat(preguntar("¿cómo registro una tasa de cambio?", "VENTAS", "es").nodo()).isEqualTo("moneda-tasa-registrar");
        assertThat(preguntar("¿cómo propongo un precio?", "INVENTARIO", "es").nodo()).isEqualTo("precio-proponer");
        verifyNoInteractions(productos, monedas);
    }

    // ------------------------------------------------------------------ datos: tasas de cambio

    @Test @DisplayName("UT-ASI-19 tasa del dólar con fecha y fuente, en español y en inglés")
    void tasaDolar() {
        when(monedas.listar(false)).thenReturn(monedasRegistradas());
        AsistenteRespuesta r = preguntar("¿a cuánto está el dólar?", "VENTAS", "es");
        assertThat(r.tipo()).isEqualTo(AsistenteRespuesta.TASA);
        assertThat(r.texto()).isEqualTo("1 USD = 4.012,5 COP (vigente desde 2026-11-10 · fuente: TRM Banco de la República)");
        assertThat(r.enlace().ruta()).isEqualTo("/monedas");
        assertThat(preguntar("what is the dollar rate?", "VENTAS", "en").texto())
                .isEqualTo("1 USD = 4,012.5 COP (effective since 2026-11-10 · source: TRM Banco de la República)");
    }

    @Test @DisplayName("UT-ASI-20 todas las tasas: solo monedas extranjeras activas; sin tasa vigente e inactiva se informan")
    void todasLasTasas() {
        when(monedas.listar(false)).thenReturn(monedasRegistradas());
        assertThat(preguntar("tasa de cambio", "VENTAS", "es").texto()).isEqualTo(
                "1 USD = 4.012,5 COP (vigente desde 2026-11-10 · fuente: TRM Banco de la República)\n"
                + "EUR (Euro) no tiene una tasa de cambio vigente registrada.");
        assertThat(preguntar("tasa del brl", "VENTAS", "es").texto()).isEqualTo("BRL (Real brasileño) está inactiva.");
        assertThat(preguntar("euro", "VENTAS", "en").texto()).isEqualTo("EUR (Euro) has no current exchange rate recorded.");
        when(monedas.listar(false)).thenReturn(monedasRegistradas().subList(0, 1));
        assertThat(preguntar("exchange rates", "VENTAS", "en").texto()).isEqualTo("There are no active foreign currencies recorded.");
    }

    @Test @DisplayName("UT-ASI-21 \"vender en dólares\" es una pregunta de ventas, no de tasa")
    void operacionNoEsTasa() {
        AsistenteRespuesta r = preguntar("quiero vender en dólares", "VENTAS", "es");
        assertThat(r.nodo()).isEqualTo("venta-moneda");
        verify(monedas, never()).listar(false);
    }
}
