import java.util.*;

// =============================================================================
// HeuristicaConstructiva
// Genera la solución inicial (sección 3.3.1 del documento).
// Para cada pedido busca el mejor vuelo directo disponible (greedy),
// o una ruta con escala si no hay directo dentro del plazo.
// =============================================================================
public class HeuristicaConstructiva {

    private final Map<String,Aeropuerto> aeropuertos;

    HeuristicaConstructiva(Map<String,Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos;
    }

    SolucionPlan construir(List<Pedido> pedidos, List<Vuelo> vuelos) {
        SolucionPlan sol = new SolucionPlan();

        // Índice de vuelos por origen para acceso rápido
        Map<String, List<Vuelo>> porOrigen = new HashMap<>();
        for (Vuelo v : vuelos) {
            porOrigen.computeIfAbsent(v.origen, k -> new ArrayList<>()).add(v);
        }

        for (Pedido p : pedidos) {
            RutaPedido mejor = mejorRutaDirecta(p, porOrigen, sol, pedidos);
            if (mejor == null) {
                mejor = mejorRutaConEscala(p, porOrigen, sol, pedidos);
            }
            if (mejor != null) sol.asignar(p, mejor);
        }

        return sol;
    }

    // ── Ruta directa: vuelo con mismos origen/destino, sale después del ingreso,
    //    dentro del plazo, con capacidad disponible ────────────────────────────
    RutaPedido mejorRutaDirecta(Pedido p,
                                 Map<String,List<Vuelo>> porOrigen,
                                 SolucionPlan sol,
                                 List<Pedido> pedidos) {
        List<Vuelo> candidatos = porOrigen.getOrDefault(p.origen, List.of());
        RutaPedido mejor = null;
        int mejorLlegada = Integer.MAX_VALUE;

        for (Vuelo v : candidatos) {
            if (v.cancelado)              continue;
            if (!v.destino.equals(p.destino)) continue;
            if (v.salidaMin < p.ingresoMin)   continue;  // sale antes de que llegue

            RutaPedido ruta = RutaPedido.directa(v);
            if (!ruta.cumplePlazo(p, aeropuertos))       continue;

            int libre = v.capacidad - sol.maletasEnVuelo(v, pedidos);
            if (libre < p.cantMaletas)                   continue;

            if (v.llegadaMin < mejorLlegada) {
                mejorLlegada = v.llegadaMin;
                mejor = ruta;
            }
        }
        return mejor;
    }

    // ── Ruta con UNA escala: busca aeropuerto intermedio ─────────────────────
    RutaPedido mejorRutaConEscala(Pedido p,
                                   Map<String,List<Vuelo>> porOrigen,
                                   SolucionPlan sol,
                                   List<Pedido> pedidos) {
        List<Vuelo> tramos1 = porOrigen.getOrDefault(p.origen, List.of());
        RutaPedido mejor = null;
        int mejorLlegada = Integer.MAX_VALUE;

        for (Vuelo v1 : tramos1) {
            if (v1.cancelado || v1.salidaMin < p.ingresoMin) continue;
            if (v1.destino.equals(p.destino)) continue; // sería directo

            int libre1 = v1.capacidad - sol.maletasEnVuelo(v1, pedidos);
            if (libre1 < p.cantMaletas) continue;

            List<Vuelo> tramos2 = porOrigen.getOrDefault(v1.destino, List.of());
            for (Vuelo v2 : tramos2) {
                if (v2.cancelado)                     continue;
                if (!v2.destino.equals(p.destino))    continue;
                if (v2.salidaMin < v1.llegadaMin)     continue; // mal encadenado

                int libre2 = v2.capacidad - sol.maletasEnVuelo(v2, pedidos);
                if (libre2 < p.cantMaletas) continue;

                RutaPedido ruta = RutaPedido.conEscala(v1, v2);
                if (!ruta.cumplePlazo(p, aeropuertos)) continue;

                if (ruta.llegadaMin() < mejorLlegada) {
                    mejorLlegada = ruta.llegadaMin();
                    mejor = ruta;
                }
            }
        }
        return mejor;
    }
}
