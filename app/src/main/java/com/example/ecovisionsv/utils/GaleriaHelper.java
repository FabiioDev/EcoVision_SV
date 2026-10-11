package com.example.ecovisionsv.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Las URIs del Photo Picker (content://media/picker/...) solo tienen permiso de lectura
 * mientras vive el proceso que las recibió. Para que el historial pueda abrirlas
 * después, la imagen se copia a Pictures/EcoVisionSV, igual que las fotos de la cámara.
 */
public class GaleriaHelper {

    private static final String CARPETA_DESTINO = "EcoVisionSV";

    private GaleriaHelper() {
    }

    /** true si la URI ya es una imagen de MediaStore creada por la app (p. ej. la cámara). */
    public static boolean esUriPersistente(Uri uri) {
        if ("file".equals(uri.getScheme())) return true;
        return MediaStore.AUTHORITY.equals(uri.getAuthority())
                && !uri.getPath().startsWith("/picker");
    }

    /**
     * Copia la imagen a la galería de la app y devuelve la nueva URI.
     *
     * @throws IOException si no se puede leer el origen o escribir la copia
     */
    public static Uri copiarAGaleria(Context context, Uri origen) throws IOException {
        String nombre = "ECOVISION_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis())
                + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return copiarMediaStore(context, origen, nombre);
        }
        return copiarLegacy(context, origen, nombre);
    }

    private static Uri copiarMediaStore(Context context, Uri origen, String nombre) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/" + CARPETA_DESTINO);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri destino = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (destino == null) throw new IOException("No se pudo crear la imagen en la galería");

        try (InputStream in = resolver.openInputStream(origen);
             OutputStream out = resolver.openOutputStream(destino)) {
            copiar(in, out);
        } catch (IOException | RuntimeException e) {
            resolver.delete(destino, null, null);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }

        cv.clear();
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(destino, cv, null, null);
        return destino;
    }

    /** API < 29: sin RELATIVE_PATH; se usa la carpeta Pictures de la app (no requiere permisos). */
    private static Uri copiarLegacy(Context context, Uri origen, String nombre) throws IOException {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), CARPETA_DESTINO);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo crear " + dir);

        File archivo = new File(dir, nombre);
        try (InputStream in = context.getContentResolver().openInputStream(origen);
             OutputStream out = new FileOutputStream(archivo)) {
            copiar(in, out);
        }
        return Uri.fromFile(archivo);
    }

    private static void copiar(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) throw new IOException("No se pudo abrir la imagen");
        byte[] buffer = new byte[8192];
        int leidos;
        while ((leidos = in.read(buffer)) != -1) {
            out.write(buffer, 0, leidos);
        }
    }
}
