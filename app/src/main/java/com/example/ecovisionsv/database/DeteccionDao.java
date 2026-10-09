package com.example.ecovisionsv.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object para la tabla {@code detecciones}.
 * Las operaciones de escritura se ejecutan en un hilo de fondo
 * (ver {@link EcoVisionDatabase}).
 */
@Dao
public interface DeteccionDao {
    /** Inserta un nuevo resultado de inferencia y devuelve el rowId generado. */
    @Insert
    long insertar(DeteccionEntity entidad);

    /** Devuelve todos los registros ordenados del más reciente al más antiguo. */
    @Query("SELECT * FROM detecciones ORDER BY timestamp DESC")
    List<DeteccionEntity> obtenerTodos();

    /** Obtiene un registro específico por su id (para la pantalla de detalle – Fase 3). */
    @Query("SELECT * FROM detecciones WHERE id = :id LIMIT 1")
    DeteccionEntity obtenerPorId(long id);

    @Query("DELETE FROM detecciones WHERE id = :id")
    void eliminarPorId(long id);

    /** Elimina todos los registros (útil para pruebas / opción "limpiar historial"). */
    @Query("DELETE FROM detecciones")
    void eliminarTodos();
}
