/*
 * Nombre: ImportacionService.java
 * Descripción: Migración desde Excel (RF-10, RN-15): plantillas, validación por fila y columna, detección de duplicados y posibles duplicados, revisión del usuario, importación atómica e informe de errores.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Importacion;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.ImportacionResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.excel.Excel;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ImportacionRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo RN-15: <b>validar</b> (formato y columnas) → <b>detectar errores</b> por fila y columna (mismas reglas que la
 * API: Bean Validation de los DTO) → <b>detectar duplicados</b> (exactos contra la base y dentro del archivo) y
 * <b>posibles duplicados</b> (nombres equivalentes al normalizar mayúsculas, tildes y espacios) → <b>revisión del
 * usuario</b> (decide qué posibles duplicados importar) → <b>consolidar e importar</b> en una sola transacción.
 * Nunca se importan filas con errores ni duplicados exactos, y un archivo idéntico (mismo SHA-256) ya importado se
 * rechaza para no duplicar información al reprocesarlo.
 */
@Service
@RequiredArgsConstructor
public class ImportacionService {

    /** Tipo productos. */
    public static final String PRODUCTOS = "productos";
    /** Tipo terceros. */
    public static final String TERCEROS = "terceros";
    /** Máximo de filas de datos por archivo. */
    public static final int MAX_FILAS = 5000;

    static final List<String> COLUMNAS_PRODUCTOS = List.of("codigo_barras", "nombre", "marca", "presentacion",
            "unidad_base", "tipo_rastreo", "tiene_vencimiento", "tiene_garantia", "meses_garantia", "categoria",
            "descripcion", "porcentaje_iva", "porcentaje_incremento", "stock_minimo");
    static final Set<String> OBLIGATORIAS_PRODUCTOS = Set.of("codigo_barras", "nombre", "marca", "presentacion", "unidad_base");
    static final List<String> COLUMNAS_TERCEROS = List.of("tipo_persona", "tipo_identificacion", "numero_identificacion",
            "primer_nombre", "segundo_nombre", "primer_apellido", "segundo_apellido", "razon_social", "direccion",
            "ciudad", "telefono", "correo", "es_cliente", "es_proveedor");
    static final Set<String> OBLIGATORIAS_TERCEROS = Set.of("tipo_persona", "tipo_identificacion", "numero_identificacion",
            "direccion", "ciudad", "telefono", "correo");

    /** Propiedad del DTO → columna del archivo (para ubicar los errores de validación). */
    private static final Map<String, String> PROPIEDAD_COLUMNA = Map.ofEntries(
            Map.entry("codigoBarras", "codigo_barras"), Map.entry("unidadBase", "unidad_base"),
            Map.entry("tipoRastreo", "tipo_rastreo"), Map.entry("mesesGarantia", "meses_garantia"),
            Map.entry("porcentajeIva", "porcentaje_iva"), Map.entry("porcentajeIncremento", "porcentaje_incremento"),
            Map.entry("stockMinimo", "stock_minimo"), Map.entry("vencimientoConLote", "tipo_rastreo"),
            Map.entry("tipoPersona", "tipo_persona"), Map.entry("tipoIdentificacion", "tipo_identificacion"),
            Map.entry("numeroIdentificacion", "numero_identificacion"), Map.entry("primerNombre", "primer_nombre"),
            Map.entry("segundoNombre", "segundo_nombre"), Map.entry("primerApellido", "primer_apellido"),
            Map.entry("segundoApellido", "segundo_apellido"), Map.entry("razonSocial", "razon_social"),
            Map.entry("naturalValida", "primer_nombre"), Map.entry("juridicaValida", "razon_social"),
            Map.entry("rolValido", "es_cliente"), Map.entry("cliente", "es_cliente"), Map.entry("proveedor", "es_proveedor"));

    private final ImportacionRepository importaciones;
    private final ProductoRepository productos;
    private final TerceroRepository terceros;
    private final UsuarioRepository usuarios;
    private final ProductoService productoService;
    private final TerceroService terceroService;
    private final EventoAuditoriaService eventos;
    private final KardexService kardex;
    private final Validator validador;
    private final ObjectMapper json;

