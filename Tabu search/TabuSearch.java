import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Búsqueda Tabú para replanificación de rutas en Tasf.B2B.
 *
 * ── Diseño ────────────────────────────────────────────────────────────────
 * Unidad de planificación : Pedido (las maletas de un pedido viajan juntas).
 * Solución inicial        : plan vigente del sistema (no se construye desde cero).
 * Tiempo                  : enteros donde cada unidad = 1 medio día.
 *
 * Vecindario (dos tipos de movimiento):
 *   REASIGNACION  : asigna un pedido elegido al azar a otra ruta disponible.
 *                   Primero rutas directas; si no hay cupo, rutas con escala.
 *   INTERCAMBIO   : swapea las rutas de dos pedidos si ambos cumplen el plazo
 *                   y la capacidad con la ruta del otro.
 *
 * Memoria:
 *   Lista tabú FIFO de capacidad τ. Prohíbe repetir el movimiento reciente.
 *   Criterio de aspiración: se permite un movimiento tabú si mejora s*.
 *
 * Criterios de parada:
 *   (a) Se alcanzan iteracionesMaximas.
 *   (b) Se acumulan iteracionesSinMejora iteraciones sin mejorar s*.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class TabuSearch implements Planificador {

    private final ParametrosTS   params;
    private final EvaluadorCosto evaluador;
    private final Random         rnd = new Random(13);

    public TabuSearch(ParametrosTS params, EvaluadorCosto evaluador) {
        this.params    = params;
        this.evaluador = evaluador;
    }

    // =========================================================================
    // Punto de entrada principal
    // =========================================================================

    @Override
    public SolucionPlan planificar(ProblemaPlanificacion problema) {

        // 1. Solución inicial: plan vigente (sección 3.3.1 del documento)
        SolucionPlan actual = problema.getSolucionInicial().copia();
        actual.setCosto(evaluador.costo(problema, actual));

        SolucionPlan mejor     = actual.copia();
        ListaTabu    listaTabu = new ListaTabu(params.tamanoListaTabu);
        int          sinMejora = 0;

        // 2. Ciclo principal
        for (int iter = 0; iter < params.iteracionesMaximas; iter++) {

            List<Movimiento> vecinos = generarVecinos(problema, actual);
            if (vecinos.isEmpty()) break;

            // 2a. Seleccionar el mejor movimiento permitido
            Movimiento mejorMov          = null;
            RutaPedido rutaAnteriorMejor = null;
            double     mejorCosto        = Double.MAX_VALUE;

            for (Movimiento m : vecinos) {
                ClaveTabu  clave    = ClaveTabu.de(m);
                boolean    esTabu   = listaTabu.esTabu(clave);
                RutaPedido rutaAntes = actual.rutaDe(m.pedido());

                // Aplicar temporalmente para evaluar
                aplicar(actual, m);
                double costoVecino = evaluador.costo(problema, actual);

                // Criterio de aspiración: se acepta aunque sea tabú
                // si mejora la mejor solución global s*
                boolean aspira = costoVecino < mejor.getCosto();

                if ((!esTabu || aspira) && costoVecino < mejorCosto) {
                    mejorCosto        = costoVecino;
                    mejorMov          = m;
                    rutaAnteriorMejor = rutaAntes;
                }

                // Siempre deshacer antes de evaluar el siguiente vecino
                deshacer(actual, m, rutaAntes);
            }

            // 2b. Aplicar el movimiento elegido definitivamente
            if (mejorMov == null) {
                sinMejora++;
            } else {
                aplicar(actual, mejorMov);
                actual.setCosto(mejorCosto);
                listaTabu.agregar(ClaveTabu.de(mejorMov));

                if (mejorCosto < mejor.getCosto()) {
                    mejor     = actual.copia();
                    sinMejora = 0;
                } else {
                    sinMejora++;
                }
            }

            // 2c. Criterio de parada por estancamiento
            if (sinMejora >= params.iteracionesSinMejora) break;
        }

        return mejor;
    }

    // =========================================================================
    // Generación del vecindario
    // =========================================================================

    private List<Movimiento> generarVecinos(ProblemaPlanificacion problema,
                                             SolucionPlan actual) {
        List<Movimiento> vecinos = new ArrayList<>();
        List<Pedido>     pedidos = problema.getPedidos();
        if (pedidos.isEmpty()) return vecinos;

        int cuota = Math.max(1, params.vecinosPorIteracion / 2);

        // ── Tipo 1: Reasignación ─────────────────────────────────────────
        Pedido elegido = pedidos.get(rnd.nextInt(pedidos.size()));
        vecinos.addAll(reasignaciones(problema, elegido, actual, cuota));

        // ── Tipo 2: Intercambio ──────────────────────────────────────────
        int intentos = 0;
        int maxIntentos = params.vecinosPorIteracion * 3;

        while (vecinos.size() < params.vecinosPorIteracion && intentos < maxIntentos) {
            intentos++;
            if (pedidos.size() < 2) break;

            Pedido p1 = pedidos.get(rnd.nextInt(pedidos.size()));
            Pedido p2 = pedidos.get(rnd.nextInt(pedidos.size()));
            if (p1.equals(p2)) continue;

            RutaPedido r1 = actual.rutaDe(p1);
            RutaPedido r2 = actual.rutaDe(p2);
            if (r1 == null || r2 == null) continue;

            // Válido si cada pedido cumple el plazo con la ruta del otro
            // y la capacidad de los vuelos es suficiente
            if (r2.cumplePlazo(p1) && r1.cumplePlazo(p2)
                    && capacidadOk(problema, actual, p1, r2)
                    && capacidadOk(problema, actual, p2, r1)) {
                vecinos.add(new MovimientoIntercambio(p1, r2, p2, r1));
            }
        }

        return vecinos;
    }

    /**
     * Genera rutas alternativas para un pedido.
     * Orden: primero directas (más rápidas), luego con escala.
     */
    private List<Movimiento> reasignaciones(ProblemaPlanificacion problema,
                                             Pedido pedido,
                                             SolucionPlan actual,
                                             int max) {
        List<Movimiento> movs      = new ArrayList<>();
        RutaPedido       rutaActual = actual.rutaDe(pedido);

        // 1. Vuelos directos
        for (Vuelo v : problema.vuelosDirectos(pedido.getOrigen(),
                                                pedido.getDestino())) {
            if (v.isCancelado()) continue;
            // No proponer la misma ruta que ya tiene
            if (rutaActual != null && v.equals(rutaActual.primerVuelo())) continue;
            // El vuelo debe salir después del ingreso del pedido
            if (v.getSalida() < pedido.getIngreso()) continue;

            RutaPedido nueva = RutaPedido.directa(v);
            if (nueva.cumplePlazo(pedido) && capacidadOk(problema, actual, pedido, nueva))
                movs.add(new Movimiento(pedido, nueva));

            if (movs.size() >= max) return movs;
        }

        // 2. Rutas con una escala (si aún hay cupo en el vecindario)
        for (RutaPedido ruta : problema.rutasConEscala(pedido)) {
            if (capacidadOk(problema, actual, pedido, ruta))
                movs.add(new Movimiento(pedido, ruta));
            if (movs.size() >= max) break;
        }

        return movs;
    }

    /**
     * Verifica que todos los vuelos de una ruta tengan capacidad libre
     * para las maletas del pedido dado.
     */
    private boolean capacidadOk(ProblemaPlanificacion problema,
                                  SolucionPlan actual,
                                  Pedido pedido,
                                  RutaPedido ruta) {
        List<Pedido> todos = problema.getPedidos();
        for (Vuelo v : ruta.getVuelos()) {
            int libre = v.getCapacidadMaxima() - actual.maletasEnVuelo(v, todos);
            if (libre < pedido.cantidadMaletas()) return false;
        }
        return true;
    }

    // =========================================================================
    // Aplicar / Deshacer (para evaluación temporal y definitiva)
    // =========================================================================

    private void aplicar(SolucionPlan s, Movimiento m) {
        if (m instanceof MovimientoIntercambio) {
            MovimientoIntercambio mi = (MovimientoIntercambio) m;
            s.asignar(mi.pedido(),  mi.nuevaRuta());
            s.asignar(mi.pedido2(), mi.nuevaRuta2());
        } else {
            s.asignar(m.pedido(), m.nuevaRuta());
        }
    }

    private void deshacer(SolucionPlan s, Movimiento m, RutaPedido rutaAnterior) {
        if (m instanceof MovimientoIntercambio) {
            MovimientoIntercambio mi = (MovimientoIntercambio) m;
            // rutaAnterior = ruta original de pedido()
            // nuevaRuta()  = ruta original de pedido2() (se la pasamos en el constructor)
            s.asignar(mi.pedido(),  rutaAnterior);
            s.asignar(mi.pedido2(), mi.nuevaRuta());
        } else {
            if (rutaAnterior != null) s.asignar(m.pedido(), rutaAnterior);
            else                      s.desasignar(m.pedido());
        }
    }
}
