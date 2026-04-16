import java.util.*;

// =============================================================================
// Aeropuerto
// =============================================================================
// Cargado desde: c.1inf54.26.1.v1.Aeropuerto.husos.v1...txt
// Formato:  ID   CODIGO   Ciudad   Pais   alias   GMT   CAPACIDAD_ALMACEN
// Ejemplo:  01   SKBO     Bogotá   Colombia  bogo  -5   430
// =============================================================================
class Aeropuerto {
    final String codigo;
    final String ciudad;
    final String pais;
    final int gmtOffset; // horas respecto a UTC
    final int capacidadAlmacen; // maletas

    Aeropuerto(String codigo, String ciudad, String pais, int gmt, int cap) {
        this.codigo = codigo;
        this.ciudad = ciudad;
        this.pais = pais;
        this.gmtOffset = gmt;
        this.capacidadAlmacen = cap;
    }

    @Override
    public String toString() {
        return codigo;
    }
}

// =============================================================================
// Vuelo
// =============================================================================
// Cargado desde: planes_vuelo.txt
// Formato: ORIG-DEST-HH:MM_salida-HH:MM_llegada-CAPACIDAD
// Ejemplo: SKBO-SEQM-03:34-04:21-0300
// Los tiempos son locales del aeropuerto de origen.
// Convertimos todo a minutos desde medianoche para facilitar comparaciones.
// Si llegada < salida, el vuelo cruza medianoche -> llegada += 1440 min.
// =============================================================================
class Vuelo {
    static int idCounter = 1;

    final int id;
    final String origen;
    final String destino;
    final int salidaMin; // minutos desde 00:00 (UTC+0 normalizado)
    final int llegadaMin; // minutos desde 00:00 (puede ser > 1440 si cruza medianoche)
    final int capacidad; // maletas máximas
    boolean cancelado = false;

    Vuelo(String origen, String destino, int salidaMin, int llegadaMin, int capacidad) {
        this.id = idCounter++;
        this.origen = origen;
        this.destino = destino;
        this.salidaMin = salidaMin;
        // Si llega antes de que sale -> cruza medianoche
        this.llegadaMin = (llegadaMin < salidaMin) ? llegadaMin + 1440 : llegadaMin;
        this.capacidad = capacidad;
    }

    int duracionMin() {
        return llegadaMin - salidaMin;
    }

    @Override
    public String toString() {
        return String.format("V%d[%s->%s %02d:%02d->%02d:%02d cap=%d%s]",
                id, origen, destino,
                salidaMin / 60, salidaMin % 60,
                (llegadaMin % 1440) / 60, (llegadaMin % 1440) % 60,
                capacidad, cancelado ? " CANCEL" : "");
    }
}

// =============================================================================
// Pedido
// =============================================================================
// Cargado desde: _envios_[CODIGO].txt
// Formato: id_pedido-fecha-HH-MM-dest-cantMaletas-idCliente
// Ejemplo: 000000001-20260102-00-55-SPIM-002-0019169
// aeropuertoOrigen = el código del archivo (e.g. SKBO)
// ingreso en minutos desde medianoche del día de la fecha
// =============================================================================
class Pedido {
    final String id;
    final String origen; // aeropuerto donde se entrega el pedido a Tasf.B2B
    final String destino;
    final String fecha; // yyyyMMdd
    final int ingresoMin; // minutos desde 00:00 del día de ingreso
    final int cantMaletas;
    final String idCliente;

    Pedido(String id, String origen, String destino, String fecha,
            int ingresoMin, int cantMaletas, String idCliente) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.fecha = fecha;
        this.ingresoMin = ingresoMin;
        this.cantMaletas = cantMaletas;
        this.idCliente = idCliente;
    }

    // Plazo en minutos desde medianoche:
    // mismo continente (GMT similar) -> +1440 min (1 día)
    // distinto continente -> +2880 min (2 días)
    // Simplificación MVP: si |gmtOrigen - gmtDestino| <= 4 -> mismo continente
    int plazoMin(Map<String, Aeropuerto> aeropuertos) {
        Aeropuerto ap = aeropuertos.get(origen);
        Aeropuerto ad = aeropuertos.get(destino);
        if (ap == null || ad == null)
            return ingresoMin + 2880;
        int diff = Math.abs(ap.gmtOffset - ad.gmtOffset);
        return ingresoMin + (diff <= 4 ? 1440 : 2880);
    }

    @Override
    public String toString() {
        return String.format("Ped[%s %s->%s x%d]", id, origen, destino, cantMaletas);
    }
}

// =============================================================================
// RutaPedido
// =============================================================================
// Secuencia de 1 o 2 vuelos asignada a un Pedido.
// Todas las maletas del pedido viajan juntas.
// =============================================================================
class RutaPedido {
    final List<Vuelo> vuelos;

    RutaPedido(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos);
    }

    static RutaPedido directa(Vuelo v) {
        return new RutaPedido(List.of(v));
    }

    static RutaPedido conEscala(Vuelo v1, Vuelo v2) {
        return new RutaPedido(List.of(v1, v2));
    }

    Vuelo primerVuelo() {
        return vuelos.get(0);
    }

    int llegadaMin() {
        return vuelos.get(vuelos.size() - 1).llegadaMin;
    }

    boolean cumplePlazo(Pedido p, Map<String, Aeropuerto> aps) {
        return llegadaMin() <= p.plazoMin(aps);
    }

    boolean tieneVueloCancelado() {
        return vuelos.stream().anyMatch(v -> v.cancelado);
    }

    boolean esConsistente() {
        for (int i = 0; i < vuelos.size() - 1; i++) {
            Vuelo a = vuelos.get(i), b = vuelos.get(i + 1);
            if (!a.destino.equals(b.origen))
                return false;
            if (b.salidaMin < a.llegadaMin)
                return false;
        }
        return true;
    }

    RutaPedido copia() {
        return new RutaPedido(new ArrayList<>(vuelos));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ruta[");
        for (int i = 0; i < vuelos.size(); i++) {
            if (i > 0)
                sb.append("->");
            sb.append(vuelos.get(i).origen).append("->").append(vuelos.get(i).destino);
        }
        return sb.append("]").toString();
    }
}