    /**
     * Plantilla vacía con encabezados, fila de ejemplo e instrucciones.
     *
     * @param tipo productos o terceros
     * @return bytes .xlsx
     */
    public byte[] plantilla(String tipo) {
        List<String> cols = columnas(tipo);
        SXSSFWorkbook wb = Excel.libro(tipo, cols);
        Sheet s = wb.getSheet(tipo);
        if (PRODUCTOS.equals(tipo)) {
            Excel.fila(s, 1, List.of("7701234599999", "Martillo", "Stanley", "16 oz", "unidad", "NINGUNO", "NO", "SI", "6",
                    "Herramientas", "Martillo de uña", "19", "0.30", "5"));
        } else {
            Excel.fila(s, 1, List.of("NATURAL", "CC", "1010101010", "Ana", "", "Gómez", "", "", "Cra 1 # 2-3", "Neiva",
                    "3001112233", "ana@correo.com", "SI", "NO"));
        }
        Sheet ayuda = wb.createSheet("instrucciones");
        List<String> lineas = PRODUCTOS.equals(tipo) ? List.of(
                "Obligatorias: codigo_barras (8 a 14 dígitos, único), nombre, marca, presentacion, unidad_base.",
                "tipo_rastreo: NINGUNO, LOTE o SERIE (por defecto NINGUNO). Si tiene_vencimiento = SI, debe ser LOTE.",
                "tiene_vencimiento / tiene_garantia: SI o NO. meses_garantia: entero 1–120.",
                "porcentaje_iva: 0–100 (ej. 19). porcentaje_incremento: fracción (0.30 = 30 %). stock_minimo: número ≥ 0.",
                "Las existencias iniciales NO se importan: se registran con recepciones de compra o ajustes aprobados.",
                "Formatee la columna codigo_barras como Texto para conservar ceros a la izquierda.")
                : List.of(
                "Obligatorias: tipo_persona (NATURAL/JURIDICA), tipo_identificacion (CC, NIT, CE, PASAPORTE),",
                "numero_identificacion (único), direccion, ciudad, telefono, correo.",
                "Persona natural: primer_nombre y primer_apellido obligatorios. Persona jurídica: razon_social obligatoria.",
                "es_cliente / es_proveedor: SI o NO (al menos uno en SI).");
        for (int i = 0; i < lineas.size(); i++) {
            Excel.fila(ayuda, i, List.of(lineas.get(i)));
        }
        return Excel.bytes(wb);
    }

    /**
     * Valida un archivo y guarda el resultado para revisión (no importa nada).
     *
     * @param tipo productos o terceros
     * @param nombreArchivo nombre original
     * @param contenido bytes del archivo
     * @param actor usuario
     * @return resultado de la validación
     */
    @Transactional
    public ImportacionResponse validar(String tipo, String nombreArchivo, byte[] contenido, Actor actor) {
        List<String> cols = columnas(tipo);
        String nombre = nombreArchivo == null ? "archivo.xlsx" : nombreArchivo.replaceAll("[\\\\/]", "_");
        if (contenido == null || contenido.length == 0) {
            throw ApiException.regla("ARCHIVO_VACIO", "error.archivo.vacio");
        }
        if (!nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw ApiException.regla("ARCHIVO_INVALIDO", "error.archivo.formato");
        }
        String hash = sha256(contenido);
        if (importaciones.existsByTipoAndHashArchivoAndEstado(tipo, hash, EstadoImportacion.IMPORTADA)) {
            throw ApiException.conflicto("ARCHIVO_YA_IMPORTADO", "error.archivo.ya.importado", nombre);
        }
        List<ImportacionResponse.Fila> filas = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet s = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            Row enc = s.getRow(s.getFirstRowNum());
            if (enc == null) {
                throw ApiException.regla("ARCHIVO_SIN_ENCABEZADO", "error.archivo.encabezado", String.join(", ", cols));
            }
            Map<String, Integer> indice = new HashMap<>();
            for (int c = 0; c < enc.getLastCellNum(); c++) {
                String h = normalizarEncabezado(Excel.texto(enc.getCell(c), fmt));
                if (!h.isEmpty()) {
                    indice.putIfAbsent(h, c);
                }
            }
            List<String> faltantes = obligatorias(tipo).stream().filter(c -> !indice.containsKey(c)).sorted().toList();
            if (!faltantes.isEmpty()) {
                throw ApiException.regla("COLUMNAS_FALTANTES", "error.archivo.columnas", String.join(", ", faltantes));
            }
            int datos = 0;
            for (int r = enc.getRowNum() + 1; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                Map<String, String> valores = new LinkedHashMap<>();
                boolean vacia = true;
                for (String c : cols) {
                    Integer i = indice.get(c);
                    String v = i == null || row == null ? "" : Excel.texto(row.getCell(i), fmt);
                    valores.put(c, v);
                    vacia &= v.isEmpty();
                }
                if (vacia) {
                    continue;
                }
                if (++datos > MAX_FILAS) {
                    throw ApiException.regla("ARCHIVO_DEMASIADO_GRANDE", "error.archivo.filas", String.valueOf(MAX_FILAS));
                }
                filas.add(new ImportacionResponse.Fila(r + 1, "VALIDA", valores, new ArrayList<>(), null));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.regla("ARCHIVO_INVALIDO", "error.archivo.formato");
        }
        if (filas.isEmpty()) {
            throw ApiException.regla("ARCHIVO_SIN_DATOS", "error.archivo.sin.datos");
        }
        filas = PRODUCTOS.equals(tipo) ? validarProductos(filas) : validarTerceros(filas);
        Importacion imp = importaciones.save(Importacion.builder().tipo(tipo).nombreArchivo(recortar(nombre, 255))
                .hashArchivo(hash).estado(EstadoImportacion.VALIDADA).totalFilas(filas.size())
                .filasValidas(contar(filas, "VALIDA")).filasError(contar(filas, "ERROR"))
                .filasDuplicadas(contar(filas, "DUPLICADO") + contar(filas, "POSIBLE_DUPLICADO"))
                .filas(aJson(filas)).idUsuario(actor.idUsuario()).fechaCarga(kardex.ahora()).build());
        eventos.registrar(actor, Auditables.IMPORTACION, Auditables.VALIDAR, "IMPORTACION", imp.getId(),
                "Archivo " + nombre + " validado (" + tipo + ")", Map.of("filas", filas.size(),
                        "validas", imp.getFilasValidas(), "errores", imp.getFilasError(), "duplicadas", imp.getFilasDuplicadas()));
        return aDto(imp, filas);
    }

