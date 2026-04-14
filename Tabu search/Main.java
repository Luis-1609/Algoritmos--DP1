import java.util.*;

/**
 * Prueba del Tabú Search con el modelo definitivo de Tasf.B2B.
 *
 * ── Escenario ─────────────────────────────────────────────────────────────
 * Aeropuertos : LIM, BOG (América) | MAD, FRA (Europa) | NRT, SIN (Asia)
 * Pedidos     : 5 pedidos de distintas rutas y tamaños
 * Disrupción  : se cancela el vuelo principal LIM→MAD, forzando replan.
 *
 * Tiempo en medios días enteros (t=0 inicio de la simulación):
 *   t=0 → inicio (ej. 8:00 AM día 1)
 *   t=1 → medio día después (8:00 PM día 1)
 *   t=2 → día completo (8:00 AM día 2)
 * ──────────────────────────────────────────────────────────────────────────
 */
public class Main {

    public static void main(String[] args) {

        // ── Aeropuertos (lista fija) ────────────────────────────────────
        Aeropuerto lim = Aeropuerto.LIM;  // Lima       (América)
        Aeropuerto jfk = Aeropuerto.JFK;  // Nueva York (América)
        Aeropuerto mad = Aeropuerto.MAD;  // Madrid     (Europa)
        Aeropuerto cdg = Aeropuerto.CDG;  // París      (Europa)
        Aeropuerto nrt = Aeropuerto.NRT;  // Tokio      (Asia)

        // ── Vuelos (salida/llegada en medios días) ──────────────────────
        // Intercontinental LIM→MAD  (duración 2 unidades, plazo pedido: 4)
        // LIM→MAD intercontinental (dur. 2 unid., plazo pedido: 4)
        Vuelo v01 = new Vuelo("V01", lim, mad, 0, 2, 200);  // principal (se cancelará)
        Vuelo v02 = new Vuelo("V02", lim, mad, 1, 3, 180);  // alternativo
        Vuelo v03 = new Vuelo("V03", lim, mad, 2, 4, 160);  // tardío

        // LIM→CDG intercontinental
        Vuelo v04 = new Vuelo("V04", lim, cdg, 0, 2, 180);

        // LIM→JFK continental América (dur. 1 unid.)
        Vuelo v05 = new Vuelo("V05", lim, jfk, 0, 1, 200);

        // JFK→MAD intercontinental (ruta LIM→JFK→MAD)
        Vuelo v06 = new Vuelo("V06", jfk, mad, 2, 4, 200);

        // JFK→CDG intercontinental
        Vuelo v07 = new Vuelo("V07", jfk, cdg, 2, 4, 180);

        // LIM→NRT intercontinental
        Vuelo v08 = new Vuelo("V08", lim, nrt, 0, 2, 150);
        Vuelo v09 = new Vuelo("V09", lim, nrt, 1, 3, 150);

        // MAD→NRT  (escala LIM→MAD→NRT)
        Vuelo v10 = new Vuelo("V10", mad, nrt, 3, 5, 200);

        // CDG→NRT  (escala LIM→CDG→NRT)
        Vuelo v11 = new Vuelo("V11", cdg, nrt, 3, 5, 180);

        // CDG→MAD continental Europa (dur. 1 unid.)
        Vuelo v12 = new Vuelo("V12", cdg, mad, 3, 4, 200);

        List<Vuelo> vuelos = new ArrayList<>(
            Arrays.asList(v01, v02, v03, v04, v05, v06, v07, v08, v09, v10, v11, v12)
        );

        // ── Pedidos (ingresan en t=0) ───────────────────────────────────

        // P01: 3 maletas LIM→MAD, plazo ≤ t=4
        Pedido p01 = new Pedido("P01", lim, mad, 0, "LATAM");
        p01.agregarMaleta(new Maleta("M001", "P01"));
        p01.agregarMaleta(new Maleta("M002", "P01"));
        p01.agregarMaleta(new Maleta("M003", "P01"));

        // P02: 3 maletas LIM→MAD, plazo ≤ t=4
        Pedido p02 = new Pedido("P02", lim, mad, 0, "Iberia");
        p02.agregarMaleta(new Maleta("M004", "P02"));
        p02.agregarMaleta(new Maleta("M005", "P02"));
        p02.agregarMaleta(new Maleta("M006", "P02"));

        // P03: 2 maletas LIM→NRT, plazo ≤ t=4
        Pedido p03 = new Pedido("P03", lim, nrt, 0, "ANA");
        p03.agregarMaleta(new Maleta("M007", "P03"));
        p03.agregarMaleta(new Maleta("M008", "P03"));

        // P04: 2 maletas LIM→CDG, plazo ≤ t=4
        Pedido p04 = new Pedido("P04", lim, cdg, 0, "Air France");
        p04.agregarMaleta(new Maleta("M009", "P04"));
        p04.agregarMaleta(new Maleta("M010", "P04"));

        // P05: 4 maletas JFK→MAD, plazo ≤ t=4
        Pedido p05 = new Pedido("P05", jfk, mad, 0, "American Airlines");
        p05.agregarMaleta(new Maleta("M011", "P05"));
        p05.agregarMaleta(new Maleta("M012", "P05"));
        p05.agregarMaleta(new Maleta("M013", "P05"));
        p05.agregarMaleta(new Maleta("M014", "P05"));

        List<Pedido> pedidos = Arrays.asList(p01, p02, p03, p04, p05);

        // ── Problema y solución inicial ─────────────────────────────────
        ProblemaPlanificacion problema = new ProblemaPlanificacion(pedidos, vuelos);

        SolucionPlan inicial = new SolucionPlan();
        inicial.asignar(p01, RutaPedido.directa(v01));          // LIM→MAD directo
        inicial.asignar(p02, RutaPedido.directa(v01));          // LIM→MAD directo
        inicial.asignar(p03, RutaPedido.directa(v08));          // LIM→NRT directo
        inicial.asignar(p04, RutaPedido.directa(v04));          // LIM→CDG directo
        inicial.asignar(p05, RutaPedido.directa(v06));          // JFK→MAD directo

        problema.setSolucionInicial(inicial);

        EvaluadorCosto evaluador = new EvaluadorCosto();
        inicial.setCosto(evaluador.costo(problema, inicial));

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  TASF.B2B — BÚSQUEDA TABÚ (5 ciudades)      ║");
        System.out.println("║  LIM | JFK | MAD | CDG | NRT                ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        System.out.println("── SOLUCIÓN INICIAL ──");
        imprimirResumen(inicial, pedidos, evaluador, new ParametrosTS());
        imprimirDetalle(inicial, pedidos);

        // ── Cancelar vuelo V01 y mostrar impacto ────────────────────────
        System.out.println("\n>>> Cancelando V01 (LIM→MAD t=0→2)...\n");
        problema.cancelarVuelo("V01");
        inicial.setCosto(evaluador.costo(problema, inicial));

        System.out.println("── TRAS CANCELACIÓN ──");
        imprimirResumen(inicial, pedidos, evaluador, new ParametrosTS());

        // ── Ejecutar Tabú Search ────────────────────────────────────────
        ParametrosTS params = new ParametrosTS();
        params.iteracionesMaximas   = 400;
        params.tamanoListaTabu      = 15;
        params.vecinosPorIteracion  = 30;
        params.iteracionesSinMejora = 60;
        params.umbralAmbar          = 300.0;
        params.umbralRojo           = 1000.0;

        TabuSearch ts = new TabuSearch(params, evaluador);

        System.out.println("\n>>> Ejecutando Búsqueda Tabú...\n");
        long t0     = System.currentTimeMillis();
        SolucionPlan replan = ts.planificar(problema);
        long tiempo = System.currentTimeMillis() - t0;

        System.out.println("\n── SOLUCIÓN REPLANIFICADA ──");
        System.out.println("   Tiempo de ejecución : " + tiempo + " ms");
        imprimirResumen(replan, pedidos, evaluador, params);
        imprimirDetalle(replan, pedidos);
    }

        imprimirDetalle(replan, pedidos);
    }

