package com.irkana.ecovisionsv.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.config.ConfigRepository;
import com.irkana.ecovisionsv.model.Deteccion;
import com.irkana.ecovisionsv.model.RespuestaPrediccion;
import com.irkana.ecovisionsv.network.InferenciaRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ResultadoActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.irkana.ecovisionsv.EXTRA_IMAGE_URI";
    /** Si falta, la Activity llama a la API con EXTRA_IMAGE_URI mostrando un skeleton. */
    public static final String EXTRA_RESPUESTA_JSON = "com.irkana.ecovisionsv.EXTRA_RESPUESTA_JSON";
    /** true cuando se abre desde el historial: sin toast de guardado ni botones de acción. */
    public static final String EXTRA_DESDE_HISTORIAL = "com.irkana.ecovisionsv.EXTRA_DESDE_HISTORIAL";
    /** Se devuelve en el resultado cuando el usuario pide una nueva detección. */
    public static final String EXTRA_NUEVA_DETECCION = "com.irkana.ecovisionsv.EXTRA_NUEVA_DETECCION";

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
    private boolean desdeHistorial;
    private View skeleton;
    private ObjectAnimator pulsoSkeleton;
    // Se guardan al terminar la inferencia para no repetirla si la Activity se recrea
    private String uriActual;
    private String jsonActual;

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

        desdeHistorial = getIntent().getBooleanExtra(EXTRA_DESDE_HISTORIAL, false);

        bindViews();
        configurarBotones();

        String uriString = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        String jsonString = getIntent().getStringExtra(EXTRA_RESPUESTA_JSON);
        if (savedInstanceState != null && savedInstanceState.getString(EXTRA_RESPUESTA_JSON) != null) {
            uriString = savedInstanceState.getString(EXTRA_IMAGE_URI);
            jsonString = savedInstanceState.getString(EXTRA_RESPUESTA_JSON);
        }

        if (uriString == null) { finish(); return; }

        if (jsonString != null) {
            mostrarResultado(uriString, jsonString);
        } else {
            analizar(Uri.parse(uriString));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_IMAGE_URI, uriActual);
        outState.putString(EXTRA_RESPUESTA_JSON, jsonActual);
    }

    @Override
    protected void onDestroy() {
        if (pulsoSkeleton != null) pulsoSkeleton.cancel();
        super.onDestroy();
    }

    private void bindViews() {
        imagenPrincipal = findViewById(R.id.imagen_resultado_principal);
        textoCantidad = findViewById(R.id.texto_cantidad_detecciones);
        carrusel = findViewById(R.id.carrusel_detecciones);
        skeleton = findViewById(R.id.skeleton_resultado);
        // ProgressBar de carga de imagen
        ProgressBar progress = findViewById(R.id.progress_imagen);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    /** Llama a la API mostrando el skeleton; al responder pinta el resultado. */
    private void analizar(Uri uri) {
        mostrarSkeleton(true);
        new InferenciaRepository(this).inferir(uri, new InferenciaRepository.Callback() {
            @Override
            public void onExito(Uri imagenUri, RespuestaPrediccion resp, List<Deteccion> filtradas) {
                // Si el usuario salió, el resultado ya quedó guardado en el historial
                if (isFinishing() || isDestroyed()) return;
                mostrarSkeleton(false);
                mostrarResultado(imagenUri.toString(), new Gson().toJson(resp));
            }

            @Override
            public void onError(String mensaje) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ResultadoActivity.this, mensaje, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void mostrarSkeleton(boolean visible) {
        skeleton.setVisibility(visible ? View.VISIBLE : View.GONE);
        findViewById(R.id.scroll_resultado).setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        if (!desdeHistorial) {
            findViewById(R.id.footer_resultado).setVisibility(visible ? View.GONE : View.VISIBLE);
        }

        if (visible) {
            pulsoSkeleton = ObjectAnimator.ofFloat(
                    skeleton.findViewById(R.id.skeleton_bloques), View.ALPHA, 1f, 0.45f);
            pulsoSkeleton.setDuration(800);
            pulsoSkeleton.setRepeatMode(ValueAnimator.REVERSE);
            pulsoSkeleton.setRepeatCount(ValueAnimator.INFINITE);
            pulsoSkeleton.start();
        } else if (pulsoSkeleton != null) {
            pulsoSkeleton.cancel();
            pulsoSkeleton = null;
        }
    }

    private void mostrarResultado(String uriString, String jsonString) {
        uriActual = uriString;
        jsonActual = jsonString;

        respuesta = new Gson().fromJson(jsonString, RespuestaPrediccion.class);
        detecciones = respuesta.deteccionesFiltradas();

        Uri imageUri = Uri.parse(uriString);
        cargarYDibujarImagen(imageUri);
        configurarCarrusel();

        textoCantidad.setText(getResources().getQuantityString(
                R.plurals.detecciones_encontradas,
                detecciones.size(),
                detecciones.size()));

        // Toast de guardado (la BD ya se escribió en InferenciaRepository)
        if (!desdeHistorial) {
            Toast.makeText(this, R.string.inferencia_guardado, Toast.LENGTH_SHORT).show();
        }
    }

    private void cargarYDibujarImagen(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmapOriginal = BitmapFactory.decodeStream(in);
        } catch (IOException | SecurityException e) {
            // Imagen borrada o sin permiso de lectura: se muestra solo el resumen
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
        } catch (IOException | SecurityException e) {
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
        int asomo = detecciones.size() > 1
                ? getResources().getDimensionPixelSize(R.dimen.carrusel_asomo) : 0;
        carrusel.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false) {
            /*
             * El ancho de cada tarjeta se fija al medirla, cuando el carrusel ya
             * tiene ancho. Fijarlo al crear el ViewHolder fallaba: los primeros se
             * crean con el carrusel aún sin medir (ancho 0), se quedaban a ancho
             * completo y, al reciclarse, otras tarjetas tampoco dejaban asomar a
             * sus vecinas.
             * Con varias detecciones todas miden el ancho menos dos asomos, para
             * que una intermedia centrada deje ver carrusel_asomo de cada vecina.
             * Con una sola (asomo = 0) ocupa todo el ancho.
             */
            @Override
            public void measureChildWithMargins(@NonNull View hijo, int usadoAncho,
                                                int usadoAlto) {
                int ancho = getWidth() - getPaddingStart() - getPaddingEnd() - 2 * asomo;
                if (ancho > 0) hijo.getLayoutParams().width = ancho;
                super.measureChildWithMargins(hijo, usadoAncho, usadoAlto);
            }
        });
        carrusel.setAdapter(adapter);
        new SnapCarrusel().attachToRecyclerView(carrusel);

        int total = detecciones.size();
        LinearLayout dotsContainer = findViewById(R.id.pager_dots);
        TextView contador = findViewById(R.id.texto_contador_carrusel);

        // Con una sola detección no hay nada que deslizar: sin puntos ni contador
        if (total <= 1) {
            dotsContainer.setVisibility(View.GONE);
            contador.setVisibility(View.GONE);
            return;
        }

        contador.setVisibility(View.VISIBLE);
        actualizarIndicadores(dotsContainer, contador, total, 0);

        carrusel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm =
                            (LinearLayoutManager) rv.getLayoutManager();
                    if (lm == null) return;
                    // La tarjeta siguiente asoma, así que se usa la primera visible
                    // por completo y, si no hay, la primera visible
                    int pos = lm.findFirstCompletelyVisibleItemPosition();
                    if (pos == RecyclerView.NO_POSITION) {
                        pos = lm.findFirstVisibleItemPosition();
                    }
                    if (pos != RecyclerView.NO_POSITION) {
                        actualizarIndicadores(dotsContainer, contador, total, pos);
                    }
                }
            }
        });
    }

    /**
     * Puntos de página: el activo es una píldora morada más ancha y los demás
     * puntos claros. El contador muestra "N de total".
     */
    private void actualizarIndicadores(LinearLayout container, TextView contador,
                                       int total, int activo) {
        contador.setText(getString(R.string.carrusel_contador, activo + 1, total));

        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int alto    = Math.round(6  * density);
        int anchoOn = Math.round(18 * density);
        int margen  = Math.round(3  * density);

        for (int i = 0; i < total; i++) {
            boolean esActivo = i == activo;
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    esActivo ? anchoOn : alto, alto);
            lp.setMargins(margen, 0, margen, 0);
            dot.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(alto / 2f);
            shape.setColor(getColor(esActivo ? R.color.secondary : R.color.dot_inactivo));
            dot.setBackground(shape);
            container.addView(dot);
        }
    }

    // botones
    private void configurarBotones() {
        // Botón atrás del header
        findViewById(R.id.boton_atras).setOnClickListener(v -> finish());

        // Desde el historial solo se muestra el botón atrás
        if (desdeHistorial) {
            findViewById(R.id.footer_resultado).setVisibility(View.GONE);
            return;
        }

        // Nueva detección → HomeFragment recibe el resultado y abre el escáner,
        // así la nueva captura pasa por el mismo flujo de inferencia y guardado.
        // Con camera_enabled=false el botón se oculta.
        View botonNueva = findViewById(R.id.boton_nueva_deteccion);
        if (!ConfigRepository.getInstance(this).get().isCameraEnabled()) {
            botonNueva.setVisibility(View.GONE);
        }
        botonNueva.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_NUEVA_DETECCION, true);
            setResult(RESULT_OK, data);
            finish();
        });

        // Volver al inicio → vuelve a MainActivity limpiando la pila
        findViewById(R.id.boton_volver_inicio).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    // helpers
    static int bboxColorPara(int index) {
        return BBOX_COLORS[index % BBOX_COLORS.length];
    }

    /**
     * Paso de una tarjeta por gesto (como PagerSnapHelper), con alineación según
     * la posición: la primera se alinea al borde inicial (asoma la siguiente a la
     * derecha), la última al borde final (asoma la anterior a la izquierda) y las
     * intermedias se centran (asoman la anterior y la siguiente).
     */
    static class SnapCarrusel extends PagerSnapHelper {
        @Override
        public int[] calculateDistanceToFinalSnap(
                @NonNull RecyclerView.LayoutManager lm, @NonNull View destino) {
            int[] distancia = new int[2];
            if (!lm.canScrollHorizontally()) return distancia;
            OrientationHelper h = OrientationHelper.createHorizontalHelper(lm);
            int pos = lm.getPosition(destino);
            if (pos == 0) {
                distancia[0] = h.getDecoratedStart(destino) - h.getStartAfterPadding();
            } else if (pos == lm.getItemCount() - 1) {
                distancia[0] = h.getDecoratedEnd(destino) - h.getEndAfterPadding();
            } else {
                int centroTarjeta = h.getDecoratedStart(destino)
                        + h.getDecoratedMeasurement(destino) / 2;
                int centroCarrusel = h.getStartAfterPadding() + h.getTotalSpace() / 2;
                distancia[0] = centroTarjeta - centroCarrusel;
            }
            return distancia;
        }
    }

    // Adapter del carrusel
    static class DeteccionAdapter
            extends RecyclerView.Adapter<DeteccionAdapter.VH> {

        /** Margen de contexto alrededor del bbox, relativo a su tamaño. */
        private static final float MARGEN_RECORTE = 0.12f;
        /** Alto del área del recorte en item_deteccion_carrusel.xml. */
        private static final int ALTO_RECORTE_DP = 220;

        private final List<Deteccion> items;
        private final Bitmap bitmapOriginal;
        // Los recortes se generan una vez por posición al hacer scroll
        private final Bitmap[] recortes;

        DeteccionAdapter(List<Deteccion> items, Bitmap bitmapOriginal) {
            this.items = items;
            this.bitmapOriginal = bitmapOriginal;
            this.recortes = new Bitmap[items.size()];
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

            ColorStateList colorTint = ColorStateList.valueOf(colorInt);

            // Franja de color bajo el recorte
            h.franjaColor.setBackgroundColor(colorInt);

            // Chip de categoría: color fijo de la clase (igual que en el historial)
            CategoriaEstilo.aplicarChip(h.chipCategoria, d.categoria);

            // Porcentaje de confianza — texto oscuro sobre fondo blanco
            int pct = Math.round(d.confianza * 100);
            h.textoConfianza.setText(pct + "%");

            // Barra coloreada
            h.barraConfianza.setProgress(pct);
            h.barraConfianza.getProgressDrawable()
                    .setColorFilter(colorInt, PorterDuff.Mode.SRC_IN);

            // Badge numérico
            h.numeroBadge.setText(String.valueOf(pos + 1));
            h.numeroBadge.setBackgroundTintList(colorTint);

            // Consejo genérico
            h.textoConsejo.setText(
                    consejoParaCategoria(d.categoria, h.textoConsejo.getContext()));

            // Recorte del objeto con su bbox
            if (recortes[pos] == null) {
                recortes[pos] = crearRecorte(d, colorInt, h.itemView);
            }
            h.imagenRecorte.setImageBitmap(recortes[pos]);
        }

        /**
         * Recorta la detección de la imagen original (resolución completa) con un margen
         * de contexto, oscurece ese margen y dibuja el bbox exacto con el color de la detección.
         * Las coordenadas del bbox se trasladan al sistema del recorte, así que coinciden
         * con las de la imagen principal.
         */
        private Bitmap crearRecorte(Deteccion d, int colorInt, View itemView) {
            if (bitmapOriginal == null || d.bboxNorm == null || d.bboxNorm.size() < 4) return null;

            int w = bitmapOriginal.getWidth(), ih = bitmapOriginal.getHeight();
            float bx1 = d.bboxNorm.get(0) * w, by1 = d.bboxNorm.get(1) * ih;
            float bx2 = d.bboxNorm.get(2) * w, by2 = d.bboxNorm.get(3) * ih;
            float mx = (bx2 - bx1) * MARGEN_RECORTE, my = (by2 - by1) * MARGEN_RECORTE;

            int x  = Math.max(0, (int) (bx1 - mx));
            int y  = Math.max(0, (int) (by1 - my));
            int x2 = Math.min(w,  (int) Math.ceil(bx2 + mx));
            int y2 = Math.min(ih, (int) Math.ceil(by2 + my));
            int cw = x2 - x, ch = y2 - y;
            if (cw <= 0 || ch <= 0) return null;

            Bitmap recorte = Bitmap.createBitmap(bitmapOriginal, x, y, cw, ch)
                    .copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(recorte);
            RectF bbox = new RectF(bx1 - x, by1 - y, bx2 - x, by2 - y);

            // Escala con la que fitCenter mostrará el recorte: sirve para que el
            // trazo mida ~2.5dp en pantalla sin importar el tamaño del objeto
            float density = itemView.getResources().getDisplayMetrics().density;
            float anchoVista = itemView.getResources().getDisplayMetrics().widthPixels - 36 * density;
            float escala = Math.min(anchoVista / cw, ALTO_RECORTE_DP * density / ch);
            float trazo = 2.5f * density / escala;

            // Oscurecer solo el margen de contexto (todo menos el bbox)
            Path fuera = new Path();
            fuera.setFillType(Path.FillType.EVEN_ODD);
            fuera.addRect(0, 0, cw, ch, Path.Direction.CW);
            fuera.addRect(bbox, Path.Direction.CW);
            Paint velo = new Paint(Paint.ANTI_ALIAS_FLAG);
            velo.setColor(0x66000000);
            canvas.drawPath(fuera, velo);

            Paint borde = new Paint(Paint.ANTI_ALIAS_FLAG);
            borde.setStyle(Paint.Style.STROKE);
            borde.setStrokeWidth(trazo);
            borde.setColor(colorInt);
            canvas.drawRect(bbox, borde);

            return recorte;
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