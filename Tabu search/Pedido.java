import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// =============================================================================
// Maleta
// =============================================================================

/**
 * Unidad física de envío. Medidas y peso estandarizados (enunciado Tasf.B2B).
 *
 * Una maleta pertenece a exactamente un Pedido.
 * No tiene origen/destino propio: esos datos viven en el Pedido al que
 * pertenece. Las maletas de un pedido SIEMPRE viajan juntas en la misma ruta.
 */
class Maleta {

    private final String id;
    private final String idPedido;

    public Maleta(String id, String idPedido) {
        this.id       = id;
        this.idPedido = idPedido;
    }

    public String getId()       { return id;       }
    public String getIdPedido() { return idPedido; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Maleta)) return false;
        return Objects.equals(id, ((Maleta) o).id);
    }

    @Override public int    hashCode()  { return Objects.hash(id);          }
    @Override public String toString()  { return "Maleta[" + id + "]";      }
}


// =============================================================================
// Pedido
// =============================================================================

/**
 * Pedido de envío realizado por una aerolínea cliente a Tasf.B2B.
 *
 * Agrupa una o más maletas que VIAJAN JUNTAS en la misma RutaPedido.
 * La unidad de planificación del algoritmo es el Pedido, no la Maleta.
 *
 * ── Modelo de tiempo ──────────────────────────────────────────────────────
 * ingreso: entero (medios días) en que la aerolínea entrega las maletas.
 *
 * Plazos:
 *   Mismo continente    → ingreso + 2 unidades
 *   Distinto continente → ingreso + 4 unidades
 * ──────────────────────────────────────────────────────────────────────────
 */
public class Pedido {

    private final String       id;
    private final Aeropuerto   origen;
    private final Aeropuerto   destino;
    private final int          ingreso;           // medios días desde t=0
    private final String       aerolineaCliente;
    private final List<Maleta> maletas;

    public Pedido(String id, Aeropuerto origen, Aeropuerto destino,
                  int ingreso, String aerolineaCliente) {
        this.id               = id;
        this.origen           = origen;
        this.destino          = destino;
        this.ingreso          = ingreso;
        this.aerolineaCliente = aerolineaCliente;
        this.maletas          = new ArrayList<>();
    }

    // ── Gestión de maletas ──────────────────────────────────────────────

    public void agregarMaleta(Maleta m) { maletas.add(m); }
    public int  cantidadMaletas()       { return maletas.size(); }

    // ── Consultas de negocio ────────────────────────────────────────────

    /**
     * Plazo límite absoluto en medios días.
     *   Mismo continente    → ingreso + 2
     *   Distinto continente → ingreso + 4
     */
    public int plazoLimite() {
        return ingreso + origen.plazoMaximo(destino);
    }

    public boolean esIntercontinental() {
        return !origen.mismoContinenteQue(destino);
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public String       getId()               { return id;               }
    public Aeropuerto   getOrigen()           { return origen;           }
    public Aeropuerto   getDestino()          { return destino;          }
    public int          getIngreso()          { return ingreso;          }
    public String       getAerolineaCliente() { return aerolineaCliente; }
    public List<Maleta> getMaletas()          { return Collections.unmodifiableList(maletas); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido)) return false;
        return Objects.equals(id, ((Pedido) o).id);
    }

    @Override public int    hashCode()  { return Objects.hash(id); }
    @Override public String toString()  {
        return "Pedido[" + id + " " + origen.getCodigo()
             + "→" + destino.getCodigo()
             + " x" + maletas.size()
             + " plazo≤t" + plazoLimite() + "]";
    }
}
