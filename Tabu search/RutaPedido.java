import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Secuencia de vuelos asignada a un Pedido completo.
 *
 * Todas las maletas del pedido ocupan capacidad en TODOS los vuelos de la ruta.
 * Soporta rutas directas (1 vuelo) y rutas con una escala (2 vuelos).
 *
 * El tiempo es en enteros (medios días).
 */
public class RutaPedido {

    private final List<Vuelo> vuelos;

    public RutaPedido(List<Vuelo> vuelos) {
        if (vuelos == null || vuelos.isEmpty())
            throw new IllegalArgumentException("La ruta necesita al menos un vuelo.");
        this.vuelos = new ArrayList<>(vuelos);
    }

    // ── Constructores de conveniencia ───────────────────────────────────

    public static RutaPedido directa(Vuelo v) {
        return new RutaPedido(List.of(v));
    }

    public static RutaPedido conEscala(Vuelo v1, Vuelo v2) {
        return new RutaPedido(List.of(v1, v2));
    }

    // ── Consultas de negocio ────────────────────────────────────────────

    public Aeropuerto origen()   { return vuelos.get(0).getOrigen(); }
    public Aeropuerto destino()  { return vuelos.get(vuelos.size() - 1).getDestino(); }
    public int        salida()   { return vuelos.get(0).getSalida(); }
    public int        llegada()  { return vuelos.get(vuelos.size() - 1).getLlegada(); }
    public boolean    esDirecta(){ return vuelos.size() == 1; }

    /**
     * ¿La ruta entrega el pedido dentro del plazo?
     * Compara la llegada del último vuelo con el plazo límite del pedido.
     */
    public boolean cumplePlazo(Pedido pedido) {
        return llegada() <= pedido.plazoLimite();
    }

    /** ¿Algún vuelo de la ruta fue cancelado? */
    public boolean tieneVueloCancelado() {
        return vuelos.stream().anyMatch(Vuelo::isCancelado);
    }

    /**
     * Valida el encadenamiento lógico de la ruta:
     *   - Destino de cada vuelo = origen del siguiente.
     *   - El siguiente vuelo sale >= llegada del anterior.
     */
    public boolean esConsistente() {
        for (int i = 0; i < vuelos.size() - 1; i++) {
            Vuelo a = vuelos.get(i);
            Vuelo b = vuelos.get(i + 1);
            if (!a.getDestino().equals(b.getOrigen()))  return false;
            if (b.getSalida() < a.getLlegada())         return false;
        }
        return true;
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public List<Vuelo> getVuelos()   { return Collections.unmodifiableList(vuelos); }
    public int         numVuelos()   { return vuelos.size(); }
    public Vuelo       primerVuelo() { return vuelos.get(0); }

    public RutaPedido  copia()       { return new RutaPedido(new ArrayList<>(vuelos)); }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("Ruta[");
        for (int i = 0; i < vuelos.size(); i++) {
            if (i > 0) sb.append("→");
            sb.append(vuelos.get(i).getId());
        }
        return sb.append(" llega=t").append(llegada()).append("]").toString();
    }
}
