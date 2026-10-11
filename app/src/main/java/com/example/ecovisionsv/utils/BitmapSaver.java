package com.example.ecovisionsv.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapSaver {

    private static final String CARPETA_BBOX = "resultados";
    private static final int JPEG_QUALITY = 90;

    private BitmapSaver() {
    }

    /**
     * Guarda [bitmap] como JPEG en el almacenamiento privado de la app (files/resultados).
     * No aparece en la galería, no requiere permisos y se borra junto con la BD
     * al limpiar los datos de la app.
     *
     * @return URI del archivo guardado, o null si falla.
     */
    public static Uri guardar(Context context, Bitmap bitmap) {
        File dir = new File(context.getFilesDir(), CARPETA_BBOX);
        if (!dir.exists() && !dir.mkdirs()) return null;

        File archivo = new File(dir, "ECOVISION_BBOX_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(archivo)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
        } catch (IOException e) {
            return null;
        }

        return Uri.fromFile(archivo);
    }
}
