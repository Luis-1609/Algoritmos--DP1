import java.util.*;

// =============================================================================
// Parámetros de la Búsqueda Tabú
// =============================================================================
class ParametrosTS {
    int    iterMaximas      = 300;
    int    tamanoListaTabu  = 20;
    int    vecinosPorIter   = 30;
    int    maxSinMejora     = 60;
    double umbralAmbar      = 500.0;
    double umbralRojo       = 2000.0;
}

// =============================================================================
// Clave tabú: identifica un movimiento (idPedido + idVuelo destino)
// =============================================================================
class ClaveTabu {
    final String idPedido;
    final int    idVuelo;

    ClaveTabu(String idPedido, int idVuelo) {
        this.idPedido = idPedido;
        this.idVuelo  = idVuelo;
    }

    static ClaveTabu de(Movimiento m) {
        return new ClaveTabu(m.pedido.id, m.nuevaRuta.primerVuelo().id);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ClaveTabu c)) return false;
        return idPedido.equals(c.idPedido) && idVuelo == c.idVuelo;
    }
    @Override public int hashCode() { return Objects.hash(idPedido, idVuelo); }
}

// =============================================================================
// Lista tabú FIFO + HashSet O(1)
// =============================================================================
class ListaTabu {
    private final int               cap;
    private final ArrayDeque<ClaveTabu> cola;
    private final HashSet<ClaveTabu>    idx;

    ListaTabu(int cap) {
        this.cap  = cap;
        this.cola = new ArrayDeque<>(cap+1);
        this.idx  = new HashSet<>(cap*2);
    }

    void agregar(ClaveTabu c) {
        if (idx.contains(c)) return;
        cola.addLast(c); idx.add(c);
        if (cola.size() > cap) idx.remove(cola.removeFirst());
    }

    boolean esTabu(ClaveTabu c) { return idx.contains(c); }
    void    limpiar()           { cola.clear(); idx.clear(); }
}

// =============================================================================
// Movimiento de reasignación
// =============================================================================
class Movimiento {
    final Pedido     pedido;
    final RutaPedido nuevaRuta;
    Movimiento(Pedido p, RutaPedido r) { this.pedido = p; this.nuevaRuta = r; }
}

// =============================================================================
// TabuSearch
// =============================================================================
public class TabuSearch {

    private final ParametrosTS           params;
    private final EvaluadorCosto         eval;
    private final Map<String,Aeropuerto> aeropuertos;
    private final HeuristicaConstructiva heuristica;
    private final Random                 rnd = new Random(42);

    TabuSearch(ParametrosTS params, EvaluadorCosto eval,
               Map<String,Aeropuerto> aeropuertos) {
        this.params      = params;
        this.eval        = eval;
        this.aeropuertos = aeropuertos;
        this.heuristica  = new HeuristicaConstructiva(aeropuertos);
    }

    // ── Punto de entrada ────────────────────────────────────────────────────
    SolucionPlan planificar(SolucionPlan inicial,
                             List<Pedido> pedidos,
                             List<Vuelo>  vuelos) {

        SolucionPlan actual = inicial.copia();
        eval.costo(actual, pedidos, vuelos);

        SolucionPlan mejor     = actual.copia();
        ListaTabu    listaTabu = new ListaTabu(params.tamanoListaTabu);
        int          sinMejora = 0;

        // Índice de vuelos por origen para generación de vecindario
        Map<String,List<Vuelo>> porOrigen = new HashMap<>();
        for (Vuelo v : vuelos) {
            porOrigen.computeIfAbsent(v.origen, k -> new ArrayList<>()).add(v);
        }

        for (int iter = 0; iter < params.iterMaximas; iter++) {

            List<Movimiento> vecinos = generarVecinos(actual, pedidos, vuelos, porOrigen);
            if (vecinos.isEmpty()) break;

            Movimiento   mejorMov   = null;
            RutaPedido   rutaAnte   = null;
            double       mejorCosto = Double.MAX_VALUE;

            for (Movimiento m : vecinos) {
                ClaveTabu clave   = ClaveTabu.de(m);
                boolean   esTabu  = listaTabu.esTabu(clave);
                RutaPedido antes  = actual.rutaDe(m.pedido);

                // Aplicar temporalmente
                actual.asignar(m.pedido, m.nuevaRuta);
                double c = eval.costo(actual, pedidos, vuelos);

                boolean aspira = c < mejor.costo; // criterio de aspiración

                if ((!esTabu || aspira) && c < mejorCosto) {
                    mejorCosto = c;
                    mejorMov   = m;
                    rutaAnte   = antes;
                }
                // Deshacer
                if (antes != null) actual.asignar(m.pedido, antes);
                else               actual.desasignar(m.pedido);
            }

            if (mejorMov == null) {
                sinMejora++;
            } else {
                actual.asignar(mejorMov.pedido, mejorMov.nuevaRuta);
                eval.costo(actual, pedidos, vuelos);
                listaTabu.agregar(ClaveTabu.de(mejorMov));

                if (actual.costo < mejor.costo) {
                    mejor     = actual.copia();
                    sinMejora = 0;
                } else {
                    sinMejora++;
                }
            }

            if (sinMejora >= params.maxSinMejora) break;
        }

        return mejor;
    }

    // ── Vecindario: reasignaciones de un pedido elegido al azar ─────────────
    private List<Movimiento> generarVecinos(SolucionPlan sol,
                                             List<Pedido>  pedidos,
                                             List<Vuelo>   vuelos,
                                             Map<String,List<Vuelo>> porOrigen) {
        List<Movimiento> vecinos = new ArrayList<>();
        if (pedidos.isEmpty()) return vecinos;

        Pedido p = pedidos.get(rnd.nextInt(pedidos.size()));

        // 1. Rutas directas alternativas
        RutaPedido actual = sol.rutaDe(p);
        for (Vuelo v : porOrigen.getOrDefault(p.origen, List.of())) {
            if (v.cancelado || !v.destino.equals(p.destino)) continue;
            if (v.salidaMin < p.ingresoMin)                  continue;
            if (actual != null && v == actual.primerVuelo())  continue;

            RutaPedido ruta = RutaPedido.directa(v);
            if (!ruta.cumplePlazo(p, aeropuertos))           continue;
            int libre = v.capacidad - sol.maletasEnVuelo(v, pedidos);
            if (libre < p.cantMaletas)                        continue;

            vecinos.add(new Movimiento(p, ruta));
            if (vecinos.size() >= params.vecinosPorIter/2)   break;
        }

        // 2. Rutas con escala
        if (vecinos.size() < params.vecinosPorIter) {
            RutaPedido escala = heuristica.mejorRutaConEscala(p, porOrigen, sol, pedidos);
            if (escala != null) vecinos.add(new Movimiento(p, escala));
        }

        return vecinos;
    }
}
