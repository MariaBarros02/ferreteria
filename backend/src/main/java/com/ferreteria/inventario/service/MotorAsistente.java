/*
 * Nombre: MotorAsistente.java
 * Descripción: Motor de reglas determinista del asistente virtual: valida el árbol de decisión, normaliza el texto (minúsculas, sin tildes ni signos, sin palabras vacías, raíces simples) y clasifica la consulta por coincidencia de palabras clave. No usa servicios externos ni aprendizaje automático: la misma pregunta produce siempre la misma respuesta.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Motor determinista de clasificación de consultas sobre la base de conocimiento. */
public final class MotorAsistente {

    /** Puntaje mínimo para considerar que una consulta corresponde a un tema. */
    public static final double PUNTAJE_MINIMO = 1.0;

    private static final Pattern ID_VALIDO = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    /** Peso de una coincidencia con las palabras clave del otro idioma. */
    private static final double PESO_OTRO_IDIOMA = 0.8;
    /** Bonificación por cada frase clave adicional que coincide en el mismo nodo. */
    private static final double BONO_FRASE_ADICIONAL = 0.1;

    /** Palabras sin significado para la clasificación (español e inglés). */
    static final Set<String> PALABRAS_VACIAS = Set.of(
            "a", "al", "ante", "con", "de", "del", "el", "en", "es", "esa", "ese", "esta", "este", "esto", "la", "las",
            "le", "lo", "los", "me", "mi", "mis", "o", "para", "por", "que", "se", "si", "su", "sus", "te", "tu", "un",
            "una", "unas", "uno", "unos", "y", "ya", "yo", "puedo", "debo", "quiero", "necesito", "favor", "porfa",
            "the", "an", "and", "are", "can", "do", "does", "for", "i", "in", "is", "it", "my", "of", "on", "or", "to",
            "we", "what", "with", "you", "please", "need", "want");

    /** Terminaciones que se quitan para comparar raíces ("registrar" ~ "registro", "precios" ~ "precio"). */
    private static final String[] SUFIJOS = {"aciones", "iciones", "ciones", "acion", "icion", "cion", "mente", "ando",
        "iendo", "ados", "idos", "adas", "idas", "ado", "ido", "ada", "ida", "ing", "ed", "ar", "er", "ir", "as", "es",
        "os", "a", "e", "o", "s"};
    private static final int RAIZ_MINIMA = 4;

    private final BaseConocimiento base;
    private final Map<String, BaseConocimiento.Nodo> porId;

    /**
     * Crea el motor y valida la base de conocimiento.
     *
     * @param base base de conocimiento
     * @throws IllegalArgumentException si un id es inválido o repetido, o si una opción o la raíz apuntan a un nodo inexistente
     */
    public MotorAsistente(BaseConocimiento base) {
        this.base = base;
        Map<String, BaseConocimiento.Nodo> mapa = new LinkedHashMap<>();
        for (BaseConocimiento.Nodo n : base.nodos()) {
            if (n.id() == null || !ID_VALIDO.matcher(n.id()).matches()) {
                throw new IllegalArgumentException("Id de nodo inválido: " + n.id());
            }
            if (mapa.put(n.id(), n) != null) {
                throw new IllegalArgumentException("Id de nodo repetido: " + n.id());
            }
            if (n.respuestaEn(BaseConocimiento.IDIOMA_DEFECTO).isBlank()) {
                throw new IllegalArgumentException("El nodo " + n.id() + " no tiene respuesta en español");
            }
        }
        for (BaseConocimiento.Nodo n : base.nodos()) {
            for (String hijo : n.opciones()) {
                if (!mapa.containsKey(hijo)) {
                    throw new IllegalArgumentException("El nodo " + n.id() + " apunta a una opción inexistente: " + hijo);
                }
            }
        }
        if (base.raiz().isEmpty()) {
            throw new IllegalArgumentException("La base de conocimiento no tiene temas principales");
        }
        for (String id : base.raiz()) {
            if (!mapa.containsKey(id)) {
                throw new IllegalArgumentException("Tema principal inexistente: " + id);
            }
        }
        this.porId = Map.copyOf(mapa);
    }

    /** @return versión del contenido */
    public String version() {
        return base.version();
    }

    /**
     * @param id identificador
     * @return nodo, si existe
     */
    public Optional<BaseConocimiento.Nodo> nodo(String id) {
        return Optional.ofNullable(id == null ? null : porId.get(id));
    }

    /**
     * Temas del menú principal que el rol puede usar.
     *
     * @param rol rol normalizado (p. ej. VENTAS)
     * @return nodos raíz permitidos, en el orden de la base
     */
    public List<BaseConocimiento.Nodo> raiz(String rol) {
        return base.raiz().stream().map(porId::get).filter(n -> permitido(n, rol)).toList();
    }

    /**
     * @param n nodo
     * @return nodos hijos, en orden
     */
    public List<BaseConocimiento.Nodo> hijos(BaseConocimiento.Nodo n) {
        return n.opciones().stream().map(porId::get).toList();
    }