    /**
     * Confirma e importa las filas válidas (y los posibles duplicados aprobados) en una sola transacción.
     *
     * @param id importación validada
     * @param aprobados filas marcadas como posible duplicado que se importan igualmente
     * @param actor usuario
     * @return resultado final por fila
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportacionResponse confirmar(Long id, List<Integer> aprobados, Actor actor) {
        Importacion imp = importaciones.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Importación"));
        if (imp.getEstado() != EstadoImportacion.VALIDADA) {
            throw ApiException.conflicto("IMPORTACION_PROCESADA", "error.importacion.procesada", String.valueOf(id));
        }
        if (importaciones.existsByTipoAndHashArchivoAndEstado(imp.getTipo(), imp.getHashArchivo(), EstadoImportacion.IMPORTADA)) {
            throw ApiException.conflicto("ARCHIVO_YA_IMPORTADO", "error.archivo.ya.importado", imp.getNombreArchivo());
        }
        Set<Integer> ok = aprobados == null ? Set.of() : new HashSet<>(aprobados);
        List<ImportacionResponse.Fila> filas = deJson(imp.getFilas());
        List<ImportacionResponse.Fila> resultado = new ArrayList<>();
        int importadas = 0;
        for (ImportacionResponse.Fila f : filas) {
            boolean candidata = "VALIDA".equals(f.estado()) || ("POSIBLE_DUPLICADO".equals(f.estado()) && ok.contains(f.fila()));
            if (!candidata) {
                resultado.add(new ImportacionResponse.Fila(f.fila(), "VALIDA".equals(f.estado()) || "POSIBLE_DUPLICADO".equals(f.estado())
                        ? "OMITIDA" : f.estado(), f.datos(), f.errores(), f.coincidencia()));
                continue;
            }
            // Revalida unicidad en el momento de importar (la base pudo cambiar desde la validación).
            String conflicto = PRODUCTOS.equals(imp.getTipo()) ? conflictoProducto(f.datos()) : conflictoTercero(f.datos());
            if (conflicto != null) {
                List<ImportacionResponse.ErrorCelda> errs = new ArrayList<>(f.errores());
                errs.add(new ImportacionResponse.ErrorCelda(PRODUCTOS.equals(imp.getTipo()) ? "codigo_barras" : "numero_identificacion",
                        conflicto));
                resultado.add(new ImportacionResponse.Fila(f.fila(), "OMITIDA", f.datos(), errs, f.coincidencia()));
                continue;
            }
            if (PRODUCTOS.equals(imp.getTipo())) {
                productoService.crear(producto(f.datos()));
            } else {
                terceroService.crear(tercero(f.datos()));
            }
            importadas++;
            resultado.add(new ImportacionResponse.Fila(f.fila(), "IMPORTADA", f.datos(), f.errores(), f.coincidencia()));
        }
        imp.setEstado(EstadoImportacion.IMPORTADA);
        imp.setFilasImportadas(importadas);
        imp.setFechaProceso(kardex.ahora());
        imp.setFilas(aJson(resultado));
        imp = importaciones.save(imp);
        eventos.registrar(actor, Auditables.IMPORTACION, Auditables.IMPORTAR, "IMPORTACION", imp.getId(),
                "Importación de " + imp.getTipo() + " confirmada: " + importadas + " fila(s) importada(s)",
                Map.of("archivo", imp.getNombreArchivo(), "importadas", importadas, "omitidas", resultado.size() - importadas));
        return aDto(imp, resultado);
    }

    /**
     * Cancela una importación validada (no importa nada).
     *
     * @param id importación
     * @param actor usuario
     * @return importación cancelada
     */
    @Transactional
    public ImportacionResponse cancelar(Long id, Actor actor) {
        Importacion imp = importaciones.findForUpdate(id).orElseThrow(() -> ApiException.noEncontrado("Importación"));
        if (imp.getEstado() != EstadoImportacion.VALIDADA) {
            throw ApiException.conflicto("IMPORTACION_PROCESADA", "error.importacion.procesada", String.valueOf(id));
        }
        imp.setEstado(EstadoImportacion.CANCELADA);
        imp.setFechaProceso(kardex.ahora());
        imp = importaciones.save(imp);
        eventos.registrar(actor, Auditables.IMPORTACION, Auditables.CANCELAR, "IMPORTACION", imp.getId(),
                "Importación cancelada: " + imp.getNombreArchivo(), null);
        return aDto(imp, deJson(imp.getFilas()));
    }

