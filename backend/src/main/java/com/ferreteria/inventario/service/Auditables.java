/*
 * Nombre: Auditables.java
 * Descripción: Catálogo de módulos y acciones usados en los eventos de auditoría (RF-20), para filtros consistentes.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import java.util.List;

/**
 * Nombres estables de módulos y acciones auditadas.
 */
public final class Auditables {

    /** Módulo productos. */ public static final String PRODUCTOS = "PRODUCTOS";
    /** Módulo terceros. */ public static final String TERCEROS = "TERCEROS";
    /** Módulo compras. */ public static final String COMPRAS = "COMPRAS";
    /** Módulo ventas. */ public static final String VENTAS = "VENTAS";
    /** Módulo inventario. */ public static final String INVENTARIO = "INVENTARIO";
    /** Módulo precios. */ public static final String PRECIOS = "PRECIOS";
    /** Módulo cierres. */ public static final String CIERRES = "CIERRES";
    /** Módulo usuarios. */ public static final String USUARIOS = "USUARIOS";
    /** Módulo importación. */ public static final String IMPORTACION = "IMPORTACION";
    /** Módulo monedas y tasas de cambio. */ public static final String MONEDAS = "MONEDAS";

    /** Creación. */ public static final String CREAR = "CREAR";
    /** Actualización. */ public static final String ACTUALIZAR = "ACTUALIZAR";
    /** Eliminación física (solo registros sin historial). */ public static final String ELIMINAR = "ELIMINAR";
    /** Desactivación (eliminación lógica). */ public static final String DESACTIVAR = "DESACTIVAR";
    /** Reactivación. */ public static final String ACTIVAR = "ACTIVAR";
    /** Anulación de documento. */ public static final String ANULAR = "ANULAR";
    /** Venta. */ public static final String VENTA = "VENTA";
    /** Recepción de compra. */ public static final String RECEPCION = "RECEPCION";
    /** Devolución. */ public static final String DEVOLUCION = "DEVOLUCION";
    /** Solicitud (ajustes). */ public static final String SOLICITAR = "SOLICITAR";
    /** Aprobación. */ public static final String APROBAR = "APROBAR";
    /** Rechazo. */ public static final String RECHAZAR = "RECHAZAR";
    /** Propuesta de precio. */ public static final String PROPONER = "PROPONER";
    /** Ajuste aplicado. */ public static final String AJUSTE = "AJUSTE";
    /** Registro de conteo. */ public static final String CONTEO = "CONTEO";
    /** Cierre de sesión o período. */ public static final String CERRAR = "CERRAR";
    /** Reapertura de período. */ public static final String REABRIR = "REABRIR";
    /** Reserva. */ public static final String RESERVAR = "RESERVAR";
    /** Liberación de reserva. */ public static final String LIBERAR = "LIBERAR";
    /** Cambio de estado de inventario. */ public static final String CAMBIO_ESTADO = "CAMBIO_ESTADO";
    /** Baja por vencimiento. */ public static final String BAJA_VENCIMIENTO = "BAJA_VENCIMIENTO";
    /** Cambio de rol o permisos. */ public static final String CAMBIO_ROL = "CAMBIO_ROL";
    /** Restablecimiento de contraseña. */ public static final String RESTABLECER_CLAVE = "RESTABLECER_CLAVE";
    /** Importación confirmada. */ public static final String IMPORTAR = "IMPORTAR";
    /** Validación de archivo. */ public static final String VALIDAR = "VALIDAR";
    /** Cancelación. */ public static final String CANCELAR = "CANCELAR";
    /** Exportación de datos. */ public static final String EXPORTAR = "EXPORTAR";
    /** Registro de tasa de cambio. */ public static final String TASA_CAMBIO = "TASA_CAMBIO";

    /** Módulos disponibles para filtrar. */
    public static final List<String> MODULOS = List.of(PRODUCTOS, TERCEROS, COMPRAS, VENTAS, INVENTARIO, PRECIOS,
            CIERRES, USUARIOS, IMPORTACION, MONEDAS);
    /** Acciones disponibles para filtrar. */
    public static final List<String> ACCIONES = List.of(CREAR, ACTUALIZAR, ELIMINAR, DESACTIVAR, ACTIVAR, ANULAR, VENTA,
            RECEPCION, DEVOLUCION, SOLICITAR, APROBAR, RECHAZAR, PROPONER, AJUSTE, CONTEO, CERRAR, REABRIR, RESERVAR,
            LIBERAR, CAMBIO_ESTADO, BAJA_VENCIMIENTO, CAMBIO_ROL, RESTABLECER_CLAVE, VALIDAR, IMPORTAR, CANCELAR, EXPORTAR, TASA_CAMBIO);

    private Auditables() { }
}
