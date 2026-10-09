package com.example.ecovisionsv.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecovisionsv.R;
import com.example.ecovisionsv.model.Deteccion;
import com.example.ecovisionsv.model.RespuestaPrediccion;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ResultadoActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.example.ecovisionsv.EXTRA_IMAGE_URI";
    public static final String EXTRA_RESPUESTA_JSON = "com.example.ecovisionsv.EXTRA_RESPUESTA_JSON";

    // Paleta de colores para bounding boxes (hasta 8 detecciones)
    private static final int[] BBOX_COLORS = {
            0xFF26A641, 0xFF0A84FF, 0xFFFF9F0A, 0xFF32ADE6,
            0xFFFF375F, 0xFFBF5AF2, 0xFFFF6961, 0xFF30D158
    };

    private ImageView imagenPrincipal;
    private TextView textoCantidad;
    private RecyclerView carrusel;
    private Bitmap bitmapOriginal;
    private Bitmap bitmapConBboxes;
    private RespuestaPrediccion respuesta;
    private List<Deteccion> detecciones;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_resultado);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_resultado), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        cargarDatos();
        configurarBotones();
    }

    private void bindViews() {
        imagenPrincipal = findViewById(R.id.imagen_resultado_principal);
        textoCantidad = findViewById(R.id.texto_cantidad_detecciones);
        carrusel = findViewById(R.id.carrusel_detecciones);
        // ProgressBar de carga de imagen
        ProgressBar progress = findViewById(R.id.progress_imagen);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    private void cargarDatos() {
        String uriString = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        String jsonString = getIntent().getStringExtra(EXTRA_RESPUESTA_JSON);

        if (uriString == null || jsonString == null) { finish(); return; }

        respuesta = new Gson().fromJson(jsonString, RespuestaPrediccion.class);
        detecciones = respuesta.deteccionesFiltradas(0.45f);

        Uri imageUri = Uri.parse(uriString);
        cargarYDibujarImagen(imageUri);
        configurarCarrusel();

        textoCantidad.setText(getResources().getQuantityString(
                R.plurals.detecciones_encontradas,
                detecciones.size(),
                detecciones.size()));

        // Toast de guardado (la BD ya se escribió en InferenciaRepository)
        Toast.makeText(this, R.string.inferencia_guardado, Toast.LENGTH_SHORT).show();
    }

    private void cargarYDibujarImagen(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmapOriginal = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return;
        }
        if (bitmapOriginal == null) return;

        bitmapOriginal = corregirOrientacionExif(uri, bitmapOriginal);
        bitmapConBboxes = dibujarBoundingBoxes(bitmapOriginal.copy(Bitmap.Config.ARGB_8888, true));
        imagenPrincipal.setImageBitmap(bitmapConBboxes);
    }

    private Bitmap corregirOrientacionExif(Uri uri, Bitmap src) {
        int rotation = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return src;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            return src; // sin corrección si falla
        }

        if (rotation == 0) return src;

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        src.recycle();
        return rotated;
    }

    /**
     * Dibuja los bounding boxes sobre el bitmap
     */
    private Bitmap dibujarBoundingBoxes(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

        Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);

        for (int i = 0; i < detecciones.size(); i++) {
            Deteccion d = detecciones.get(i);
            int colorInt = bboxColorPara(i);
            if (d.bboxNorm == null || d.bboxNorm.size() < 4) continue;

            float xmin = d.bboxNorm.get(0) * w;
            float ymin = d.bboxNorm.get(1) * h;
            float xmax = d.bboxNorm.get(2) * w;
            float ymax = d.bboxNorm.get(3) * h;
            RectF rect = new RectF(xmin, ymin, xmax, ymax);

            // Relleno semitransparente (alpha ~25%)
            fillPaint.setColor(colorInt & 0x00FFFFFF | 0x40000000);
            canvas.drawRoundRect(rect, 12f, 12f, fillPaint);
            // Borde
            borderPaint.setColor(colorInt);
            canvas.drawRoundRect(rect, 12f, 12f, borderPaint);
            // Etiqueta: "[N] Categoria ABC"
            String label = "[" + (i + 1) + "] " + d.categoria + " " + Math.round(d.confianza * 100) + "%";

            float textW = textPaint.measureText(label);
            float labelH = 36f;
            RectF labelBg = new RectF(xmin, ymin, xmin + textW + 16f, ymin + labelH);
            textBgPaint.setColor(colorInt);
            canvas.drawRoundRect(labelBg, 6f, 6f, textBgPaint);
            canvas.drawText(label, xmin + 8f, ymin + labelH - 8f, textPaint);
        }

        return bitmap;
    }

    // carrusel
    private void configurarCarrusel() {
        DeteccionAdapter adapter = new DeteccionAdapter(detecciones, bitmapOriginal);
        carrusel.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        carrusel.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(carrusel);

        // Dot pager indicator
        LinearLayout dotsContainer = findViewById(R.id.pager_dots);
        configurarDots(dotsContainer, detecciones.size(), 0);

        carrusel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm =
                            (LinearLayoutManager) rv.getLayoutManager();
                    if (lm == null) return;
                    int pos = lm.findFirstCompletelyVisibleItemPosition();
                    if (pos != RecyclerView.NO_ID) {
                        configurarDots(dotsContainer, detecciones.size(), pos);
                    }
                }
            }
        });
    }

    private void configurarDots(LinearLayout container, int total, int active) {
        container.removeAllViews();
        int dp6  = (int)(6  * getResources().getDisplayMetrics().density);
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        int dp4  = (int)(4  * getResources().getDisplayMetrics().density);

        for (int i = 0; i < total; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    i == active ? dp8 : dp6,
                    i == active ? dp8 : dp6);
            lp.setMargins(dp4, 0, dp4, 0);
            dot.setLayoutParams(lp);

            // Dot activo: color primario — inactivo: gris claro
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(i == active
                    ? getColor(R.color.primary)
                    : getColor(R.color.on_surface_variant));
            dot.setBackground(shape);
            container.addView(dot);
        }
    }

    // botones
    private void configurarBotones() {
        // Nueva detección → vuelve a ScannerActivity limpiando la pila
        findViewById(R.id.boton_nueva_deteccion).setOnClickListener(v -> {
            startActivity(new Intent(this, ScannerActivity.class));
            finish();
        });

        // Volver al inicio → vuelve a MainActivity limpiando la pila
        findViewById(R.id.boton_volver_inicio).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Botón atrás del header
        findViewById(R.id.boton_atras).setOnClickListener(v -> finish());
    }

    // helpers
    static int bboxColorPara(int index) {
        return BBOX_COLORS[index % BBOX_COLORS.length];
    }

    // Adapter del carrusel
    static class DeteccionAdapter
            extends RecyclerView.Adapter<DeteccionAdapter.VH> {

        private final List<Deteccion> items;
        private final Bitmap bitmapOriginal;

        DeteccionAdapter(List<Deteccion> items, Bitmap bitmapOriginal) {
            this.items = items;
            this.bitmapOriginal = bitmapOriginal;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_deteccion_carrusel, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Deteccion d  = items.get(pos);
            int colorInt = bboxColorPara(pos);

            // Franja lateral de color (6dp a la izquierda del card)
            h.franjaColor.setBackgroundColor(colorInt);

            // Chip de categoría: fondo coloreado, texto blanco
            h.chipCategoria.setText(d.categoria);
            h.chipCategoria.setBackgroundColor(colorInt);

            // Porcentaje de confianza — texto oscuro sobre fondo blanco
            int pct = Math.round(d.confianza * 100);
            h.textoConfianza.setText(pct + "%");

            // Barra coloreada
            h.barraConfianza.setProgress(pct);
            h.barraConfianza.getProgressDrawable()
                    .setColorFilter(colorInt, PorterDuff.Mode.SRC_IN);

            // Badge numérico
            h.numeroBadge.setText(String.valueOf(pos + 1));
            h.numeroBadge.setBackgroundColor(colorInt);

            // Consejo genérico
            h.textoConsejo.setText(
                    consejoParaCategoria(d.categoria, h.textoConsejo.getContext()));

            // Recorte de imagen con padding de contexto
            h.imagenRecorte.setClipToOutline(true);
            if (bitmapOriginal != null && d.bboxNorm != null && d.bboxNorm.size() == 4) {
                int w = bitmapOriginal.getWidth(), ih = bitmapOriginal.getHeight();
                float px = (d.bboxNorm.get(2) - d.bboxNorm.get(0)) * 0.08f;
                float py = (d.bboxNorm.get(3) - d.bboxNorm.get(1)) * 0.08f;
                int x  = Math.max(0, (int)((d.bboxNorm.get(0) - px) * w));
                int y  = Math.max(0, (int)((d.bboxNorm.get(1) - py) * ih));
                int x2 = Math.min(w,  (int)((d.bboxNorm.get(2) + px) * w));
                int y2 = Math.min(ih, (int)((d.bboxNorm.get(3) + py) * ih));
                int bw = x2 - x, bh = y2 - y;
                if (bw > 0 && bh > 0) {
                    h.imagenRecorte.setImageBitmap(
                            Bitmap.createBitmap(bitmapOriginal, x, y, bw, bh));
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // Texto genérico para cada clase del modelo (6 clases TACO)
        private static String consejoParaCategoria(String categoria, android.content.Context ctx) {
            if (categoria == null) return ctx.getString(R.string.consejo_default);
            switch (categoria.toLowerCase().trim()) {
                case "plastico":
                    return ctx.getString(R.string.consejo_plastico);
                case "metal":
                    return ctx.getString(R.string.consejo_metal);
                case "papel":
                    return ctx.getString(R.string.consejo_papel);
                case "carton":
                    return ctx.getString(R.string.consejo_carton);
                case "vidrio":
                    return ctx.getString(R.string.consejo_vidrio);
                case "organico":
                    return ctx.getString(R.string.consejo_organico);
                default:
                    return ctx.getString(R.string.consejo_default);
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            View         franjaColor;
            TextView     chipCategoria;
            TextView     numeroBadge;
            ImageView    imagenRecorte;
            TextView     textoConfianza;
            ProgressBar  barraConfianza;
            TextView     textoConsejo;

            VH(@NonNull View v) {
                super(v);
                franjaColor    = v.findViewById(R.id.franja_color);
                chipCategoria  = v.findViewById(R.id.chip_categoria);
                numeroBadge    = v.findViewById(R.id.numero_deteccion);
                imagenRecorte  = v.findViewById(R.id.imagen_recorte);
                textoConfianza = v.findViewById(R.id.texto_confianza_deteccion);
                barraConfianza = v.findViewById(R.id.barra_confianza);
                textoConsejo   = v.findViewById(R.id.texto_consejo_categoria);
            }
        }
    }
}