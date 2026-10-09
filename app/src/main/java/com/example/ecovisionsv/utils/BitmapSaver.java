package com.example.ecovisionsv.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BitmapSaver {

    private static final String CARPETA_BBOX = "EcoVisionSV_Resultados";
    private static final int JPEG_QUALITY = 90;

    private BitmapSaver() {
    }

    /**
     * Guarda [bitmap] como JPEG en Pictures/EcoVisionSV_Resultados.
     *
     * @return URI del archivo guardado, o null si falla.
     */
    public static Uri guardar(Context context, Bitmap bitmap) {
        String nombre = "ECOVISION_BBOX_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date())
                + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return guardarMediaStore(context, bitmap, nombre);
        } else {
            return guardarLegacy(context, bitmap, nombre);
        }
    }

    private static Uri guardarMediaStore(Context context, Bitmap bitmap, String nombre) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, nombre);
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/" + CARPETA_BBOX);
        cv.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = context.getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) return null;

        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) return null;
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        } catch (IOException e) {
            context.getContentResolver().delete(uri, null, null);
            return null;
        }

        cv.clear();
        cv.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, cv, null, null);

        // .nomedia: evita que MediaStore indexe la carpeta como álbum público
        crearNomediaMediaStore(context);

        return uri;
    }

    private static Uri guardarLegacy(Context context, Bitmap bitmap, String nombre) {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                CARPETA_BBOX);
        if (!dir.exists() && !dir.mkdirs()) return null;

        // .nomedia para que la galería no indexe esta carpeta
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try { nomedia.createNewFile(); } catch (IOException ignored) { }
        }

        File archivo = new File(dir, nombre);
        try (FileOutputStream fos = new FileOutputStream(archivo)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
        } catch (IOException e) {
            return null;
        }

        return Uri.fromFile(archivo);
    }

    private static void crearNomediaMediaStore(Context context) {
        // Solo necesario una vez; si ya existe no hace nada.
        // Se usa una query simple para comprobarlo.
        android.database.Cursor c = context.getContentResolver().query(
                MediaStore.Files.getContentUri("external"),
                new String[]{MediaStore.Files.FileColumns._ID},
                MediaStore.Files.FileColumns.DISPLAY_NAME + "=? AND "
                        + MediaStore.Files.FileColumns.RELATIVE_PATH + "=?",
                new String[]{".nomedia", "Pictures/" + CARPETA_BBOX + "/"},
                null);
        boolean existe = (c != null && c.getCount() > 0);
        if (c != null) c.close();
        if (existe) return;

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Files.FileColumns.DISPLAY_NAME, ".nomedia");
        cv.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream");
        cv.put(MediaStore.Files.FileColumns.RELATIVE_PATH,
                "Pictures/" + CARPETA_BBOX + "/");
        context.getContentResolver()
                .insert(MediaStore.Files.getContentUri("external"), cv);
    }

}
