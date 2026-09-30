package com.example.ecovisionsv.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base de datos Room de EcoVision.
 */
@Database(entities = {DeteccionEntity.class}, version = 1, exportSchema = false)
public abstract class EcoVisionDatabase extends RoomDatabase {

    public abstract DeteccionDao deteccionDao();

    // Pool de hilos dedicado a operaciones de BD
    public static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static volatile EcoVisionDatabase INSTANCE;

    public static EcoVisionDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (EcoVisionDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    EcoVisionDatabase.class,
                                    "ecovision.db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
