/**
 * Continentes operativos de Tasf.B2B.
 *
 * Determina el plazo máximo de entrega y la duración del traslado:
 *   Mismo continente    → plazo 2 unidades, traslado 1 unidad
 *   Distinto continente → plazo 4 unidades, traslado 2 unidades
 *
 * Una "unidad" equivale a un medio día (12 horas reales).
 */
public enum Continente {
    AMERICA,
    EUROPA,
    ASIA
}
