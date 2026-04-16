import java.util.*;

// =============================================================================
// SolucionPlan: mapeo pedido → ruta
// =============================================================================
class SolucionPlan {

    // Penalizaciones (ajustables)
    static final double PEN_SIN_RUTA  = 1000.0;
    static final double PEN_PLAZO     =  500.0;
    static final double PEN_CAPACIDAD =  200.0;

    private final Map<String, RutaPedido> asignaciones = new LinkedHashMap<>();
    double costo = Double.MAX_VALUE;
    int    pedidosCubiertos = 0;

    void asignar(Pedido p, RutaPedido r)  { asignaciones.put(p.id, r); }
    void desasignar(Pedido p)             { asignaciones.remove(p.id); }
    RutaPedido rutaDe(Pedido p)           { return asignaciones.get(p.id); }
    boolean    tieneAsignacion(Pedido p)  { return asignaciones.containsKey(p.id); }

    // Maletas totales que usan un vuelo dado
    int maletasEnVuelo(Vuelo v, List<Pedido> pedidos) {
        int total = 0;
        for (Pedido p : pedidos) {
            RutaPedido r = asignaciones.get(p.id);
            if (r != null && r.vuelos.contains(v)) total += p.cantMaletas;
        }
        return total;
    }

    SolucionPlan copia() {
        SolucionPlan c = new SolucionPlan();
        asignaciones.forEach((k, v) -> c.asignaciones.put(k, v.copia()));
        c.costo            = this.costo;
        c.pedidosCubiertos = this.pedidosCubiertos;
        return c;
    }

    Map<String,RutaPedido> getAsignaciones() {
        return Collections.unmodifiableMap(asignaciones);
    }

    @Override public String toString() {
        return String.format("Sol[asignados=%d costo=%.1f]",
            asignaciones.size(), costo);
    }
}

// =============================================================================
// EvaluadorCosto
// =============================================================================
class EvaluadorCosto {

    private final Map<String,Aeropuerto> aeropuertos;

    EvaluadorCosto(Map<String,Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos;
    }

    double costo(SolucionPlan sol, List<Pedido> pedidos, List<Vuelo> vuelos) {
        double pen = 0;
        int    cub = 0;

        for (Pedido p : pedidos) {
            RutaPedido r = sol.rutaDe(p);
            if (r == null || r.tieneVueloCancelado() || !r.esConsistente()) {
                pen += SolucionPlan.PEN_SIN_RUTA;
                continue;
            }
            cub++;
            if (!r.cumplePlazo(p, aeropuertos)) pen += SolucionPlan.PEN_PLAZO;
        }

        // Capacidad de vuelos
        for (Vuelo v : vuelos) {
            if (v.cancelado) continue;
            int exceso = sol.maletasEnVuelo(v, pedidos) - v.capacidad;
            if (exceso > 0) pen += exceso * SolucionPlan.PEN_CAPACIDAD;
        }

        sol.pedidosCubiertos = cub;
        sol.costo            = pen;
        return pen;
    }

    // Semáforo según umbrales configurables
    enum Semaforo { VERDE, AMBAR, ROJO }

    Semaforo semaforo(double costo, double umbAmbar, double umbRojo) {
        if (costo <= 0)          return Semaforo.VERDE;
        if (costo < umbRojo)     return Semaforo.AMBAR;
        return Semaforo.ROJO;
    }
}
