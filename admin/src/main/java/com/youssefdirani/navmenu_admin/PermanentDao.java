package com.youssefdirani.navmenu_admin;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface PermanentDao { //Dao is Data Access Object

    /*
    String column1 = "a_certain_column_name";
    String column2 = "another_column_name";

    @Query("SELECT * FROM " + tableName)
    List<AnnouncementEntity> getAll();

    @Query("SELECT * FROM " + tableName + " WHERE " + uid + " IN (:userIds)")
    List<AnnouncementEntity> loadAllByIds( int[] userIds );

    @Query("SELECT * FROM " + tableName + " WHERE " + column1 + " LIKE :first AND " +
            column2 + " LIKE :last LIMIT 1")
    AnnouncementEntity findByName(double first, double last);

    @Insert
    void insertAll(AnnouncementEntity... users);

    @Update
     public int updateSongs(List<Song> songs);

     @Delete
    void delete(AnnouncementEntity announcementEntity);
    */
//for the navheader table
    @Insert
    void insertNavHeader(NavHeaderEntity navHeaderEntity); //only going to be used once

    @Query( "SELECT * FROM navheader LIMIT 1" )
    NavHeaderEntity getNavHeader();

    @Update
    public void updateNavHeader(NavHeaderEntity navHeaderEntity);

//Now for the nav table
    @Insert
    void insertNav(NavEntity navEntity);

    @Query( "SELECT * FROM nav WHERE 'order' LIKE :orderValue LIMIT 1" )
    NavEntity loadById( int orderValue );

    @Query( "SELECT * FROM nav" )
    List<NavEntity> getNav();
}
