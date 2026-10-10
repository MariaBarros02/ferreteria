#!/usr/bin/env python3
######################################################################
# Nombre: test_taiga_metricas.py
# Descripción: Pruebas unitarias (unittest) de las métricas y del reporte de fallos hacia Taiga, sin red (cliente simulado).
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
import datetime as dt, json, os, sys, tempfile, unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "taiga"))
import metricas, taiga_client  # noqa: E402

AHORA = dt.datetime(2026, 10, 7, tzinfo=dt.timezone.utc)
DESDE = AHORA - dt.timedelta(days=30)
iso = lambda d: d.isoformat()


class SemaforoTest(unittest.TestCase):
    M_MAYOR = {"sentido": "mayor_mejor", "verde": 90, "amarillo": 75}
    M_MENOR = {"sentido": "menor_mejor", "verde": 5, "amarillo": 10}

    def test_mayor_mejor_en_los_bordes(self):
        self.assertEqual(metricas.semaforo(90, self.M_MAYOR), "verde")
        self.assertEqual(metricas.semaforo(89.9, self.M_MAYOR), "amarillo")
        self.assertEqual(metricas.semaforo(75, self.M_MAYOR), "amarillo")
        self.assertEqual(metricas.semaforo(74.9, self.M_MAYOR), "rojo")

    def test_menor_mejor_en_los_bordes(self):
        self.assertEqual(metricas.semaforo(5, self.M_MENOR), "verde")
        self.assertEqual(metricas.semaforo(10, self.M_MENOR), "amarillo")
        self.assertEqual(metricas.semaforo(11, self.M_MENOR), "rojo")

    def test_sin_datos(self):
        self.assertEqual(metricas.semaforo(None, self.M_MENOR), "sin_datos")


class CalculoTest(unittest.TestCase):
    def test_ci(self):
        r = metricas.metricas_ci([{"resultado": "ok", "segundos": 600}, {"resultado": "fallo", "segundos": 1200}])
        self.assertEqual(r["M01"], 50.0); self.assertEqual(r["M12"], 15.0)
        self.assertIsNone(metricas.metricas_ci([])["M01"])

    def test_issues(self):
        issues = [
            {"is_closed": False, "created_date": iso(AHORA - dt.timedelta(days=10)), "tags": [["produccion", None]]},
            {"is_closed": True, "created_date": iso(AHORA - dt.timedelta(hours=30)), "finished_date": iso(AHORA - dt.timedelta(hours=6)),
             "assigned_to_extra_info": {"full_name_display": "Ana"}, "tags": []},
            {"is_closed": True, "created_date": iso(AHORA - dt.timedelta(hours=60)), "finished_date": iso(AHORA - dt.timedelta(hours=12)),
             "assigned_to_extra_info": {"full_name_display": "Ana"}, "tags": []},
        ]
        v, por = metricas.metricas_issues(issues, AHORA, DESDE)
        self.assertEqual(v["M02"], 1); self.assertEqual(v["M04"], 10); self.assertEqual(v["M08"], 1)
        self.assertEqual(v["M03"], 36.0)   # mediana de 24 h y 48 h
        self.assertEqual(por, {"Ana": 2})

    def test_tareas_ignoran_fuera_de_ventana(self):
        t = [{"is_closed": True, "created_date": iso(AHORA - dt.timedelta(days=100)), "finished_date": iso(AHORA - dt.timedelta(days=90)),
              "assigned_to_extra_info": {"username": "x"}}]
        self.assertEqual(metricas.metricas_tareas(t, DESDE), {})

    def test_markdown_incluye_estado(self):
        defs = json.load(open(os.path.join(metricas.RAIZ, "metrics", "metricas.json"), encoding="utf-8"))
        md = metricas.a_markdown(metricas.evaluar(defs, {"M01": 95.0, "M02": 20}))
        self.assertIn("🟢", md); self.assertIn("🔴", md); self.assertIn("⚪", md)

    def test_definicion_de_metricas_es_consistente(self):
        defs = json.load(open(os.path.join(metricas.RAIZ, "metrics", "metricas.json"), encoding="utf-8"))
        ids = [m["id"] for m in defs["metricas"]]
        self.assertEqual(len(ids), len(set(ids)))
        for m in defs["metricas"]:
            self.assertIn(m["sentido"], ("mayor_mejor", "menor_mejor"))
            self.assertIn(m["ambito"], ("sistema", "equipo", "individual"))


class ReporteFalloTest(unittest.TestCase):
    def test_construir_reporte_incluye_contexto_y_tres_tareas(self):
        asunto, desc, tareas = taiga_client.construir_reporte("backend", "a" * 40, "main", "Ana", "a@x.com", "FAIL: algo")
        self.assertIn("backend", asunto); self.assertIn("aaaaaaaa", asunto)
        self.assertIn("bloqueado", desc); self.assertIn("FAIL: algo", desc); self.assertEqual(len(tareas), 3)

    def test_huella_estable_y_distinta_por_etapa(self):
        a = taiga_client.huella("backend", "ERROR x\nFAIL y"); b = taiga_client.huella("backend", "FAIL y\nERROR x")
        self.assertEqual(a, b); self.assertNotEqual(a, taiga_client.huella("frontend", "ERROR x\nFAIL y"))

    def test_reportar_fallo_crea_issue_tareas_y_notifica_sin_duplicar(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(taiga_client, "ESTADO_DIR", d), \
             mock.patch.dict(os.environ, {"TAIGA_NOTIFY_IDS": "1,2"}), \
             mock.patch.object(taiga_client, "mapa_usuarios", return_value={"a@x.com": 9}):
            log = os.path.join(d, "b.log"); open(log, "w").write("FAIL: prueba rota")
            fake = mock.MagicMock()
            fake.crear_issue.return_value = {"id": 77, "ref": 12}
            fake.issue_abierta.return_value = True
            args = mock.Mock(etapa="backend", sha="b" * 40, rama="main", autor="Ana", correo="a@x.com", log=log)
            with mock.patch.object(taiga_client, "cliente_desde_entorno", return_value=fake):
                taiga_client.reportar_fallo(args)
                fake.crear_issue.assert_called_once()
                self.assertEqual(fake.crear_tarea.call_count, 3)
                self.assertEqual(fake.crear_issue.call_args[0][2], [1, 2, 9])   # observadores = equipo + autor
                taiga_client.reportar_fallo(args)                                # mismo fallo: comenta, no duplica
                fake.crear_issue.assert_called_once(); fake.comentar_issue.assert_called_once()


if __name__ == "__main__":
    unittest.main()
