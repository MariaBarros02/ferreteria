/*
 * Nombre: comprobante.js
 * Descripción: Generación del PDF del comprobante de venta (RF-19) con jsPDF a partir del detalle devuelto por la API: empresa, cliente, líneas, totales, numeración y leyenda (comprobante interno o representación gráfica de factura electrónica según la integración fiscal).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { jsPDF } from 'jspdf';
import { cantidad, fecha, fechaHora, moneda } from '../utils/formato';
import { leyendaComprobante, tituloComprobante, unir } from '../utils/comprobante';

export { leyendaComprobante, nombreArchivoComprobante, tituloComprobante } from '../utils/comprobante';

/**
 * Construye el PDF.
 * @param {object} detalle VentaDetalleResponse
 * @param {Function} t función de traducción
 * @returns {jsPDF} documento
 */
export function crearComprobantePdf(detalle, t) {
  const { venta, cliente, empresa } = detalle;
  const doc = new jsPDF({ unit: 'mm', format: 'letter' });
  const ancho = doc.internal.pageSize.getWidth();
  const alto = doc.internal.pageSize.getHeight();
  const m = 15;
  let y = m;

  const texto = (s, x, yy, op) => doc.text(String(s ?? ''), x, yy, op);
  const salto = (h) => {
    y += h;
    if (y > alto - 25) { doc.addPage(); y = m; }
  };

  doc.setFont('helvetica', 'bold'); doc.setFontSize(14);
  texto(empresa?.nombre, m, y);
  doc.setFont('helvetica', 'normal'); doc.setFontSize(9);
  [empresa?.nit ? `NIT ${empresa.nit}` : '', unir(empresa?.direccion, empresa?.ciudad), unir(empresa?.telefono, empresa?.correo)]
    .filter(Boolean).forEach((linea, i) => texto(linea, m, y + 5 + i * 4));

  doc.setFont('helvetica', 'bold'); doc.setFontSize(12);
  texto(tituloComprobante(t, venta), ancho - m, y, { align: 'right' });
  doc.setFont('helvetica', 'normal'); doc.setFontSize(9);
  texto(`${t('comprobante.numeroInterno')}: ${venta.numeroInterno}`, ancho - m, y + 5, { align: 'right' });
  texto(`${t('comprobante.numeracion')}: ${venta.factura?.numeracionDian ?? '—'}`, ancho - m, y + 9, { align: 'right' });
  texto(`${t('comprobante.fecha')}: ${fechaHora(venta.factura?.fechaEmision || venta.fecha)}`, ancho - m, y + 13, { align: 'right' });
  y += 20;

  doc.setDrawColor(180); doc.line(m, y, ancho - m, y); salto(6);
  doc.setFont('helvetica', 'bold'); texto(t('comprobante.cliente'), m, y); doc.setFont('helvetica', 'normal');
  salto(5); texto(cliente?.nombre, m, y);
  [unir(cliente?.tipoIdentificacion, cliente?.numeroIdentificacion), unir(cliente?.direccion, cliente?.ciudad, cliente?.telefono), unir(cliente?.correo)]
    .filter(Boolean).forEach((linea) => { salto(4); texto(linea, m, y); });
  salto(7);

  // Encabezado de líneas
  const col = { desc: m, cant: 105, precio: 130, desc2: 155, total: ancho - m };
  doc.setFillColor(235, 238, 242); doc.rect(m, y - 4, ancho - 2 * m, 6, 'F');
  doc.setFont('helvetica', 'bold');
  texto(t('comprobante.descripcion'), col.desc + 1, y);
  texto(t('comprobante.cantidad'), col.cant, y, { align: 'right' });
  texto(t('comprobante.precio'), col.precio + 15, y, { align: 'right' });
  texto(t('comprobante.descuentoIva'), col.desc2 + 15, y, { align: 'right' });
  texto(t('comprobante.total'), col.total, y, { align: 'right' });
  doc.setFont('helvetica', 'normal');
  salto(6);
  (venta.lineas || []).forEach((l) => {
    const descripcion = doc.splitTextToSize(`${l.producto ?? `#${l.productoId}`} (${l.codigoBarras ?? ''})`, 85);
    texto(descripcion, col.desc + 1, y);
    const unidad = l.unidad ? `${cantidad(l.cantidadPresentacion)} ${l.unidad}` : `${cantidad(l.cantidad)} ${l.unidadBase ?? ''}`;
    texto(unidad, col.cant, y, { align: 'right' });
    texto(moneda(l.precioAplicado, 2), col.precio + 15, y, { align: 'right' });
    texto(`${moneda(l.descuento, 2)} / ${moneda(l.impuesto, 2)}`, col.desc2 + 15, y, { align: 'right' });
    texto(moneda(l.total, 2), col.total, y, { align: 'right' });
    if (l.garantiaHasta) { salto(4); doc.setFontSize(8); texto(`${t('comprobante.garantiaHasta')}: ${fecha(l.garantiaHasta)}`, col.desc + 1, y); doc.setFontSize(9); }
    salto(Math.max(5, descripcion.length * 4));
  });
  doc.line(m, y - 2, ancho - m, y - 2);
  salto(3);
  const totales = [
    [t('comprobante.subtotal'), venta.subtotal], [t('comprobante.descuentos'), venta.totalDescuentos],
    [t('comprobante.impuestos'), venta.totalImpuestos], [t('comprobante.total'), venta.total],
  ];
  totales.forEach(([e, v], i) => {
    if (i === totales.length - 1) doc.setFont('helvetica', 'bold');
    texto(e, ancho - m - 50, y); texto(moneda(v, 2), ancho - m, y, { align: 'right' });
    salto(5);
  });
  doc.setFont('helvetica', 'normal');
  if (venta.observaciones) { salto(2); texto(`${t('comprobante.observaciones')}: ${venta.observaciones}`, m, y); salto(5); }
  texto(`${t('comprobante.atendio')}: ${detalle.usuarioNombre ?? '—'}`, m, y);

  // Leyenda en el pie de cada página
  const paginas = doc.getNumberOfPages();
  for (let p = 1; p <= paginas; p += 1) {
    doc.setPage(p);
    doc.setFontSize(8); doc.setTextColor(90);
    texto(doc.splitTextToSize(leyendaComprobante(t, venta), ancho - 2 * m), m, alto - 14);
    texto(`${p}/${paginas}`, ancho - m, alto - 8, { align: 'right' });
    doc.setTextColor(0);
  }
  doc.setProperties({ title: `${tituloComprobante(t, venta)} ${venta.numeroInterno}`, subject: leyendaComprobante(t, venta) });
  return doc;
}
