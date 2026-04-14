import java.util.Objects;

/**
 * Vuelo operado por Tasf.B2B entre dos aeropuertos.
 *
 * ── Modelo de tiempo ──────────────────────────────────────────────────────
 * salida y llegada son enteros que representan medios días desde t=0.
 *   t=0 → inicio simulación
 *   t=1 → medio día después
 *   t=2 → un día completo
 *
 * Duraciones estándar del enunciado:
 *   Mismo continente    → 1 unidad de duración
 *   Distinto continente → 2 unidades de duración
 *
 * Capacidades por tipo de ruta:
 *   Mismo continente    → 150–250 maletas
 *   Distinto continente → 150–400 maletas
 * ──────────────────────────────────────────────────────────────────────────
 */
public class Vuelo {

    private final String     id;
    private final Aeropuerto origen;
    private final Aeropuerto destino;
    private final int        salida;          // unidades desde t=0
    private final int        llegada;         // unidades desde t=0
    private final int        capacidadMaxima; // maletas
    private boolean          cancelado;

    public Vuelo(String id, Aeropuerto origen, Aeropuerto destino,
                 int salida, int llegada, int capacidadMaxima) {
        this.id              = id;
        this.origen          = origen;
        this.destino         = destino;
        this.salida          = salida;
        this.llegada         = llegada;
        this.capacidadMaxima = capacidadMaxima;
        this.cancelado       = false;
    }

    // ── Consultas ───────────────────────────────────────────────────────

    public boolean esMismoContinente() {
        return origen.mismoContinenteQue(destino);
    }

    public int duracion() {
        return llegada - salida;
    }

    // ── Getters / setters ───────────────────────────────────────────────

    public String     getId()              { return id;              }
    public Aeropuerto getOrigen()          { return origen;          }
    public Aeropuerto getDestino()         { return destino;         }
    public int        getSalida()          { return salida;          }
    public int        getLlegada()         { return llegada;         }
    public int        getCapacidadMaxima() { return capacidadMaxima; }
    public boolean    isCancelado()        { return cancelado;       }
    public void       setCancelado(boolean b) { this.cancelado = b;  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vuelo)) return false;
        return Objects.equals(id, ((Vuelo) o).id);
    }

    @Override public int    hashCode()  { return Objects.hash(id); }
    @Override public String toString()  {
        return "Vuelo[" + id + " " + origen.getCodigo()
             + "→" + destino.getCodigo()
             + " t=" + salida + "→" + llegada
             + (cancelado ? " CANCELADO" : "") + "]";
    }
}