    /**
     * Detalle de una importación.
     *
     * @param id importación
     * @return importación con filas
     */
    @Transactional(readOnly = true)
    public ImportacionResponse obtener(Long id) {
        Importacion imp = importaciones.findById(id).orElseThrow(() -> ApiException.noEncontrado("Importación"));
        return aDto(imp, deJson(imp.getFilas()));
    }

    /**
     * Tipo de una importación (para validar permisos por tipo).
     *
     * @param id importación
     * @return productos o terceros
     */
    @Transactional(readOnly = true)
    public String tipo(Long id) {
        return importaciones.findById(id).map(Importacion::getTipo).orElseThrow(() -> ApiException.noEncontrado("Importación"));
    }

    /**
     * Historial (sin detalle de filas).
     *
     * @param pagina página
     * @param tamano tamaño
     * @return página
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ImportacionResponse> historial(int pagina, int tamano) {
        return PaginaResponse.de(importaciones.findAllByOrderByIdDesc(PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), 100))), i -> aDto(i, List.of()));
    }

    /**
     * Informe de errores y filas omitidas en Excel.
     *
     * @param id importación
     * @return bytes .xlsx
     */
    @Transactional(readOnly = true)
    public byte[] informeErrores(Long id) {
        Importacion imp = importaciones.findById(id).orElseThrow(() -> ApiException.noEncontrado("Importación"));
        List<String> cols = new ArrayList<>(List.of("fila", "estado", "columna", "mensaje", "coincidencia"));
        cols.addAll(columnas(imp.getTipo()));
        SXSSFWorkbook wb = Excel.libro("errores", cols);
        Sheet s = wb.getSheet("errores");
        int r = 1;
        for (ImportacionResponse.Fila f : deJson(imp.getFilas())) {
            if (f.errores().isEmpty() && f.coincidencia() == null) {
                continue;
            }
            List<ImportacionResponse.ErrorCelda> errs = f.errores().isEmpty()
                    ? List.of(new ImportacionResponse.ErrorCelda("", "")) : f.errores();
            for (ImportacionResponse.ErrorCelda e : errs) {
                List<Object> v = new ArrayList<>(List.of(f.fila(), f.estado(), e.columna(), e.mensaje(),
                        f.coincidencia() == null ? "" : f.coincidencia()));
                for (String c : columnas(imp.getTipo())) {
                    v.add(f.datos().getOrDefault(c, ""));
                }
                Excel.fila(s, r++, v);
            }
        }
        return Excel.bytes(wb);
    }

