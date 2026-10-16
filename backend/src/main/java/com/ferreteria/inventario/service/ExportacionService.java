/*
 * Nombre: ExportacionService.java
 * Descripción: Exportación a Excel de productos, existencias, terceros, ventas y movimientos (RF-10 complementario), con límites de filas y protección contra fórmulas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.ExistenciaResponse;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.VentaResumenResponse;
import com.ferreteria.inventario.excel.Excel;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportaciones. Los permisos se validan en el controlador (misma matriz que la consulta en pantalla). Cada
 * exportación queda registrada en la auditoría. Las cadenas se neutralizan para evitar inyección de fórmulas.
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    /** Máximo de filas por exportación (protege memoria y tiempo de respuesta). */
    public static final int MAX_FILAS = 50_000;
    private static final int LOTE = 100;

    private final ProductoRepository productos;
    private final TerceroRepository terceros;
    private final InventarioService inventario;
    private final VentaConsultaService ventas;
    private final AuditoriaService auditoria;
    private final EventoAuditoriaService eventos;
    private final ParametrosNegocio parametros;

    /**
     * Catálogo de productos.
     *
     * @param conCostos incluir costo promedio (solo roles autorizados)
     * @param actor usuario
     * @return .xlsx
     */
    @Transactional   // no es de solo lectura: registra el evento de exportación
    public byte[] productos(boolean conCostos, Actor actor) {
        List<String> cols = new ArrayList<>(List.of("id", "codigo_barras", "nombre", "marca", "presentacion", "categoria",
                "unidad_base", "tipo_rastreo", "tiene_vencimiento", "tiene_garantia", "meses_garantia", "porcentaje_iva",
                "porcentaje_incremento", "stock_minimo", "activo"));
        if (conCostos) {
            cols.add("costo_promedio");
        }
        SXSSFWorkbook wb = Excel.libro("productos", cols);
        Sheet s = wb.getSheet("productos");
        int r = 1;
        for (Producto p : productos.findAll(Sort.by("nombre", "marca", "presentacion"))) {
            if (r > MAX_FILAS) {
                break;
            }
            List<Object> v = new ArrayList<>(java.util.Arrays.asList(p.getId(), p.getCodigoBarras(), p.getNombre(), p.getMarca(),
                    p.getPresentacion(), p.getCategoria(), p.getUnidadBase(), String.valueOf(p.getTipoRastreo()),
                    p.isTieneVencimiento(), p.isTieneGarantia(), p.getMesesGarantia(), p.getPorcentajeIva(),
                    p.getPorcentajeIncremento(), p.getStockMinimo(), p.isActivo()));
            if (conCostos) {
                v.add(p.getCostoPromedio());
            }
            Excel.fila(s, r++, v);
        }
        registrar(actor, "productos", r - 1);
        return Excel.bytes(wb);
    }

    /**
     * Existencias por estado con alertas.
     *
     * @param conCostos incluir costo y valor del inventario
     * @param actor usuario
     * @return .xlsx
     */
    @Transactional   // no es de solo lectura: registra el evento de exportación
    public byte[] existencias(boolean conCostos, Actor actor) {
        List<String> cols = new ArrayList<>(List.of("producto_id", "producto", "codigo_barras", "unidad_base", "disponible",
                "reservado", "danado", "total", "vencido_sin_baja", "por_vencer", "stock_minimo", "precio_vigente", "alertas"));
        if (conCostos) {
            cols.add("costo_promedio");
            cols.add("valor_inventario");
        }
        SXSSFWorkbook wb = Excel.libro("existencias", cols);
        Sheet s = wb.getSheet("existencias");
        int r = 1;
        for (int pagina = 0; r <= MAX_FILAS; pagina++) {
            var page = inventario.existencias("", null, pagina, LOTE);
            for (ExistenciaResponse e : page.contenido()) {
                List<Object> v = new ArrayList<>(java.util.Arrays.asList(e.productoId(), e.producto(), e.codigoBarras(),
                        e.unidadBase(), e.disponible(), e.reservado(), e.danado(), e.total(), e.vencidoSinBaja(),
                        e.porVencer(), e.stockMinimo(), e.precioVigente(), String.join(", ", e.alertas())));
                if (conCostos) {
                    v.add(e.costoPromedio());
                    v.add(e.valorInventario());
                }
                Excel.fila(s, r++, v);
            }
            if (pagina + 1 >= page.totalPaginas()) {
                break;
            }
        }
        registrar(actor, "existencias", r - 1);
        return Excel.bytes(wb);
    }

    /**
     * Terceros (datos de contacto: solo roles autorizados).
     *
     * @param actor usuario
     * @return .xlsx
     */
    @Transactional   // no es de solo lectura: registra el evento de exportación
    public byte[] terceros(Actor actor) {
        SXSSFWorkbook wb = Excel.libro("terceros", List.of("id", "tipo_persona", "tipo_identificacion",
                "numero_identificacion", "nombre", "direccion", "ciudad", "telefono", "correo", "es_cliente", "es_proveedor"));
        Sheet s = wb.getSheet("terceros");
        int r = 1;
        for (Tercero t : terceros.findAll(Sort.by("id"))) {
            if (r > MAX_FILAS) {
                break;
            }
            Excel.fila(s, r++, java.util.Arrays.asList(t.getId(), String.valueOf(t.getTipoPersona()),
                    String.valueOf(t.getTipoIdentificacion()), t.getNumeroIdentificacion(),
                    com.ferreteria.inventario.dto.TerceroResponse.nombreDe(t), t.getDireccion(), t.getCiudad(),
                    t.getTelefono(), t.getCorreo(), t.isCliente(), t.isProveedor()));
        }
        registrar(actor, "terceros", r - 1);
        return Excel.bytes(wb);
    }

    /**
     * Ventas de un rango de fechas.
     *
     * @param desde fecha inicial (opcional)
     * @param hasta fecha final (opcional)
     * @param actor usuario
     * @return .xlsx
     */
    @Transactional   // no es de solo lectura: registra el evento de exportación
    public byte[] ventas(LocalDate desde, LocalDate hasta, Actor actor) {
        SXSSFWorkbook wb = Excel.libro("ventas", List.of("id", "numero_interno", "factura", "fecha_utc", "cliente",
                "usuario", "estado", "total", "con_devoluciones"));
        Sheet s = wb.getSheet("ventas");
        int r = 1;
        for (int pagina = 0; r <= MAX_FILAS; pagina++) {
            var page = ventas.listar(new VentaConsultaService.Filtros(null, null, desde, hasta, null), pagina, LOTE);
            for (VentaResumenResponse v : page.contenido()) {
                Excel.fila(s, r++, java.util.Arrays.asList(v.id(), v.numeroInterno(), v.numeracionDian(), v.fecha(),
                        v.cliente(), v.usuario(), String.valueOf(v.estado()), v.total(), v.tieneDevoluciones()));
            }
            if (pagina + 1 >= page.totalPaginas()) {
                break;
            }
        }
        registrar(actor, "ventas", r - 1);
        return Excel.bytes(wb);
    }

    /**
     * Movimientos de inventario con filtros.
     *
     * @param f filtros
     * @param actor usuario
     * @return .xlsx
     */
    @Transactional   // no es de solo lectura: registra el evento de exportación
    public byte[] movimientos(AuditoriaService.Filtros f, Actor actor) {
        SXSSFWorkbook wb = Excel.libro("movimientos", List.of("id", "fecha_utc", "tipo", "estado_inventario", "producto_id",
                "cantidad", "valor_anterior", "valor_nuevo", "documento", "motivo", "usuario_id", "ip", "lote_id",
                "movimiento_original"));
        Sheet s = wb.getSheet("movimientos");
        int r = 1;
        for (int pagina = 0; r <= MAX_FILAS; pagina++) {
            var page = auditoria.consultarFiltrado(f, parametros.getZonaHoraria(), pagina, LOTE);
            for (MovimientoResponse m : page.contenido()) {
                Excel.fila(s, r++, java.util.Arrays.asList(m.id(), m.fechaHora(), String.valueOf(m.tipo()),
                        String.valueOf(m.estadoInventario()), m.idProducto(), m.cantidad(), m.valorAnterior(), m.valorNuevo(),
                        m.documentoRelacionado(), m.motivo(), m.idUsuario(), m.ip(), m.idLote(), m.idMovimientoOriginal()));
            }
            if (pagina + 1 >= page.totalPaginas()) {
                break;
            }
        }
        registrar(actor, "movimientos", r - 1);
        return Excel.bytes(wb);
    }

    private void registrar(Actor actor, String que, int filas) {
        eventos.registrar(actor, Auditables.IMPORTACION, Auditables.EXPORTAR, "EXPORTACION", que,
                "Exportación de " + que + " a Excel (" + filas + " filas)", java.util.Map.of("filas", filas));
    }
}