    /**
     * @param n nodo
     * @param rol rol normalizado
     * @return {@code true} si el nodo no restringe roles o el rol está incluido
     */
    public boolean permitido(BaseConocimiento.Nodo n, String rol) {
        return n.roles().isEmpty() || (rol != null && n.roles().contains(rol));
    }

    /**
     * Indica si el rol puede abrir la pantalla enlazada por el nodo (roles del enlace o, si no tiene, los del nodo).
     *
     * @param n nodo
     * @param rol rol normalizado
     * @return {@code true} si hay enlace y está permitido
     */
    public boolean enlacePermitido(BaseConocimiento.Nodo n, String rol) {
        if (n.enlace() == null || n.enlace().ruta() == null) {
            return false;
        }
        List<String> roles = n.enlace().roles().isEmpty() ? n.roles() : n.enlace().roles();
        return roles.isEmpty() || (rol != null && roles.contains(rol));
    }

    /**
     * Clasifica una consulta: puntaje de cada nodo según sus frases clave.
     * <p>Una frase coincide si todas sus palabras significativas aparecen en la consulta (igual, con la misma raíz o
     * con a lo sumo dos letras de diferencia al final). Vale el número de palabras de la frase (las frases largas son
     * más específicas); las del otro idioma valen el 80 %. El nodo toma su mejor frase más 0,1 por cada otra que coincida.</p>
     *
     * @param texto consulta del usuario
     * @param idioma es/en
     * @return nodos con puntaje mayor que cero, del más al menos probable (empates: más frases, luego orden de la base)
     */
    public List<Puntaje> clasificar(String texto, String idioma) {
        List<String> palabras = palabras(normalizar(texto));
        if (palabras.isEmpty()) {
            return List.of();
        }
        List<Puntaje> resultado = new ArrayList<>();
        int orden = 0;
        for (BaseConocimiento.Nodo n : base.nodos()) {
            double mejor = 0;
            int coincidencias = 0;
            for (Map.Entry<String, List<String>> e : n.claves().entrySet()) {
                double peso = e.getKey().equals(idioma) ? 1.0 : PESO_OTRO_IDIOMA;
                for (String frase : e.getValue()) {
                    List<String> requeridas = palabras(normalizar(frase));
                    if (!requeridas.isEmpty() && requeridas.stream().allMatch(r -> palabras.stream().anyMatch(p -> coincide(p, r)))) {
                        coincidencias++;
                        mejor = Math.max(mejor, requeridas.size() * peso);
                    }
                }
            }
            if (mejor > 0) {
                resultado.add(new Puntaje(n, mejor + BONO_FRASE_ADICIONAL * (coincidencias - 1), orden));
            }
            orden++;
        }
        resultado.sort(Comparator.comparingDouble(Puntaje::puntaje).reversed().thenComparingInt(Puntaje::orden));
        return resultado;
    }

    /**
     * Minúsculas, sin tildes ni signos de puntuación, espacios simples.
     *
     * @param texto texto libre (puede ser nulo)
     * @return texto normalizado
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /**
     * Palabras significativas de un texto ya normalizado (sin palabras vacías, sin repetidas).
     *
     * @param normalizado texto normalizado
     * @return palabras en orden de aparición
     */
    public static List<String> palabras(String normalizado) {
        List<String> r = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (String p : normalizado.split(" ")) {
            if (!p.isEmpty() && !PALABRAS_VACIAS.contains(p) && vistas.add(p)) {
                r.add(p);
            }
        }
        return r;
    }

    /**
     * Compara una palabra de la consulta con una palabra clave.
     *
     * @param palabra palabra de la consulta (normalizada)
     * @param clave palabra clave (normalizada)
     * @return {@code true} si son iguales, comparten raíz o una es la otra con hasta dos letras más al final
     */
    public static boolean coincide(String palabra, String clave) {
        if (palabra.equals(clave)) {
            return true;
        }
        if (palabra.length() >= RAIZ_MINIMA && clave.length() >= RAIZ_MINIMA) {
            boolean palabraMasLarga = palabra.length() >= clave.length();
            String larga = palabraMasLarga ? palabra : clave;
            String corta = palabraMasLarga ? clave : palabra;
            if (larga.startsWith(corta) && larga.length() - corta.length() <= 2) {
                return true;
            }
            return lexema(palabra).equals(lexema(clave));
        }
        return false;
    }

    /**
     * Lexema simple: quita una terminación frecuente si quedan al menos cuatro letras.
     *
     * @param palabra palabra normalizada
     * @return lexema
     */
    static String lexema(String palabra) {
        for (String s : SUFIJOS) {
            if (palabra.endsWith(s) && palabra.length() - s.length() >= RAIZ_MINIMA) {
                return palabra.substring(0, palabra.length() - s.length());
            }
        }
        return palabra;
    }

    /**
     * Resultado de la clasificación de un nodo.
     *
     * @param nodo nodo
     * @param puntaje puntaje (mayor = más probable)
     * @param orden posición del nodo en la base (desempate estable)
     */
    public record Puntaje(BaseConocimiento.Nodo nodo, double puntaje, int orden) {
    }
}