    private static void imprimirResumen(SolucionPlan sol, List<Pedido> pedidos,
                                         EvaluadorCosto eval, ParametrosTS params) {
        double costo = sol.getCosto();
        EvaluadorCosto.Semaforo s = eval.semaforo(costo, params.umbralAmbar, params.umbralRojo);
        String icono = s == EvaluadorCosto.Semaforo.VERDE ? "● VERDE  (sin violaciones)"
                     : s == EvaluadorCosto.Semaforo.AMBAR ? "● ÁMBAR  (riesgo leve)"
                     : "● ROJO   (violación crítica)";
        System.out.printf("   Pedidos cubiertos : %d/%d%n",
            sol.getPedidosCubiertos(), pedidos.size());
        System.out.printf("   Costo             : %.1f%n", costo);
        System.out.println("   Semáforo          : " + icono);
    }

    private static void imprimirDetalle(SolucionPlan sol, List<Pedido> pedidos) {
        System.out.println("   Detalle:");
        for (Pedido p : pedidos) {
            RutaPedido r = sol.rutaDe(p);
            if (r == null) {
                System.out.printf("     %-4s (%d mal.) → SIN RUTA ✗%n",
                    p.getId(), p.cantidadMaletas());
            } else {
                String estado = r.cumplePlazo(p)
                    ? "OK  [llega t=" + r.llegada() + " ≤ plazo t=" + p.plazoLimite() + "]"
                    : "✗   [llega t=" + r.llegada() + " > plazo t=" + p.plazoLimite() + "]";
                System.out.printf("     %-4s (%d mal.) → %-22s %s%n",
                    p.getId(), p.cantidadMaletas(), r.toString(), estado);
            }
        }
    }
}
