package com.example.ecovisionsv.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entidad Room que persiste el resultado de una sesión de inferencia.
 * Tabla: detecciones
 *  - ruta de la imagen guardada en el dispositivo (Pictures/EcoVisionSV)
 *  - resultado JSON crudo de la API (para reconstruir la vista de detalle en Fase 3)
 *  - resumen de la detección de mayor confianza (categoría + confianza)
 *  - dimensiones de la imagen enviada al modelo
 *  - latencia de la inferencia
 *  - timestamp de cuándo se realizó
 */
@Entity(tableName = "detecciones")
public class DeteccionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Ruta URI de la imagen en la galería: content://media/... */
    @ColumnInfo(name = "imagen_uri")
    public String imagenUri;

    /** JSON completo devuelto por /predict (para reconstruir el resultado completo). */
    @ColumnInfo(name = "resultado_json")
    public String resultadoJson;

    /** Categoría de la detección con mayor confianza (post-NMS). */
    @ColumnInfo(name = "categoria_principal")
    public String categoriaPrincipal;

    /** Confianza de la detección principal, 0.0–1.0. */
    @ColumnInfo(name = "confianza_principal")
    public float confianzaPrincipal;

    /** Número de detecciones únicas tras aplicar NMS. */
    @ColumnInfo(name = "num_detecciones")
    public int numDetecciones;

    /** Ancho de la imagen tal como fue recibida por el servidor (640 px). */
    @ColumnInfo(name = "ancho_imagen")
    public int anchoImagen;

    /** Alto de la imagen tal como fue recibida por el servidor (640 px). */
    @ColumnInfo(name = "alto_imagen")
    public int altoImagen;

    /** Latencia de la inferencia en el servidor (ms). */
    @ColumnInfo(name = "latencia_ms")
    public float latenciaMs;

    /** Timestamp Unix (ms) de cuando se realizó la detección. */
    @ColumnInfo(name = "timestamp")
    public long timestamp;
}
