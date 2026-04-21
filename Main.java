import java.util.*;
import java.io.*;

// =============================================================================
// Main — MVP Búsqueda Tabú para Tasf.B2B
//
// USO:
//   java Main [aeropuertos.txt] [planes_vuelo.txt] [directorio_envios] [maxPedidos]
//
// Valores por defecto (misma carpeta que el .class):
//   aeropuertos : c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt
//   vuelos      : planes_vuelo.txt
//   envios      : _envios_SKBO_.txt   (un solo archivo de prueba)
//   maxPedidos  : 200 (limitar para el MVP de presentación)
// =============================================================================
public class Main {

    public static void main(String[] args) throws Exception {

        // ── Argumentos o valores por defecto ─────────────────────────────
        String rutaAp = args.length > 0 ? args[0]
                : "c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt";
        String rutaVuel = args.length > 1 ? args[1] : "planes_vuelo.txt";
        String rutaEnv = args.length > 2 ? args[2] : "_envios_SKBO_.txt";
        int maxPed = args.length > 3 ? Integer.parseInt(args[3]) : 200;

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   TASF.B2B — MVP BÚSQUEDA TABÚ (Replanificación)    ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        // ── 1. Cargar datos ───────────────────────────────────────────────
        System.out.println("── Cargando datos...");
        Map<String, Aeropuerto> aeropuertos = Cargador.cargarAeropuertos(rutaAp);
        System.out.printf("   Aeropuertos cargados : %d%n", aeropuertos.size());

        List<Vuelo> vuelos = Cargador.cargarVuelos(rutaVuel);
        System.out.printf("   Vuelos cargados      : %d%n", vuelos.size());

        List<Pedido> pedidos;
        File f = new File(rutaEnv);
        if (f.isDirectory()) {
            pedidos = Cargador.cargarTodosPedidos(rutaEnv);
        } else {
            pedidos = Cargador.cargarPedidos(rutaEnv);
        }
        // Limitar para el MVP
        if (pedidos.size() > maxPed)
            pedidos = pedidos.subList(0, maxPed);
        System.out.printf("   Pedidos cargados     : %d (max=%d)%n\n", pedidos.size(), maxPed);

        if (aeropuertos.isEmpty() || vuelos.isEmpty() || pedidos.isEmpty()) {
            System.err.println("ERROR: No se pudieron cargar los datos. Verifique las rutas.");
            System.err.println("  Aeropuertos: " + rutaAp);
            System.err.println("  Vuelos:      " + rutaVuel);
            System.err.println("  Envíos:      " + rutaEnv);
            System.exit(1);
        }

        // ── 2. Heurística constructiva (solución inicial) ─────────────────
        System.out.println("── Generando solución inicial (heurística constructiva)...");
        EvaluadorCosto eval = new EvaluadorCosto(aeropuertos);
        HeuristicaConstructiva hc = new HeuristicaConstructiva(aeropuertos);

        long t0 = System.currentTimeMillis();
        SolucionPlan inicial = hc.construir(pedidos, vuelos);
        eval.costo(inicial, pedidos, vuelos);
        long tHC = System.currentTimeMillis() - t0;

        ParametrosTS params = new ParametrosTS();
        imprimirResumen("SOLUCIÓN INICIAL", inicial, pedidos, eval, params, tHC);

        // ── 3. Tabú Search ────────────────────────────────────────────────
        System.out.println("\n── Ejecutando Búsqueda Tabú...");
        System.out.printf("   Parámetros: iterMax=%d tamTabu=%d vecinos=%d sinMejora=%d%n\n",
                params.iterMaximas, params.tamanoListaTabu,
                params.vecinosPorIter, params.maxSinMejora);

        TabuSearch ts = new TabuSearch(params, eval, aeropuertos);
        t0 = System.currentTimeMillis();
        SolucionPlan resultado = ts.planificar(inicial, pedidos, vuelos);
        long tTS = System.currentTimeMillis() - t0;

        imprimirResumen("SOLUCIÓN TABÚ SEARCH", resultado, pedidos, eval, params, tTS);

        // ── 4. Detalle de asignaciones ────────────────────────────────────
        System.out.println("\n── Detalle de asignaciones (primeros 20):");
        int mostrados = 0;
        for (Pedido p : pedidos) {
            if (mostrados++ >= 20)
                break;
            RutaPedido r = resultado.rutaDe(p);
            if (r == null) {
                System.out.printf("  %-12s %s->%s x%d mal. -> SIN RUTA ✗%n",
                        p.id, p.origen, p.destino, p.cantMaletas);
            } else {
                boolean ok = r.cumplePlazo(p, aeropuertos);
                System.out.printf("  %-12s %s->%s x%d mal. -> %-30s %s%n",
                        p.id, p.origen, p.destino, p.cantMaletas,
                        r.toString(), ok ? "OK" : "PLAZO INCUMPLIDO");
            }
        }
        if (pedidos.size() > 20)
            System.out.printf("  ... (%d pedidos más)%n", pedidos.size() - 20);

        // ── 5. Comparación ────────────────────────────────────────────────
        System.out.println("\n── Comparación:");
        System.out.printf("   Pedidos cubiertos: %d -> %d (+%d)%n",
                inicial.pedidosCubiertos, resultado.pedidosCubiertos,
                resultado.pedidosCubiertos - inicial.pedidosCubiertos);
        System.out.printf("   Costo:             %.1f -> %.1f (mejora: %.1f%%)%n",
                inicial.costo, resultado.costo,
                inicial.costo > 0
                        ? (1.0 - resultado.costo / inicial.costo) * 100
                        : 0.0);
    }

    // ── Utilidad de impresión ─────────────────────────────────────────────

    static void imprimirResumen(String titulo, SolucionPlan sol,
            List<Pedido> pedidos, EvaluadorCosto eval,
            ParametrosTS params, long ms) {
        EvaluadorCosto.Semaforo sem = eval.semaforo(sol.costo, params.umbralAmbar, params.umbralRojo);
        String icono = sem == EvaluadorCosto.Semaforo.VERDE ? "● VERDE  (sin violaciones)"
                : sem == EvaluadorCosto.Semaforo.AMBAR ? "● ÁMBAR  (riesgo leve)"
                        : "● ROJO   (violaciones críticas)";
        System.out.printf("── %s (%.0f ms)%n", titulo, (double) ms);
        System.out.printf("   Pedidos cubiertos : %d / %d%n",
                sol.pedidosCubiertos, pedidos.size());
        System.out.printf("   Costo total       : %.1f%n", sol.costo);
        System.out.printf("   Semáforo          : %s%n", icono);
    }
}