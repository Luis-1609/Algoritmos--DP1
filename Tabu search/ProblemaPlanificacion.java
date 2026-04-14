import java.util.*;

/**
 * Contenedor del problema de planificación de Tasf.B2B.
 *
 * Aeropuertos: lista fija {@link Aeropuerto#TODOS} (6 ciudades de prueba).
 * Pedidos y vuelos: cargados al construir el problema.
 *
 * Provee utilidades de consulta para el vecindario y el evaluador.
 */
public class ProblemaPlanificacion {

    private final List<Pedido> pedidos;
    private final List<Vuelo>  vuelos;
    private SolucionPlan       solucionInicial;

    public ProblemaPlanificacion(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos         = new ArrayList<>(pedidos);
        this.vuelos          = new ArrayList<>(vuelos);
        this.solucionInicial = new SolucionPlan();
    }

    // ── Consultas de vuelos ─────────────────────────────────────────────

    /** Vuelos no cancelados con ese origen y destino exactos. */
    public List<Vuelo> vuelosDirectos(Aeropuerto origen, Aeropuerto destino) {
        List<Vuelo> out = new ArrayList<>();
        for (Vuelo v : vuelos) {
            if (!v.isCancelado()
                    && v.getOrigen().equals(origen)
                    && v.getDestino().equals(destino))
                out.add(v);
        }
        return out;
    }

    /** Vuelos no cancelados que salen desde un aeropuerto. */
    public List<Vuelo> vuelosActivosDesde(Aeropuerto origen) {
        List<Vuelo> out = new ArrayList<>();
        for (Vuelo v : vuelos) {
            if (!v.isCancelado() && v.getOrigen().equals(origen))
                out.add(v);
        }
        return out;
    }

    /**
     * Genera rutas con UNA escala para un pedido.
     * Solo incluye rutas que cumplan el plazo y cuyo segundo vuelo
     * salga >= llegada del primero.
     */
    public List<RutaPedido> rutasConEscala(Pedido pedido) {
        List<RutaPedido> rutas = new ArrayList<>();
        Aeropuerto origen  = pedido.getOrigen();
        Aeropuerto destino = pedido.getDestino();

        for (Vuelo v1 : vuelosActivosDesde(origen)) {
            Aeropuerto escala = v1.getDestino();
            if (escala.equals(destino)) continue; // sería ruta directa

            for (Vuelo v2 : vuelosDirectos(escala, destino)) {
                if (v2.getSalida() < v1.getLlegada()) continue; // mal encadenado
                RutaPedido r = RutaPedido.conEscala(v1, v2);
                if (r.cumplePlazo(pedido)) rutas.add(r);
            }
        }
        return rutas;
    }

    /** Marca un vuelo como cancelado por su ID. */
    public void cancelarVuelo(String idVuelo) {
        for (Vuelo v : vuelos) {
            if (v.getId().equals(idVuelo)) {
                v.setCancelado(true);
                return;
            }
        }
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public List<Pedido>     getPedidos()          { return Collections.unmodifiableList(pedidos); }
    public List<Vuelo>      getVuelos()           { return Collections.unmodifiableList(vuelos);  }
    public List<Aeropuerto> getAeropuertos()      { return Aeropuerto.TODOS;                      }
    public SolucionPlan     getSolucionInicial()  { return solucionInicial;                       }
    public void setSolucionInicial(SolucionPlan s){ this.solucionInicial = s;                     }

    @Override public String toString() {
        return "Problema[pedidos=" + pedidos.size() + " vuelos=" + vuelos.size() + "]";
    }
}
