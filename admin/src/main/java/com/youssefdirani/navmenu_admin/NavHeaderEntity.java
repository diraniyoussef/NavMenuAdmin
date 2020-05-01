package com.youssefdirani.navmenu_admin;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "navheader") //actually this table will contain only 1 entity (record)
public class NavHeaderEntity {
    @PrimaryKey  //(autoGenerate = true)// is same as autoincrement.
    public int uid1 = 0;

    @ColumnInfo(name = "imagepath")
    public String imagePath;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "subtitle")
    public String subtitle;

    @ColumnInfo(name = "background_color_tag")
    public String backgroundColor;
}
