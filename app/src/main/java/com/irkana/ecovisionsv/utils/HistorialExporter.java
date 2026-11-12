package com.irkana.ecovisionsv.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.irkana.ecovisionsv.BuildConfig;
import com.irkana.ecovisionsv.database.DeteccionEntity;
import com.irkana.ecovisionsv.database.EcoVisionDatabase;
import com.irkana.ecovisionsv.model.Deteccion;
import com.irkana.ecovisionsv.model.RespuestaPrediccion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporta el historial completo a un ZIP para la prueba de campo:
 * <pre>
 * imagenes.csv      una fila por imagen, con el conteo por categoría
 * detecciones.csv   una fila por objeto detectado
 * resultados.json   resultado_json crudo de cada registro
 * manifest.json     versión de la app, totales y archivos faltantes
 * originales/{id}   foto original copiada byte a byte desde su URI
 * bbox/{id}.jpg     imagen con bboxes de files/resultados
 * </pre>
 * Los CSV se derivan de resultado_json igual que el resumen del historial,
 * así que no requiere cambios en la BD. Hace I/O pesado: llamar fuera del hilo principal.
 */
public final class HistorialExporter {

    public interface Progreso {
        void onProgreso(int actual, int total);
    }

    public static final class Resultado {
        public final int totalDetecciones;
        public final int faltantes;

        Resultado(int totalDetecciones, int faltantes) {
            this.totalDetecciones = totalDetecciones;
            this.faltantes = faltantes;
        }
    }

    private static final int BUFFER = 64 * 1024;

    private HistorialExporter() {
    }

    /** Nombre sugerido para el ZIP, p. ej. "P03_2026-10-20.zip". */
    public static String nombreArchivo(String participante) {
        return participante + "_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".zip";
    }

    public static Resultado exportar(Context context, Uri destino, String participante,
                                     Progreso progreso) throws IOException {
        List<DeteccionEntity> lista = EcoVisionDatabase.getInstance(context)
                .deteccionDao().obtenerTodos();
        // La BD devuelve del más reciente al más antiguo; el CSV va en orden cronológico
        lista.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

        ContentResolver resolver = context.getContentResolver();
        Gson gson = new Gson();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);

        // Parsear una sola vez; null si el JSON falta o está corrupto
        List<RespuestaPrediccion> respuestas = new ArrayList<>();
        TreeSet<String> categorias = new TreeSet<>();
        for (DeteccionEntity e : lista) {
            RespuestaPrediccion r = null;
            if (e.resultadoJson != null) {
                try {
                    r = gson.fromJson(e.resultadoJson, RespuestaPrediccion.class);
                } catch (RuntimeException ignored) {
                    // se reporta como faltante en el manifest
                }
            }
            respuestas.add(r);
            if (r != null) {
                for (Deteccion d : r.deteccionesFiltradas()) categorias.add(categoriaDe(d));
            }
        }

        JsonArray faltantes = new JsonArray();
        Map<Long, String> archivosOriginales = new HashMap<>();
        Map<Long, String> archivosBbox = new HashMap<>();

