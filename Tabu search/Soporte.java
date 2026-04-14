import java.util.*;

// =============================================================================
// Interfaz Planificador
// =============================================================================

/**
 * Contrato común para todos los algoritmos (SA, ALNS, Tabú Search).
 * Permite intercambiarlos en la experimentación numérica comparativa.
 */
interface Planificador {
    SolucionPlan planificar(ProblemaPlanificacion problema);
}


// =============================================================================
// EvaluadorCosto
// =============================================================================

/**
 * Calcula el costo de una solución según la función objetivo de Tasf.B2B.
 *
 * costo = Σ penalizaciones por:
 *   1. Pedidos sin ruta o con vuelo cancelado   (PEN_SIN_RUTA  × pedido)
 *   2. Pedidos con plazo incumplido             (PEN_PLAZO     × pedido)
 *   3. Exceso de maletas sobre capacidad vuelo  (PEN_CAPACIDAD × maleta extra)
 *   4. Exceso de maletas sobre capacidad almacén(PEN_ALMACEN   × maleta extra)
 */
class EvaluadorCosto {

    public double costo(ProblemaPlanificacion problema, SolucionPlan sol) {
        double pen      = 0.0;
        int    cubiertos = 0;

        List<Pedido>     pedidos     = problema.getPedidos();
        List<Vuelo>      vuelos      = problema.getVuelos();
        List<Aeropuerto> aeropuertos = problema.getAeropuertos();

        // 1 y 2: cobertura y plazos
        for (Pedido p : pedidos) {
            RutaPedido r = sol.rutaDe(p);
            if (r == null || r.tieneVueloCancelado() || !r.esConsistente()) {
                pen += SolucionPlan.PEN_SIN_RUTA;
                continue;
            }
            cubiertos++;
            if (!r.cumplePlazo(p)) pen += SolucionPlan.PEN_PLAZO;
        }

        // 3: capacidad de vuelos
        for (Vuelo v : vuelos) {
            if (v.isCancelado()) continue;
            int exceso = sol.maletasEnVuelo(v, pedidos) - v.getCapacidadMaxima();
            if (exceso > 0) pen += exceso * SolucionPlan.PEN_CAPACIDAD;
        }

        // 4: capacidad de almacenes de escala
        for (Aeropuerto ap : aeropuertos) {
            int exceso = sol.maletasEnAlmacen(ap, pedidos) - ap.getCapacidadAlmacen();
            if (exceso > 0) pen += exceso * SolucionPlan.PEN_ALMACEN;
        }

        sol.setPedidosCubiertos(cubiertos);
        return pen;
    }

    /**
     * Estado semáforo según umbrales configurables (parámetros del sistema).
     *   VERDE : costo == 0     → sin violaciones
     *   AMBAR : 0 < costo < umbralRojo  → riesgo leve
     *   ROJO  : costo >= umbralRojo     → violación crítica
     */
    public Semaforo semaforo(double costo, double umbralAmbar, double umbralRojo) {
        if (costo <= 0)         return Semaforo.VERDE;
        if (costo < umbralRojo) return Semaforo.AMBAR;
        return Semaforo.ROJO;
    }

    public enum Semaforo { VERDE, AMBAR, ROJO }
}


// =============================================================================
// ClaveTabu
// =============================================================================

/**
 * Identifica un movimiento en la lista tabú.
 * Clave = (idPedido, idPrimerVuelo de la nueva ruta).
 * Almacena strings para evitar referencias a objetos mutables.
 */
class ClaveTabu {

    private final String idPedido;
    private final String idVuelo;

    public ClaveTabu(String idPedido, String idVuelo) {
        this.idPedido = idPedido;
        this.idVuelo  = idVuelo;
    }

    /** Construye la clave a partir de un movimiento. */
    public static ClaveTabu de(Movimiento m) {
        return new ClaveTabu(
            m.pedido().getId(),
            m.nuevaRuta().primerVuelo().getId()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClaveTabu)) return false;
        ClaveTabu c = (ClaveTabu) o;
        return idPedido.equals(c.idPedido) && idVuelo.equals(c.idVuelo);
    }

    @Override public int    hashCode()  { return Objects.hash(idPedido, idVuelo); }
    @Override public String toString()  { return "TabuKey[" + idPedido + "→" + idVuelo + "]"; }
}


// =============================================================================
// ListaTabu
// =============================================================================

/**
 * Lista tabú con política FIFO y capacidad máxima configurable.
 *
 * Usa Deque (orden de expiración) + HashSet (consulta O(1)) sincronizados.
 */
class ListaTabu {

    private final int                   capacidad;
    private final ArrayDeque<ClaveTabu> cola;
    private final HashSet<ClaveTabu>    indice;

    public ListaTabu(int capacidad) {
        this.capacidad = capacidad;
        this.cola      = new ArrayDeque<>(capacidad + 1);
        this.indice    = new HashSet<>(capacidad * 2);
    }

    /** Registra un movimiento como tabú. Expira el más antiguo si hay exceso. */
    public void agregar(ClaveTabu clave) {
        if (indice.contains(clave)) return;
        cola.addLast(clave);
        indice.add(clave);
        if (cola.size() > capacidad) indice.remove(cola.removeFirst());
    }

    public boolean esTabu(ClaveTabu clave) { return indice.contains(clave); }
    public void    limpiar()               { cola.clear(); indice.clear();  }
    public int     size()                  { return cola.size();            }
}


// =============================================================================
// Movimiento
// =============================================================================

/**
 * Movimiento de reasignación: asigna un pedido a una nueva ruta.
 * Unidad atómica del vecindario de la Búsqueda Tabú.
 */
class Movimiento {

    private final Pedido     pedido;
    private final RutaPedido nuevaRuta;

    public Movimiento(Pedido pedido, RutaPedido nuevaRuta) {
        this.pedido    = pedido;
        this.nuevaRuta = nuevaRuta;
    }

    public Pedido     pedido()    { return pedido;    }
    public RutaPedido nuevaRuta() { return nuevaRuta; }

    @Override public String toString() {
        return "Mov[" + pedido.getId() + "→" + nuevaRuta + "]";
    }
}


// =============================================================================
// MovimientoIntercambio
// =============================================================================

/**
 * Movimiento de intercambio: swapea las rutas de dos pedidos.
 *
 * Semántica:
 *   pedido()  se asigna a nuevaRuta()   (que era la ruta de pedido2)
 *   pedido2() se asigna a nuevaRuta2()  (que era la ruta de pedido)
 *
 * Al deshacer:
 *   pedido()  vuelve a rutaAnterior (guardada antes de aplicar)
 *   pedido2() vuelve a nuevaRuta()  (que era su ruta original antes del swap)
 */
class MovimientoIntercambio extends Movimiento {

    private final Pedido     pedido2;
    private final RutaPedido nuevaRuta2;

    public MovimientoIntercambio(Pedido p1, RutaPedido r1,
                                 Pedido p2, RutaPedido r2) {
        super(p1, r1);
        this.pedido2    = p2;
        this.nuevaRuta2 = r2;
    }

    public Pedido     pedido2()    { return pedido2;    }
    public RutaPedido nuevaRuta2() { return nuevaRuta2; }

    @Override public String toString() {
        return "Intercambio[" + pedido().getId() + "↔" + pedido2.getId() + "]";
    }
}
