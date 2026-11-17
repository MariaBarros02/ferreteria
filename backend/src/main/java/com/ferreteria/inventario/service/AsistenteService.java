/*
 * Nombre: AsistenteService.java
 * Descripción: Asistente virtual de la plataforma: responde preguntas frecuentes con el árbol de decisión determinista (MotorAsistente) y consulta datos reales de solo lectura (precio vigente y existencias de productos, tasas de cambio vigentes). Respeta los permisos del rol: avisa cuando una operación no le corresponde y solo enlaza pantallas permitidas. No guarda las conversaciones.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.dto.AsistenteMensajeRequest;
import com.ferreteria.inventario.dto.AsistenteRespuesta;
import com.ferreteria.inventario.dto.MonedaResponse;
import com.ferreteria.inventario.dto.ProductoSeleccionResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.security.Roles;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asistente virtual basado en reglas deterministas. */
@Service
public class AsistenteService {

    /** Ubicación de la base de conocimiento en el classpath. */
    public static final String RECURSO = "asistente/base-conocimiento.json";
    /** Productos que se muestran como máximo por consulta. */
    public static final int MAX_PRODUCTOS = 3;
    /** Puntaje a partir del cual un tema se prefiere sobre la consulta de datos (frase de dos o más palabras). */
    static final double PUNTAJE_TEMA_ESPECIFICO = 2.0;

    private static final String NODO_SALUDO = "saludo";
    private static final String NODO_CONSULTA = "consulta-productos";
    private static final String RUTA_PRODUCTOS = "/productos";
    private static final String RUTA_MONEDAS = "/monedas";
    private static final Pattern CODIGO_BARRAS = Pattern.compile("(?<!\\d)(\\d{8,14})(?!\\d)");
    private static final double EMPATE = 0.001;

    /** Verbos de procedimiento: la consulta pide "cómo hacer algo", no un dato. */
    private static final Set<String> PROCEDIMIENTO = Set.of("como", "registrar", "registro", "registra", "crear", "creo",
            "agregar", "agrego", "hago", "hacer", "aprobar", "apruebo", "aprueba", "proponer", "propongo", "propone",
            "configurar", "register", "record", "create", "add", "approve", "propose", "configure");
    /** Operaciones: "vender en dólares" pregunta por la venta, no por la tasa. */
    private static final Set<String> OPERACION = Set.of("vender", "vendo", "venta", "ventas", "comprar", "compra",
            "compras", "facturar", "sell", "sale", "sales", "buy", "purchase", "invoice");
    /** Palabras que piden un dato de producto. */
    private static final Set<String> DATO_PRODUCTO = Set.of("precio", "precios", "cuesta", "cuestan", "vale", "valen",
            "valor", "stock", "existencia", "existencias", "disponible", "disponibles", "disponibilidad", "hay", "quedan",
            "queda", "cuanto", "cuantos", "cuantas", "inventario", "price", "prices", "cost", "costs", "available",
            "availability", "many", "much", "left", "inventory");
    /** Palabras genéricas que no forman parte del nombre buscado. */
    private static final Set<String> GENERICAS = Set.of("producto", "productos", "unidad", "unidades", "actual", "vigente",
            "tenemos", "tienen", "tiene", "hoy", "esta", "estan", "item", "items", "product", "products", "unit", "units",
            "current", "there", "have", "has", "today", "much", "many", "consultar", "check", "dime", "tell", "show",
            "muestrame", "saber", "know", "how", "which", "cual", "cuales", "where", "donde");
    /** Palabras que piden una tasa de cambio. */
    private static final Set<String> DATO_TASA = Set.of("tasa", "tasas", "trm", "cotizacion", "rate", "rates", "exchange");
    /** Nombres coloquiales de monedas. */
    private static final Map<String, String> ALIAS_MONEDA = Map.ofEntries(
            Map.entry("dolar", "USD"), Map.entry("dolares", "USD"), Map.entry("dollar", "USD"), Map.entry("dollars", "USD"),
            Map.entry("usd", "USD"), Map.entry("euro", "EUR"), Map.entry("euros", "EUR"), Map.entry("eur", "EUR"),
            Map.entry("mexicano", "MXN"), Map.entry("mexicanos", "MXN"), Map.entry("mxn", "MXN"), Map.entry("sol", "PEN"),
            Map.entry("soles", "PEN"), Map.entry("reales", "BRL"), Map.entry("reais", "BRL"), Map.entry("brl", "BRL"));
    private static final Map<String, String> ROLES_ES = Map.of(Roles.VENTAS, "Ventas", Roles.COMPRAS, "Compras",
            Roles.INVENTARIO, "Inventario", Roles.ADMINISTRADOR, "Administrador", Roles.GERENCIA, "Gerencia");
    private static final Map<String, String> ROLES_EN = Map.of(Roles.VENTAS, "Sales", Roles.COMPRAS, "Purchasing",
            Roles.INVENTARIO, "Inventory", Roles.ADMINISTRADOR, "Administrator", Roles.GERENCIA, "Management");