        OutputStream salida = resolver.openOutputStream(destino);
        if (salida == null) throw new IOException("No se pudo abrir el archivo de destino");

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(salida, BUFFER))) {
            // Los JPG ya vienen comprimidos: deflate no reduce su tamaño y solo gasta CPU
            zip.setLevel(Deflater.NO_COMPRESSION);
            byte[] buffer = new byte[BUFFER];
            for (int i = 0; i < lista.size(); i++) {
                if (progreso != null) progreso.onProgreso(i, lista.size());
                DeteccionEntity e = lista.get(i);

                String original = copiarImagen(resolver, zip, buffer, e.imagenUri,
                        "originales/" + e.id, e.id, "original", faltantes);
                if (original != null) archivosOriginales.put(e.id, original);

                String bbox = copiarImagen(resolver, zip, buffer, e.imagenBboxUri,
                        "bbox/" + e.id, e.id, "bbox", faltantes);
                if (bbox != null) archivosBbox.put(e.id, bbox);

                if (respuestas.get(i) == null) {
                    agregarFaltante(faltantes, e.id, "resultado_json", "no_disponible");
                }
            }
            if (progreso != null) progreso.onProgreso(lista.size(), lista.size());

            zip.setLevel(Deflater.DEFAULT_COMPRESSION);

            // imagenes.csv
            StringBuilder csv = new StringBuilder();
            List<String> cabecera = new ArrayList<>();
            cabecera.add("participante");
            cabecera.add("id");
            cabecera.add("fecha");
            cabecera.add("timestamp_ms");
            cabecera.add("num_objetos");
            for (String c : categorias) cabecera.add("n_" + c);
            cabecera.add("ancho");
            cabecera.add("alto");
            cabecera.add("umbral");
            cabecera.add("latencia_ms");
            cabecera.add("archivo_original");
            cabecera.add("archivo_bbox");
            filaCsv(csv, cabecera);

            int totalObjetos = 0;
            for (int i = 0; i < lista.size(); i++) {
                DeteccionEntity e = lista.get(i);
                RespuestaPrediccion r = respuestas.get(i);
                List<Deteccion> detecciones = r != null ? r.deteccionesFiltradas() : null;

                Map<String, Integer> conteo = new HashMap<>();
                if (detecciones != null) {
                    for (Deteccion d : detecciones) {
                        String c = categoriaDe(d);
                        Integer n = conteo.get(c);
                        conteo.put(c, n == null ? 1 : n + 1);
                    }
                }
                int numObjetos = detecciones != null ? detecciones.size() : e.numDetecciones;
                totalObjetos += numObjetos;

                List<String> fila = new ArrayList<>();
                fila.add(participante);
                fila.add(String.valueOf(e.id));
                fila.add(iso.format(new Date(e.timestamp)));
                fila.add(String.valueOf(e.timestamp));
                fila.add(String.valueOf(numObjetos));
                for (String c : categorias) {
                    // Sin JSON no se conoce el desglose: se deja vacío en lugar de 0
                    Integer n = conteo.get(c);
                    fila.add(detecciones == null ? "" : String.valueOf(n == null ? 0 : n));
                }
                fila.add(String.valueOf(e.anchoImagen));
                fila.add(String.valueOf(e.altoImagen));
                fila.add(r != null ? decimal(r.umbral, 2) : "");
                fila.add(decimal(e.latenciaMs, 1));
                fila.add(valorOVacio(archivosOriginales.get(e.id)));
                fila.add(valorOVacio(archivosBbox.get(e.id)));
                filaCsv(csv, fila);
            }
            escribirTexto(zip, "imagenes.csv", csv.toString());

            // detecciones.csv: n coincide con la numeración [1], [2]… que muestra la app
            csv.setLength(0);
            filaCsv(csv, Arrays.asList("participante", "id_imagen", "n", "categoria", "clase_id",
                    "confianza", "xmin_px", "ymin_px", "xmax_px", "ymax_px",
                    "xmin_norm", "ymin_norm", "xmax_norm", "ymax_norm"));
            for (int i = 0; i < lista.size(); i++) {
                RespuestaPrediccion r = respuestas.get(i);
                if (r == null) continue;
                List<Deteccion> detecciones = r.deteccionesFiltradas();
                for (int n = 0; n < detecciones.size(); n++) {
                    Deteccion d = detecciones.get(n);
                    List<String> fila = new ArrayList<>();
                    fila.add(participante);
                    fila.add(String.valueOf(lista.get(i).id));
                    fila.add(String.valueOf(n + 1));
                    fila.add(categoriaDe(d));
                    fila.add(String.valueOf(d.claseId));
                    fila.add(decimal(d.confianza, 4));
                    for (int k = 0; k < 4; k++) {
                        fila.add(d.bboxPx != null && d.bboxPx.size() == 4
                                ? String.valueOf(d.bboxPx.get(k)) : "");
                    }
                    for (int k = 0; k < 4; k++) {
                        fila.add(d.bboxNorm != null && d.bboxNorm.size() == 4
                                ? decimal(d.bboxNorm.get(k), 6) : "");
                    }
                    filaCsv(csv, fila);
                }
            }
            escribirTexto(zip, "detecciones.csv", csv.toString());

            // resultados.json
            JsonArray resultados = new JsonArray();
            for (DeteccionEntity e : lista) {
                JsonObject o = new JsonObject();
                o.addProperty("id", e.id);
                o.addProperty("timestamp_ms", e.timestamp);
                o.add("resultado", parsearJson(e.resultadoJson));
                resultados.add(o);
            }
            escribirTexto(zip, "resultados.json", gson.toJson(resultados));

            // manifest.json
            JsonObject manifest = new JsonObject();
            manifest.addProperty("participante", participante);
            manifest.addProperty("fecha_exportacion", iso.format(new Date()));
            manifest.addProperty("version_app", BuildConfig.VERSION_NAME);
            manifest.addProperty("total_detecciones", lista.size());
            manifest.addProperty("total_objetos", totalObjetos);
            manifest.addProperty("imagenes_originales", archivosOriginales.size());
            manifest.addProperty("imagenes_bbox", archivosBbox.size());
            manifest.add("faltantes", faltantes);
            escribirTexto(zip, "manifest.json",
                    new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        }

        return new Resultado(lista.size(), faltantes.size());
    }

    /**
     * Copia el contenido de [uri] tal cual (resolución completa y EXIF) a una entrada del ZIP.
     *
     * @return ruta dentro del ZIP, o null si la imagen no está disponible.
     */
    private static String copiarImagen(ContentResolver resolver, ZipOutputStream zip, byte[] buffer,
                                       String uriTexto, String rutaSinExtension, long id,
                                       String tipo, JsonArray faltantes) throws IOException {
        if (uriTexto == null) {
            agregarFaltante(faltantes, id, tipo, "sin_uri");
            return null;
        }
        Uri uri = Uri.parse(uriTexto);
        InputStream entrada;
        try {
            // Se abre antes de crear la entrada para no dejar archivos vacíos en el ZIP
            entrada = resolver.openInputStream(uri);
        } catch (IOException | SecurityException ex) {
            // Foto borrada de la galería o URI del Photo Picker sin permiso
            agregarFaltante(faltantes, id, tipo, ex.getClass().getSimpleName());
            return null;
        }
        if (entrada == null) {
            agregarFaltante(faltantes, id, tipo, "no_se_pudo_abrir");
            return null;
        }

        String ruta = rutaSinExtension + "." + extensionDe(resolver, uri);
        try (InputStream in = entrada) {
            zip.putNextEntry(new ZipEntry(ruta));
            int leidos;
            while ((leidos = in.read(buffer)) != -1) zip.write(buffer, 0, leidos);
            zip.closeEntry();
        }
        return ruta;
    }

    private static String extensionDe(ContentResolver resolver, Uri uri) {
        String mime = resolver.getType(uri);
        String ext = mime != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) : null;
        if (ext == null) ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        return ext == null || ext.isEmpty() ? "jpg" : ext.toLowerCase(Locale.US);
    }

    private static void agregarFaltante(JsonArray faltantes, long id, String archivo, String motivo) {
        JsonObject f = new JsonObject();
        f.addProperty("id", id);
        f.addProperty("archivo", archivo);
        f.addProperty("motivo", motivo);
        faltantes.add(f);
    }

    private static JsonElement parsearJson(String json) {
        if (json == null) return JsonNull.INSTANCE;
        try {
            return JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return JsonNull.INSTANCE;
        }
    }

    private static void escribirTexto(ZipOutputStream zip, String ruta, String contenido)
            throws IOException {
        zip.putNextEntry(new ZipEntry(ruta));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String categoriaDe(Deteccion d) {
        return d.categoria != null ? d.categoria : "—";
    }

    private static String valorOVacio(String s) {
        return s != null ? s : "";
    }

    /** Punto decimal siempre, sin importar el idioma del dispositivo. */
    private static String decimal(float valor, int decimales) {
        return String.format(Locale.US, "%." + decimales + "f", valor);
    }

    private static void filaCsv(StringBuilder sb, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(',');
            String v = valores.get(i);
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('\n');
    }
}
