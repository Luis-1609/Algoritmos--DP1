import java.util.*;

/**
 * Solución del planificador: mapeo de cada Pedido a su RutaPedido asignada.
 *
 * La unidad de planificación es el PEDIDO. La capacidad se mide en maletas
 * (suma de cantidadMaletas() de todos los pedidos que usan un vuelo).
 *
 * ── Función objetivo (lexicográfica) ─────────────────────────────────────
 *   Prioridad 1: maximizar pedidos cubiertos  → penaliza pedidos sin ruta
 *   Prioridad 2: minimizar penalizaciones     → plazo, capacidad, almacén
 *
 * Una solución perfecta tiene costo = 0.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class SolucionPlan {

    // Pesos de penalización (ajustables en experimentación numérica)
    public static final double PEN_SIN_RUTA  = 1000.0;
    public static final double PEN_PLAZO     =  500.0;
    public static final double PEN_CAPACIDAD =  200.0;
    public static final double PEN_ALMACEN   =  100.0;

    private final Map<String, RutaPedido> asignaciones; // idPedido → ruta
    private double costo;
    private int    pedidosCubiertos;

    public SolucionPlan() {
        this.asignaciones    = new LinkedHashMap<>();
        this.costo           = Double.MAX_VALUE;
        this.pedidosCubiertos = 0;
    }

    // ── Operaciones de asignación ───────────────────────────────────────

    public void asignar(Pedido pedido, RutaPedido ruta) {
        asignaciones.put(pedido.getId(), ruta);
    }

    public void desasignar(Pedido pedido) {
        asignaciones.remove(pedido.getId());
    }

    public RutaPedido rutaDe(Pedido pedido) {
        return asignaciones.get(pedido.getId());
    }

    public boolean tieneAsignacion(Pedido pedido) {
        return asignaciones.containsKey(pedido.getId());
    }

    // ── Consultas de ocupación ──────────────────────────────────────────

    /**
     * Total de maletas que ocupan un vuelo específico.
     * Suma cantidadMaletas() de cada pedido cuya ruta contiene ese vuelo.
     */
    public int maletasEnVuelo(Vuelo vuelo, List<Pedido> pedidos) {
        int total = 0;
        for (Pedido p : pedidos) {
            RutaPedido r = asignaciones.get(p.getId());
            if (r != null && r.getVuelos().contains(vuelo))
                total += p.cantidadMaletas();
        }
        return total;
    }

    /**
     * Maletas en espera en un aeropuerto de escala intermedia.
     * Solo cuenta escalas (destino de un vuelo que no es el destino final).
     */
    public int maletasEnAlmacen(Aeropuerto ap, List<Pedido> pedidos) {
        int total = 0;
        for (Pedido p : pedidos) {
            RutaPedido r = asignaciones.get(p.getId());
            if (r == null) continue;
            List<Vuelo> vs = r.getVuelos();
            for (int i = 0; i < vs.size() - 1; i++) {
                if (vs.get(i).getDestino().equals(ap))
                    total += p.cantidadMaletas();
            }
        }
        return total;
    }

    // ── Copia profunda ──────────────────────────────────────────────────

    public SolucionPlan copia() {
        SolucionPlan c = new SolucionPlan();
        for (Map.Entry<String, RutaPedido> e : asignaciones.entrySet())
            c.asignaciones.put(e.getKey(), e.getValue().copia());
        c.costo            = this.costo;
        c.pedidosCubiertos = this.pedidosCubiertos;
        return c;
    }

    // ── Getters / setters ───────────────────────────────────────────────

    public double getCosto()                  { return costo;            }
    public void   setCosto(double c)          { this.costo = c;          }
    public int    getPedidosCubiertos()       { return pedidosCubiertos; }
    public void   setPedidosCubiertos(int n)  { pedidosCubiertos = n;    }
    public int    totalAsignados()            { return asignaciones.size(); }

    public Map<String, RutaPedido> getAsignaciones() {
        return Collections.unmodifiableMap(asignaciones);
    }

    @Override public String toString() {
        return "SolucionPlan[pedidos=" + asignaciones.size()
             + " costo=" + String.format("%.1f", costo) + "]";
    }
}