    // ------------------------------------------------------------------ validación de productos

    private List<ImportacionResponse.Fila> validarProductos(List<ImportacionResponse.Fila> filas) {
        Map<String, String> codigos = new HashMap<>();
        Map<String, String> claves = new HashMap<>();
        for (Producto p : productos.findAll()) {
            codigos.put(p.getCodigoBarras(), etiqueta(p));
            claves.put(clave(p.getNombre(), p.getMarca(), p.getPresentacion()), etiqueta(p) + " [" + p.getCodigoBarras() + "]");
        }
        Map<String, Integer> codigosArchivo = new HashMap<>();
        Map<String, Integer> clavesArchivo = new HashMap<>();
        List<ImportacionResponse.Fila> r = new ArrayList<>();
        for (ImportacionResponse.Fila f : filas) {
            Map<String, String> d = f.datos();
            List<ImportacionResponse.ErrorCelda> errs = new ArrayList<>();
            ProductoRequest req = null;
            try {
                req = producto(d);
                errs.addAll(violaciones(validador.validate(req)));
            } catch (ApiException e) {
                errs.add(new ImportacionResponse.ErrorCelda(e.getArgumentos().length > 0 ? String.valueOf(e.getArgumentos()[0]) : "",
                        e.getArgumentos().length > 1 ? String.valueOf(e.getArgumentos()[1]) : e.getCodigo()));
            }
            String estado = errs.isEmpty() ? "VALIDA" : "ERROR";
            String coincidencia = null;
            String codigo = d.get("codigo_barras");
            if (!codigo.isEmpty() && codigosArchivo.containsKey(codigo)) {
                errs.add(new ImportacionResponse.ErrorCelda("codigo_barras", "Código repetido en el archivo (fila "
                        + codigosArchivo.get(codigo) + ")"));
                estado = "ERROR";
            }
            codigosArchivo.putIfAbsent(codigo, f.fila());
            if ("VALIDA".equals(estado) && codigos.containsKey(codigo)) {
                estado = "DUPLICADO";
                coincidencia = codigos.get(codigo);
            }
            String k = clave(d.get("nombre"), d.get("marca"), d.get("presentacion"));
            if ("VALIDA".equals(estado)) {
                if (claves.containsKey(k)) {
                    estado = "POSIBLE_DUPLICADO";
                    coincidencia = claves.get(k);
                } else if (clavesArchivo.containsKey(k)) {
                    estado = "POSIBLE_DUPLICADO";
                    coincidencia = "Fila " + clavesArchivo.get(k) + " del archivo";
                }
            }
            clavesArchivo.putIfAbsent(k, f.fila());
            r.add(new ImportacionResponse.Fila(f.fila(), estado, d, errs, coincidencia));
        }
        return r;
    }

    private ProductoRequest producto(Map<String, String> d) {
        return new ProductoRequest(d.get("nombre"), d.get("marca"), d.get("presentacion"), d.get("codigo_barras"),
                d.get("unidad_base"), rastreo(d.get("tipo_rastreo")), siNo(d.get("tiene_vencimiento"), "tiene_vencimiento"),
                siNo(d.get("tiene_garantia"), "tiene_garantia"), vacio(d.get("descripcion")), vacio(d.get("categoria")),
                decimal(d.get("porcentaje_iva"), "porcentaje_iva"), decimal(d.get("porcentaje_incremento"), "porcentaje_incremento"),
                decimal(d.get("stock_minimo"), "stock_minimo"), entero(d.get("meses_garantia"), "meses_garantia"));
    }

    private String conflictoProducto(Map<String, String> d) {
        if (productos.findByCodigoBarras(d.get("codigo_barras")).isPresent()) {
            return "El código ya existe en el catálogo";
        }
        if (productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(d.get("nombre"), d.get("marca"),
                d.get("presentacion")).isPresent()) {
            return "La referencia producto + marca + presentación ya existe";
        }
        return null;
    }

    // ------------------------------------------------------------------ validación de terceros

