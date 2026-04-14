/**
 * Parámetros configurables de la Búsqueda Tabú.
 * Se ajustan en la experimentación numérica comparativa con ALNS.
 */
public class ParametrosTS {

    /** Iteraciones máximas del ciclo principal. */
    public int    iteracionesMaximas   = 500;

    /** Capacidad de la lista tabú (movimientos prohibidos activos a la vez). */
    public int    tamanoListaTabu      = 20;

    /**
     * Vecinos generados por iteración.
     * La mitad se usa para reasignaciones y la mitad para intercambios.
     */
    public int    vecinosPorIteracion  = 40;

    /**
     * Iteraciones consecutivas sin mejora antes de detener anticipadamente.
     * Criterio de estancamiento combinado con iteracionesMaximas.
     */
    public int    iteracionesSinMejora = 80;

    /** Umbral inferior del semáforo: por encima → ÁMBAR. */
    public double umbralAmbar          = 200.0;

    /** Umbral superior del semáforo: por encima → ROJO. */
    public double umbralRojo           = 800.0;
}
