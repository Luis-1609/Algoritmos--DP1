import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Cargador {

    // ── Detección automática de encoding ────────────────────────────────────
    // El archivo de aeropuertos viene en UTF-16 (Windows). Probamos en orden.
    private static List<String> leerLineas(String ruta) throws IOException {
        Path p = Path.of(ruta);
        Charset[] candidatos = {
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16LE,
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_8,
                Charset.forName("windows-1252"),
                StandardCharsets.ISO_8859_1
        };
        for (Charset cs : candidatos) {
            try {
                List<String> lineas = Files.readAllLines(p, cs);
                if (!lineas.isEmpty() && lineas.get(0).length() < 1000)
                    return lineas;
            } catch (MalformedInputException e) {
                // probar siguiente
            }
        }
        return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
    }

    // Limpia BOM y nulos que introduce UTF-16
    private static String limpiar(String s) {
        return s.replaceAll("[\\x00\\uFEFF\\uFFFE]", "").trim();
    }

    // ── Aeropuertos ──────────────────────────────────────────────────────────
    // Formato: ID CODIGO Ciudad Pais alias GMT CAPACIDAD
    public static Map<String, Aeropuerto> cargarAeropuertos(String ruta) throws IOException {
        Map<String, Aeropuerto> mapa = new LinkedHashMap<>();
        for (String lineaRaw : leerLineas(ruta)) {
            String linea = limpiar(lineaRaw);
            if (linea.isEmpty() || linea.startsWith("*") || linea.startsWith("GMT")
                    || linea.contains("Am") || linea.contains("Europa")
                    || linea.contains("Asia") || linea.contains("Sur")
                    || linea.contains("DDDS"))
                continue;

            String[] p = linea.split("\\s{2,}");
            if (p.length < 7)
                continue;
            try {
                String codigo = p[1].trim();
                String ciudad = p[2].trim();
                String pais = p[3].trim();
                int gmt = Integer.parseInt(p[5].trim().replace("+", ""));
                int cap = Integer.parseInt(p[6].trim());
                if (codigo.length() >= 3 && codigo.length() <= 5)
                    mapa.put(codigo, new Aeropuerto(codigo, ciudad, pais, gmt, cap));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // ignorar linea mal formada
            }
        }
        return mapa;
    }

    // ── Vuelos ───────────────────────────────────────────────────────────────
    // Formato: ORIG-DEST-HH:MM-HH:MM-CAPACIDAD
    public static List<Vuelo> cargarVuelos(String ruta) throws IOException {
        List<Vuelo> lista = new ArrayList<>();
        for (String lineaRaw : leerLineas(ruta)) {
            String linea = limpiar(lineaRaw);
            if (linea.isEmpty())
                continue;
            String[] p = linea.split("-");
            if (p.length < 5)
                continue;
            try {
                lista.add(new Vuelo(p[0].trim(), p[1].trim(),
                        horaAMin(p[2].trim()), horaAMin(p[3].trim()),
                        Integer.parseInt(p[4].trim())));
            } catch (Exception e) {
                /* ignorar */ }
        }
        return lista;
    }

    // ── Pedidos desde un solo archivo ────────────────────────────────────────
    // Nombre esperado: _envios_XXXX_.txt donde XXXX es el código ICAO (4 letras)
    // Formato línea: id-fecha-HH-MM-dest-cantMaletas-idCliente
    public static List<Pedido> cargarPedidos(String rutaArchivo) throws IOException {
        String nombreArchivo = Path.of(rutaArchivo).getFileName().toString();
        String origen = extraerCodigoICAO(nombreArchivo);

        List<Pedido> lista = new ArrayList<>();
        for (String lineaRaw : leerLineas(rutaArchivo)) {
            String linea = limpiar(lineaRaw);
            if (linea.isEmpty())
                continue;
            String[] p = linea.split("-");
            if (p.length < 7)
                continue;
            try {
                lista.add(new Pedido(
                        p[0].trim(), // id pedido
                        origen, // origen = código del archivo
                        p[4].trim(), // destino
                        p[1].trim(), // fecha yyyyMMdd
                        Integer.parseInt(p[2].trim()) * 60
                                + Integer.parseInt(p[3].trim()), // ingreso en minutos
                        Integer.parseInt(p[5].trim()), // cantidad maletas
                        p[6].trim() // id cliente
                ));
            } catch (Exception e) {
                /* ignorar linea mal formada */ }
        }
        return lista;
    }

    // ── Carga todos los _envios_XXXX_.txt de un directorio ──────────────────
    public static List<Pedido> cargarTodosPedidos(String directorio) throws IOException {
        List<Pedido> todos = new ArrayList<>();
        File dir = new File(directorio);
        if (!dir.isDirectory()) {
            System.err.println("AVISO: '" + directorio + "' no es un directorio. Intentando como archivo.");
            return cargarPedidos(directorio);
        }
        File[] archivos = dir.listFiles((d, n) -> n.matches("_envios_[A-Z0-9]{3,5}_\\.txt"));
        if (archivos == null || archivos.length == 0) {
            System.err.println("AVISO: No se encontraron archivos _envios_XXXX_.txt en: " + directorio);
            return todos;
        }
        Arrays.sort(archivos);
        for (File f : archivos) {
            List<Pedido> pedidosArchivo = cargarPedidos(f.getPath());
            System.out.printf("   [%s] → %d pedidos%n",
                    f.getName(), pedidosArchivo.size());
            todos.addAll(pedidosArchivo);
        }
        return todos;
    }

    // ── Extrae el código ICAO del nombre del archivo ─────────────────────────
    // Ejemplos:
    // _envios_SKBO_.txt → SKBO
    // _envios_EBCI_.txt → EBCI
    // _envios_SEQM_.txt → SEQM
    // Usa regex para ser robusto frente a variaciones de nombre.
    private static String extraerCodigoICAO(String nombreArchivo) {
        // Busca 3-5 letras mayúsculas (o dígitos) entre guiones bajos
        // en el patrón _envios_XXXX_
        Pattern pat = Pattern.compile("_envios_([A-Z0-9]{3,5})_");
        Matcher mat = pat.matcher(nombreArchivo);
        if (mat.find())
            return mat.group(1);

        // Fallback: cualquier bloque de 4 mayúsculas en el nombre
        Pattern pat2 = Pattern.compile("[A-Z]{4}");
        Matcher mat2 = pat2.matcher(nombreArchivo);
        if (mat2.find())
            return mat2.group(0);

        return "UNKN";
    }

    // ── Utilidades ───────────────────────────────────────────────────────────
    private static int horaAMin(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}