    private List<ImportacionResponse.Fila> validarTerceros(List<ImportacionResponse.Fila> filas) {
        Map<String, String> ids = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (Tercero t : terceros.findAll()) {
            ids.put(t.getNumeroIdentificacion(), TerceroResponse.nombreDe(t));
            nombres.put(normalizar(TerceroResponse.nombreDe(t)), TerceroResponse.nombreDe(t) + " [" + t.getNumeroIdentificacion() + "]");
        }
        Map<String, Integer> idsArchivo = new HashMap<>();
        Map<String, Integer> nombresArchivo = new HashMap<>();
        List<ImportacionResponse.Fila> r = new ArrayList<>();
        for (ImportacionResponse.Fila f : filas) {
            Map<String, String> d = f.datos();
            List<ImportacionResponse.ErrorCelda> errs = new ArrayList<>();
            TerceroRequest req = null;
            try {
                req = tercero(d);
                errs.addAll(violaciones(validador.validate(req)));
            } catch (ApiException e) {
                errs.add(new ImportacionResponse.ErrorCelda(e.getArgumentos().length > 0 ? String.valueOf(e.getArgumentos()[0]) : "",
                        e.getArgumentos().length > 1 ? String.valueOf(e.getArgumentos()[1]) : e.getCodigo()));
            }
            String estado = errs.isEmpty() ? "VALIDA" : "ERROR";
            String coincidencia = null;
            String id = d.get("numero_identificacion");
            if (!id.isEmpty() && idsArchivo.containsKey(id)) {
                errs.add(new ImportacionResponse.ErrorCelda("numero_identificacion",
                        "Identificación repetida en el archivo (fila " + idsArchivo.get(id) + ")"));
                estado = "ERROR";
            }
            idsArchivo.putIfAbsent(id, f.fila());
            if ("VALIDA".equals(estado) && ids.containsKey(id)) {
                estado = "DUPLICADO";
                coincidencia = ids.get(id);
            }
            String nombre = req == null ? "" : normalizar(nombreSolicitud(req));
            if ("VALIDA".equals(estado) && !nombre.isEmpty()) {
                if (nombres.containsKey(nombre)) {
                    estado = "POSIBLE_DUPLICADO";
                    coincidencia = nombres.get(nombre);
                } else if (nombresArchivo.containsKey(nombre)) {
                    estado = "POSIBLE_DUPLICADO";
                    coincidencia = "Fila " + nombresArchivo.get(nombre) + " del archivo";
                }
            }
            if (!nombre.isEmpty()) {
                nombresArchivo.putIfAbsent(nombre, f.fila());
            }
            r.add(new ImportacionResponse.Fila(f.fila(), estado, d, errs, coincidencia));
        }
        return r;
    }

    private TerceroRequest tercero(Map<String, String> d) {
        return new TerceroRequest(persona(d.get("tipo_persona")), identificacion(d.get("tipo_identificacion")),
                d.get("numero_identificacion"), vacio(d.get("primer_nombre")), vacio(d.get("segundo_nombre")),
                vacio(d.get("primer_apellido")), vacio(d.get("segundo_apellido")), vacio(d.get("razon_social")),
                d.get("direccion"), d.get("ciudad"), d.get("telefono"), d.get("correo"),
                siNo(d.get("es_cliente"), "es_cliente"), siNo(d.get("es_proveedor"), "es_proveedor"));
    }

    private String conflictoTercero(Map<String, String> d) {
        return terceros.findByNumeroIdentificacion(d.get("numero_identificacion")).isPresent()
                ? "La identificación ya existe" : null;
    }

    private static String nombreSolicitud(TerceroRequest r) {
        if (r.tipoPersona() == TipoPersona.JURIDICA) {
            return r.razonSocial() == null ? "" : r.razonSocial();
        }
        return String.join(" ", Arrays.stream(new String[]{r.primerNombre(), r.segundoNombre(), r.primerApellido(),
                r.segundoApellido()}).filter(Objects::nonNull).toList());
    }

    // ------------------------------------------------------------------ utilidades

    private List<ImportacionResponse.ErrorCelda> violaciones(Set<? extends ConstraintViolation<?>> vs) {
        List<ImportacionResponse.ErrorCelda> r = new ArrayList<>();
        for (ConstraintViolation<?> v : vs) {
            String prop = v.getPropertyPath().toString();
            r.add(new ImportacionResponse.ErrorCelda(PROPIEDAD_COLUMNA.getOrDefault(prop, prop), v.getMessage()));
        }
        r.sort(Comparator.comparing(ImportacionResponse.ErrorCelda::columna));
        return r;
    }

