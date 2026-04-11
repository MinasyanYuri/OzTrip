//package com.example.oztrip;
//
//import androidx.room.Dao;
//import androidx.room.Delete;
//import androidx.room.Insert;
//import androidx.room.Query;
//import androidx.room.Update;
//
//import java.util.List;
//
//@Dao
//public interface TravelDao {
//    // Получить все поездки для верхнего ползунка
//    @Query("SELECT * FROM travel_lists")
//    List<TravelList> getAllLists();
//
//    @Query("SELECT * FROM travel_points WHERE listId = :listId")
//    List<TravelPoint> getPointsForList(int listId);
//    @Insert
//    void insertList(TravelList list);
//
//    @Update
//    void updateListName(TravelList list);
//
//    // Получить все ветки конкретной поездки
//    @Update
//    int updateList(TravelList list);
//    @Update
//    void updatePoint(TravelPoint point);
//    @Insert
//    void insertPoint(TravelPoint point);
//
//    @Delete
//    void deleteList(TravelList list);
//}