    private final MotorAsistente motor;
    private final ProductoService productos;
    private final MonedaService monedas;

    /**
     * Carga y valida la base de conocimiento al iniciar: si es inválida la aplicación no arranca.
     *
     * @param mapper ObjectMapper de la aplicación (se usa una copia que ignora el comentario "//" del JSON)
     * @param productos consulta de productos (solo lectura)
     * @param monedas consulta de monedas y tasas (solo lectura)
     */
    @Autowired
    public AsistenteService(ObjectMapper mapper, ProductoService productos, MonedaService monedas) {
        this.motor = cargar(mapper);
        this.productos = productos;
        this.monedas = monedas;
    }

    private static MotorAsistente cargar(ObjectMapper mapper) {
        ObjectMapper lector = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = new ClassPathResource(RECURSO).getInputStream()) {
            return new MotorAsistente(lector.readValue(in, BaseConocimiento.class));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la base de conocimiento del asistente: " + RECURSO, e);
        }
    }

    /** @return motor de reglas (versión y estructura de la base) */
    public MotorAsistente motor() {
        return motor;
    }

    /**
     * Mensaje de bienvenida con los temas principales permitidos para el rol.
     *
     * @param rol rol normalizado del usuario
     * @param idioma es/en
     * @return bienvenida
     */
    public AsistenteRespuesta inicio(String rol, String idioma) {
        BaseConocimiento.Nodo saludo = motor.nodo(NODO_SALUDO).orElseThrow();
        return new AsistenteRespuesta(AsistenteRespuesta.BIENVENIDA, saludo.respuestaEn(idioma), NODO_SALUDO,
                opciones(motor.raiz(rol), idioma), null);
    }

    /**
     * Responde un mensaje: un tema elegido, un código de barras, una pregunta frecuente, una consulta de precio o
     * existencias, o una tasa de cambio.
     *
     * @param req mensaje
     * @param rol rol normalizado del usuario
     * @param idioma es/en
     * @return respuesta
     * @throws ApiException 404 si el tema no existe; 422 si el mensaje está vacío
     */
    @Transactional(readOnly = true)
    public AsistenteRespuesta responder(AsistenteMensajeRequest req, String rol, String idioma) {
        if (req.nodo() != null && !req.nodo().isBlank()) {
            return deNodo(motor.nodo(req.nodo()).orElseThrow(() -> ApiException.noEncontrado("Tema")), rol, idioma);
        }
        String texto = req.texto() == null ? "" : req.texto().trim();
        if (texto.isEmpty()) {
            throw ApiException.regla("MENSAJE_VACIO", "asistente.mensaje.vacio");
        }
        Matcher codigo = CODIGO_BARRAS.matcher(texto);
        if (codigo.find()) {
            return porCodigo(codigo.group(1), idioma);
        }
        List<MotorAsistente.Puntaje> puntajes = motor.clasificar(texto, idioma);
        double mejor = puntajes.isEmpty() ? 0 : puntajes.get(0).puntaje();
        if (mejor >= PUNTAJE_TEMA_ESPECIFICO) {
            return deClasificacion(puntajes, rol, idioma);
        }
        List<String> palabras = MotorAsistente.palabras(MotorAsistente.normalizar(texto));
        boolean procedimiento = palabras.stream().anyMatch(PROCEDIMIENTO::contains);
        if (!procedimiento) {
            Optional<AsistenteRespuesta> tasa = tasa(palabras, idioma);
            if (tasa.isPresent()) {
                return tasa.get();
            }
            Optional<AsistenteRespuesta> producto = productoPorNombre(palabras, mejor, idioma);
            if (producto.isPresent()) {
                return producto.get();
            }
        }
        if (mejor >= MotorAsistente.PUNTAJE_MINIMO) {
            return deClasificacion(puntajes, rol, idioma);
        }
        return noEntendido(puntajes, rol, idioma);
    }

    // ------------------------------------------------------------------ árbol de decisión

    private AsistenteRespuesta deClasificacion(List<MotorAsistente.Puntaje> puntajes, String rol, String idioma) {
        double mejor = puntajes.get(0).puntaje();
        List<BaseConocimiento.Nodo> empatados = puntajes.stream()
                .filter(p -> Math.abs(p.puntaje() - mejor) < EMPATE).map(MotorAsistente.Puntaje::nodo).limit(3).toList();
        if (empatados.size() > 1) {
            String t = es(idioma) ? "Tu pregunta puede referirse a varios temas. ¿Cuál de estos buscas?"
                    : "Your question may refer to several topics. Which one are you looking for?";
            return new AsistenteRespuesta(AsistenteRespuesta.ACLARACION, t, null, opciones(empatados, idioma), null);
        }
        return deNodo(empatados.get(0), rol, idioma);
    }

    private AsistenteRespuesta deNodo(BaseConocimiento.Nodo n, String rol, String idioma) {
        if (n.opcionesRaiz()) {
            return new AsistenteRespuesta(AsistenteRespuesta.BIENVENIDA, n.respuestaEn(idioma), n.id(),
                    opciones(motor.raiz(rol), idioma), null);
        }
        StringBuilder texto = new StringBuilder(n.respuestaEn(idioma));
        if (!motor.permitido(n, rol) && n.opciones().isEmpty()) {   // los temas con subtemas solo informan
            boolean uno = n.roles().size() == 1;
            texto.append(es(idioma)
                    ? "\n\nTen en cuenta: esta operación la realiza" + (uno ? " el rol " : "n los roles ") + roles(n.roles(), idioma)
                      + ". Tu rol no tiene permiso para hacerla; si la necesitas, consulta con el Administrador."
                    : "\n\nPlease note: this operation is performed by the " + roles(n.roles(), idioma)
                      + (uno ? " role" : " roles") + ". Your role is not allowed to do it; if you need it, contact the Administrator.");
        }
        AsistenteRespuesta.Enlace enlace = motor.enlacePermitido(n, rol)
                ? new AsistenteRespuesta.Enlace(n.enlace().ruta(), n.enlace().textoEn(idioma)) : null;
        return new AsistenteRespuesta(AsistenteRespuesta.RESPUESTA, texto.toString(), n.id(),
                opciones(motor.hijos(n), idioma), enlace);
    }

    private AsistenteRespuesta noEntendido(List<MotorAsistente.Puntaje> puntajes, String rol, String idioma) {
        List<BaseConocimiento.Nodo> sugeridos = puntajes.stream().map(MotorAsistente.Puntaje::nodo)
                .filter(n -> !n.oculto()).limit(3).toList();
        String t = es(idioma)
                ? "No encontré una respuesta para eso. Prueba con otras palabras, escribe el código de barras de un producto o elige un tema:"
                : "I could not find an answer for that. Try other words, type a product barcode or pick a topic:";
        return new AsistenteRespuesta(AsistenteRespuesta.NO_ENTENDIDO, t, null,
                opciones(sugeridos.isEmpty() ? motor.raiz(rol) : sugeridos, idioma), null);
    }

    private List<AsistenteRespuesta.Opcion> opciones(List<BaseConocimiento.Nodo> nodos, String idioma) {
        return nodos.stream().filter(n -> !n.oculto())
                .map(n -> new AsistenteRespuesta.Opcion(n.id(), n.tituloEn(idioma))).toList();
    }

    // ------------------------------------------------------------------ datos: productos

    private AsistenteRespuesta porCodigo(String codigo, String idioma) {
        try {
            ProductoSeleccionResponse p = productos.seleccionPorCodigo(codigo);
            return respuestaProductos(List.of(p), false, idioma);
        } catch (ApiException e) {
            String t = es(idioma)
                    ? "No encontré un producto activo con el código " + codigo + ". Verifica el número o búscalo por su nombre."
                    : "I could not find an active product with barcode " + codigo + ". Check the number or search by name.";
            return new AsistenteRespuesta(AsistenteRespuesta.PRODUCTO, t, null, List.of(),
                    new AsistenteRespuesta.Enlace(RUTA_PRODUCTOS, es(idioma) ? "Ir a Productos" : "Go to Products"));
        }
    }

    private Optional<AsistenteRespuesta> productoPorNombre(List<String> palabras, double puntajeTema, String idioma) {
        if (palabras.stream().noneMatch(DATO_PRODUCTO::contains)) {
            return Optional.empty();
        }
        List<String> termino = palabras.stream()
                .filter(p -> !DATO_PRODUCTO.contains(p) && !GENERICAS.contains(p) && p.length() >= 3).toList();
        if (termino.isEmpty()) {
            return Optional.empty();
        }
        List<ProductoSeleccionResponse> encontrados = buscar(termino);
        if (!encontrados.isEmpty()) {
            boolean hayMas = encontrados.size() > MAX_PRODUCTOS;
            return Optional.of(respuestaProductos(encontrados.subList(0, Math.min(MAX_PRODUCTOS, encontrados.size())),
                    hayMas, idioma));
        }
        String buscado = String.join(" ", termino);
        if (puntajeTema >= MotorAsistente.PUNTAJE_MINIMO && !motor.clasificar(buscado, idioma).isEmpty()) {
            return Optional.empty();   // el término es un tema de ayuda ("precio sin aprobar"), no un producto
        }
        String t = es(idioma)
                ? "No encontré productos activos que coincidan con «" + buscado + "». Prueba con otro nombre, la marca o el código de barras."
                : "I could not find active products matching \"" + buscado + "\". Try another name, the brand or the barcode.";
        return Optional.of(new AsistenteRespuesta(AsistenteRespuesta.PRODUCTO, t, null,
                opciones(motor.nodo(NODO_CONSULTA).stream().toList(), idioma),
                new AsistenteRespuesta.Enlace(RUTA_PRODUCTOS, es(idioma) ? "Ir a Productos" : "Go to Products")));
    }

    /**
     * Busca primero la frase completa y después cada palabra (de la más larga a la más corta), también en singular.
     */
    private List<ProductoSeleccionResponse> buscar(List<String> termino) {
        Set<String> intentos = new LinkedHashSet<>();
        intentos.add(String.join(" ", termino));
        termino.stream().sorted((a, b) -> b.length() - a.length()).forEach(p -> {
            intentos.add(p);
            if (p.endsWith("es") && p.length() > 4) {
                intentos.add(p.substring(0, p.length() - 2));
            }
            if (p.endsWith("s") && p.length() > 3) {
                intentos.add(p.substring(0, p.length() - 1));
            }
        });
        for (String q : intentos) {
            List<ProductoSeleccionResponse> r = productos.buscarParaSeleccion(q, MAX_PRODUCTOS + 1);
            if (!r.isEmpty()) {
                return r;
            }
        }
        return List.of();
    }

    private AsistenteRespuesta respuestaProductos(List<ProductoSeleccionResponse> lista, boolean hayMas, String idioma) {
        boolean es = es(idioma);
        StringBuilder t = new StringBuilder();
        if (lista.size() > 1) {
            t.append(es ? "Encontré estos productos:" : "I found these products:").append('\n');
        }
        for (ProductoSeleccionResponse p : lista) {
            if (lista.size() > 1) {
                t.append("• ");
            }
            t.append(p.nombre()).append(" · ").append(p.marca()).append(" · ").append(p.presentacion())
                    .append(es ? " (código " : " (barcode ").append(p.codigoBarras()).append(")\n");
            t.append(lista.size() > 1 ? "  " : "")
                    .append(p.precioVigente() == null
                            ? (es ? "Sin precio aprobado" : "No approved price")
                            : (es ? "Precio vigente: $ " : "Current price: $ ") + numero(p.precioVigente(), idioma) + " COP")
                    .append(" · ").append(es ? "Disponible: " : "Available: ").append(numero(p.disponible(), idioma))
                    .append(' ').append(p.unidadBase())
                    .append(" · ").append(es ? "Reservado: " : "Reserved: ").append(numero(p.reservado(), idioma));
            if (positivo(p.vencidoSinBaja())) {
                t.append(" · ").append(es ? "Vencido sin baja: " : "Expired, not written off: ").append(numero(p.vencidoSinBaja(), idioma));
            }
            if (p.stockMinimo() != null && p.disponible() != null && p.disponible().compareTo(p.stockMinimo()) < 0) {
                t.append(es ? " · Por debajo del stock mínimo" : " · Below minimum stock");
            }
            t.append('\n');
        }
        if (hayMas) {
            t.append(es ? "Hay más coincidencias: búscalas en Productos." : "There are more matches: search for them in Products.");
        }
        return new AsistenteRespuesta(AsistenteRespuesta.PRODUCTO, t.toString().strip(), null, List.of(),
                new AsistenteRespuesta.Enlace(RUTA_PRODUCTOS, es ? "Ir a Productos" : "Go to Products"));
    }

    // ------------------------------------------------------------------ datos: tasas de cambio

    private Optional<AsistenteRespuesta> tasa(List<String> palabras, String idioma) {
        if (palabras.stream().anyMatch(OPERACION::contains)) {
            return Optional.empty();
        }
        Set<String> codigos = new LinkedHashSet<>();
        palabras.stream().map(ALIAS_MONEDA::get).filter(c -> c != null).forEach(codigos::add);
        boolean pideTasa = palabras.stream().anyMatch(DATO_TASA::contains);
        if (codigos.isEmpty() && !pideTasa) {
            return Optional.empty();
        }
        List<MonedaResponse> todas = monedas.listar(false);
        if (pideTasa) {   // "tasa de cambio del PEN": acepta también el código ISO de cualquier moneda registrada
            todas.stream().filter(m -> palabras.contains(m.codigo().toLowerCase(Locale.ROOT)) && !m.funcional())
                    .forEach(m -> codigos.add(m.codigo()));
        }
        List<MonedaResponse> elegidas = codigos.isEmpty()
                ? todas.stream().filter(m -> m.activa() && !m.funcional()).toList()
                : codigos.stream().flatMap(c -> todas.stream().filter(m -> m.codigo().equals(c))).toList();
        boolean es = es(idioma);
        StringBuilder t = new StringBuilder();
        for (MonedaResponse m : elegidas) {
            if (!t.isEmpty()) {
                t.append('\n');
            }
            if (!m.activa()) {
                t.append(m.codigo()).append(" (").append(m.nombre()).append(es ? ") está inactiva." : ") is inactive.");
            } else if (m.tasaVigente() == null) {
                t.append(m.codigo()).append(" (").append(m.nombre())
                        .append(es ? ") no tiene una tasa de cambio vigente registrada." : ") has no current exchange rate recorded.");
            } else {
                t.append("1 ").append(m.codigo()).append(" = ").append(numero(m.tasaVigente(), idioma)).append(" COP (")
                        .append(es ? "vigente desde " : "effective since ").append(m.fechaTasa());
                if (m.fuenteTasa() != null && !m.fuenteTasa().isBlank()) {
                    t.append(es ? " · fuente: " : " · source: ").append(m.fuenteTasa());
                }
                t.append(')');
            }
        }
        if (t.isEmpty()) {
            t.append(es ? "No hay monedas extranjeras activas registradas." : "There are no active foreign currencies recorded.");
        }
        return Optional.of(new AsistenteRespuesta(AsistenteRespuesta.TASA, t.toString(), null, List.of(),
                new AsistenteRespuesta.Enlace(RUTA_MONEDAS, es ? "Ir a Monedas" : "Go to Currencies")));
    }

    // ------------------------------------------------------------------ utilidades

    private static boolean es(String idioma) {
        return !"en".equals(idioma);
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /**
     * Número con separadores del idioma: 450.000,5 (es) o 450,000.5 (en); hasta 4 decimales, sin ceros sobrantes.
     *
     * @param v valor (nulo = 0)
     * @param idioma es/en
     * @return texto
     */
    static String numero(BigDecimal v, String idioma) {
        DecimalFormat f = new DecimalFormat("#,##0.####",
                DecimalFormatSymbols.getInstance(es(idioma) ? Locale.forLanguageTag("es-CO") : Locale.US));
        return f.format(v == null ? BigDecimal.ZERO : v);
    }

    private static String roles(List<String> roles, String idioma) {
        Map<String, String> nombres = es(idioma) ? ROLES_ES : ROLES_EN;
        List<String> r = new ArrayList<>(roles.stream().map(x -> nombres.getOrDefault(x, x)).toList());
        if (r.size() == 1) {
            return r.get(0);
        }
        String ultimo = r.remove(r.size() - 1);
        return String.join(", ", r) + (es(idioma) ? " y " : " and ") + ultimo;
    }
}