    private static List<String> columnas(String tipo) {
        if (PRODUCTOS.equals(tipo)) {
            return COLUMNAS_PRODUCTOS;
        }
        if (TERCEROS.equals(tipo)) {
            return COLUMNAS_TERCEROS;
        }
        throw ApiException.regla("TIPO_IMPORTACION_INVALIDO", "error.importacion.tipo", String.valueOf(tipo));
    }

    private static Set<String> obligatorias(String tipo) {
        return PRODUCTOS.equals(tipo) ? OBLIGATORIAS_PRODUCTOS : OBLIGATORIAS_TERCEROS;
    }

    /**
     * Normaliza un encabezado: minúsculas, sin tildes, espacios a guion bajo.
     *
     * @param h encabezado
     * @return encabezado normalizado
     */
    static String normalizarEncabezado(String h) {
        return sinTildes(h).toLowerCase(Locale.ROOT).trim().replaceAll("[\\s-]+", "_").replace("*", "");
    }

    /**
     * Clave para detectar posibles duplicados: sin tildes, mayúsculas, espacios ni signos.
     *
     * @param s texto
     * @return clave
     */
    static String normalizar(String s) {
        return s == null ? "" : sinTildes(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String clave(String nombre, String marca, String presentacion) {
        return normalizar(nombre) + "|" + normalizar(marca) + "|" + normalizar(presentacion);
    }

    private static String sinTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String etiqueta(Producto p) {
        return p.getNombre() + " " + p.getMarca() + " " + p.getPresentacion();
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean siNo(String v, String columna) {
        String x = v == null ? "" : sinTildes(v).trim().toUpperCase(Locale.ROOT);
        return switch (x) {
            case "", "NO", "N", "FALSE", "0" -> false;
            case "SI", "S", "TRUE", "1", "X" -> true;
            default -> throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", columna, "Use SI o NO");
        };
    }

    private static TipoRastreo rastreo(String v) {
        String x = v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
        if (x.isEmpty()) {
            return TipoRastreo.NINGUNO;
        }
        try {
            return TipoRastreo.valueOf(x);
        } catch (IllegalArgumentException e) {
            throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", "tipo_rastreo", "Use NINGUNO, LOTE o SERIE");
        }
    }

    private static TipoPersona persona(String v) {
        String x = v == null ? "" : sinTildes(v).trim().toUpperCase(Locale.ROOT);
        try {
            return x.isEmpty() ? null : TipoPersona.valueOf(x);
        } catch (IllegalArgumentException e) {
            throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", "tipo_persona", "Use NATURAL o JURIDICA");
        }
    }

    private static TipoIdentificacion identificacion(String v) {
        String x = v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
        try {
            return x.isEmpty() ? null : TipoIdentificacion.valueOf(x);
        } catch (IllegalArgumentException e) {
            throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", "tipo_identificacion",
                    "Use CC, NIT, CE o PASAPORTE");
        }
    }

    private static BigDecimal decimal(String v, String columna) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", columna, "Debe ser un número");
        }
    }

    private static Integer entero(String v, String columna) {
        BigDecimal d = decimal(v, columna);
        if (d == null) {
            return null;
        }
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.regla("VALOR_INVALIDO", "error.importacion.valor", columna, "Debe ser un número entero");
        }
    }

    private static int contar(List<ImportacionResponse.Fila> fs, String estado) {
        return (int) fs.stream().filter(f -> estado.equals(f.estado())).count();
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(byte[] b) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : h) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String aJson(List<ImportacionResponse.Fila> filas) {
        try {
            return json.writeValueAsString(filas);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ImportacionResponse.Fila> deJson(String s) {
        try {
            return json.readValue(s == null ? "[]" : s, new TypeReference<List<ImportacionResponse.Fila>>() { });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ImportacionResponse aDto(Importacion i, List<ImportacionResponse.Fila> filas) {
        String usuario = usuarios.findById(i.getIdUsuario()).map(Usuario::getCorreo).orElse(null);
        return new ImportacionResponse(i.getId(), i.getTipo(), i.getNombreArchivo(), i.getEstado(), i.getTotalFilas(),
                i.getFilasValidas(), i.getFilasError(), i.getFilasDuplicadas(), i.getFilasImportadas(), usuario,
                i.getFechaCarga(), i.getFechaProceso(), filas);
    }
}
