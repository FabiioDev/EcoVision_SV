package com.example.ecovisionsv.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Redimensiona y comprime imágenes del dispositivo a 640×640 JPEG
 * antes de enviarlas al servidor de inferencia.
 */
public class ImageResizer {
    public static final int TARGET_SIZE = 640;
    private static final int JPEG_QUALITY = 85; // balance calidad / tamaño

    private ImageResizer() { /* estático */ }

    /**
     * Lee la imagen desde [uri], corrige la rotación EXIF, escala a
     * 640×640 y la devuelve como array de bytes JPEG listos para subir.
     *
     * @throws IOException si no se puede leer la Uri o la imagen está corrupta.
     */
    public static byte[] resizeToBytes(Context context, Uri uri) throws IOException {
        Bitmap original = decodeSampledBitmap(context, uri);
        Bitmap rotated  = correctRotation(context, uri, original);
        Bitmap scaled   = Bitmap.createScaledBitmap(rotated, TARGET_SIZE, TARGET_SIZE, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);

        // Liberar memoria explícitamente
        if (rotated != original) rotated.recycle();
        if (scaled  != rotated)  scaled.recycle();
        original.recycle();

        return out.toByteArray();
    }

    /**
     * Decodifica el bitmap con inSampleSize calculado para no cargar
     * la imagen completa en memoria cuando es muy grande (>4 MP).
     */
    private static Bitmap decodeSampledBitmap(Context context, Uri uri) throws IOException {
        // Primer pase: solo las dimensiones
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, opts);
        }

        // Calcular inSampleSize para que ninguna dimensión supere TARGET_SIZE * 2
        opts.inSampleSize    = calculateInSampleSize(opts, TARGET_SIZE * 2, TARGET_SIZE * 2);
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig  = Bitmap.Config.ARGB_8888;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new IOException("No se pudo decodificar la imagen.");
            return bmp;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
        int h = opts.outHeight, w = opts.outWidth;
        int sample = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2, halfW = w / 2;
            while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) {
                sample *= 2;
            }
        }
        return sample;
    }

    /**
     * Corrige la orientación de la imagen leyendo el tag EXIF.
     * Las fotos tomadas con CameraX en modo MINIMIZE_LATENCY a veces
     * se guardan rotadas 90°.
     */
    private static Bitmap correctRotation(Context context, Uri uri, Bitmap src)
            throws IOException {
        int rotation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:  rotation = 90;  break;
                    case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                    default: break;
                }
            }
        }

        if (rotation == 0) return src;

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }
}
