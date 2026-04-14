import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aeropuerto de la red Tasf.B2B (un aeropuerto por ciudad).
 *
 * ── Modelo de tiempo ──────────────────────────────────────────────────────
 * El tiempo se representa con enteros donde cada unidad = 1 medio día.
 * Ejemplo: t=0 inicio simulación, t=1 medio día después, t=2 un día, etc.
 *
 * Plazos de entrega (enunciado):
 *   Mismo continente    → 2 unidades (1 día)
 *   Distinto continente → 4 unidades (2 días)
 *
 * Capacidad del almacén: entre 500 y 800 maletas según la ciudad.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class Aeropuerto {

    private final String     codigo;
    private final String     ciudad;
    private final Continente continente;
    private final int        capacidadAlmacen;   // 500–800 maletas

    private Aeropuerto(String codigo, String ciudad,
                       Continente continente, int capacidadAlmacen) {
        this.codigo           = codigo;
        this.ciudad           = ciudad;
        this.continente       = continente;
        this.capacidadAlmacen = capacidadAlmacen;
    }

    // ── Lista fija de aeropuertos de prueba ─────────────────────────────
    // América (2)
    public static final Aeropuerto LIM = new Aeropuerto("LIM", "Lima",       Continente.AMERICA, 600);
    public static final Aeropuerto JFK = new Aeropuerto("JFK", "Nueva York", Continente.AMERICA, 800);

    // Europa (2)
    public static final Aeropuerto MAD = new Aeropuerto("MAD", "Madrid",     Continente.EUROPA,  700);
    public static final Aeropuerto CDG = new Aeropuerto("CDG", "París",      Continente.EUROPA,  750);

    // Asia (1)
    public static final Aeropuerto NRT = new Aeropuerto("NRT", "Tokio",      Continente.ASIA,    500);

    /** Lista completa de aeropuertos activos en la simulación. */
    public static final List<Aeropuerto> TODOS = Collections.unmodifiableList(
        Arrays.asList(LIM, JFK, MAD, CDG, NRT)
    );

    // ── Consultas de negocio ────────────────────────────────────────────

    /** ¿Mismo continente que otro aeropuerto? */
    public boolean mismoContinenteQue(Aeropuerto otro) {
        return this.continente == otro.continente;
    }

    /**
     * Plazo máximo de entrega en unidades (medios días).
     *   Mismo continente    → 2 unidades
     *   Distinto continente → 4 unidades
     */
    public int plazoMaximo(Aeropuerto destino) {
        return mismoContinenteQue(destino) ? 2 : 4;
    }

    /**
     * Duración estándar de traslado en unidades (medios días).
     *   Mismo continente    → 1 unidad
     *   Distinto continente → 2 unidades
     */
    public int duracionTraslado(Aeropuerto destino) {
        return mismoContinenteQue(destino) ? 1 : 2;
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public String     getCodigo()           { return codigo;           }
    public String     getCiudad()           { return ciudad;           }
    public Continente getContinente()       { return continente;       }
    public int        getCapacidadAlmacen() { return capacidadAlmacen; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Aeropuerto)) return false;
        return Objects.equals(codigo, ((Aeropuerto) o).codigo);
    }

    @Override public int    hashCode()  { return Objects.hash(codigo); }
    @Override public String toString()  { return "Aeropuerto[" + codigo + "/" + ciudad + "]"; }
}
