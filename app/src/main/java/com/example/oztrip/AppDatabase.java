//package com.example.oztrip;
//
//import android.content.Context;
//
//import androidx.room.Database;
//import androidx.room.Room;
//import androidx.room.RoomDatabase;
//import androidx.room.TypeConverters;
//
//@Database(entities = {TravelList.class, TravelPoint.class}, version = 1)
//@TypeConverters({Converters.class}) // ОБЯЗАТЕЛЬНО ДОБАВЬ ЭТУ СТРОКУ
//public abstract class AppDatabase extends RoomDatabase {
//    public abstract TravelDao travelDao();
//
//    private static AppDatabase instance;
//
//    public static synchronized AppDatabase getInstance(Context context) {
//        if (instance == null) {
//            instance = Room.databaseBuilder(context.getApplicationContext(),
//                            AppDatabase.class, "oztrip_database")
//                    .allowMainThreadQueries() // Для тестов пойдет, но лучше в потоке
//                    .build();
//        }
//        return instance;
//    }
//}