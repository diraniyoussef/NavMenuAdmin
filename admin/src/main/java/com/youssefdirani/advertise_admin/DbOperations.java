package com.youssefdirani.advertise_admin;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

class DbOperations {
    private MainActivity activity;

    DbOperations(MainActivity activity ) {
        this.activity = activity;
        db_room = Room.databaseBuilder( activity, AppDatabase.class, "my_db" ).build();
        permanentDao = db_room.permanentDao();
    }

    void addNavRecord( final String title ) {
        new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                //Log.i("Youssef", "inside DbOperations : adding a nav entity.");
                NavEntity navEntity = new NavEntity();
                navEntity.title = title;
                navEntity.bottombar_backgroundColorTag = "colorWhite"; //background color to white. This is my default
                navEntity.iconTag = "ic_no_icon";
                List<NavEntity> navEntityList = permanentDao.getAllNav();
                navEntity.index = navEntityList.size(); //it's my convention to preserve throughout the app the order of indexes
                permanentDao.insertNav( navEntity );
                Log.i("addNavRecord", "about to create tables for index " + navEntity.index);
                setBottomBarTableAndFirstBottomNavContentTable( navEntity.index ); //getCheckedItemOrder still returns the old value

            }
        }.start();
    }
/*
    private void findNavEntityByIndex( NavEntity navEntity, final int index ) { //usually when we call it we know it'll return an actual
        List<NavEntity> navEntityList = permanentDao.getAllNav();
        for( NavEntity navEntity1 : navEntityList ) {
            if( navEntity1.index == index ) {
                navEntity = navEntity1;
                return;
            }
        }
        navEntity = null;
    }
 */

    void removeNavRecord( final int index ) {
        new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                //Log.i("Youssef", "inside DbOperations : adding a nav entity.");
                List<NavEntity> navEntityList = permanentDao.getAllNav();
                NavEntity navEntity = navEntityList.get( index );
                final int originalNavSize = navEntityList.size();
                concatenateNavTableIndices( index, originalNavSize ); //fixing the index of the records in nav table
                Log.i("Youssef", "before deleting nav entity from database");
                permanentDao.deleteNavEntity( navEntity );
                Log.i("Youssef", "after deleting nav entity from database");
                deleteBbTable();
                deleteBottomNavContentTablesButKeepUpTo(-1);
                renameTables( index, originalNavSize );
            }

            private void renameTables( final int startingIndex, final int originalNavSize ) {
                if( startingIndex < originalNavSize - 1 ) { //startingIndex is passed such that it corresponds for the just deleted element
                    db.beginTransaction();
                    try {
                        for( int i = startingIndex ; i < originalNavSize - 1 ; i++ ) {
                            String oldBottomBarTableName = generateBbTableName( i + 1 );
                            Cursor cursor_bb = db.query("SELECT * FROM '" + oldBottomBarTableName + "'");
                            for( int j = 0 ; j < cursor_bb.getCount() ; j++ ) {
                                String oldBottomNavContentTableName = generateBottomNavContentTableName( i + 1, j );
                                String newBottomNavContentTableName = generateBottomNavContentTableName( i , j );
                                db.execSQL("ALTER TABLE " + oldBottomNavContentTableName + " RENAME TO " + newBottomNavContentTableName + ";");
                            }
                            String newBottomBarTableName = generateBbTableName( i );
                            db.execSQL("ALTER TABLE " + oldBottomBarTableName + " RENAME TO " + newBottomBarTableName + ";");
                        }
                        db.setTransactionSuccessful(); //to commit
                    } catch(Exception e) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("error", "position 0.");
                                activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                            }
                        });
                    } finally {
                        db.endTransaction();
                    }
                }
            }
/*
            private boolean isTableExists( String tableName ) {
                String query = "select DISTINCT tbl_name from sqlite_master where tbl_name = '"+tableName+"'";
                try {
                    Cursor cursor = db.query(query, null);
                    if(cursor!=null) {
                        return cursor.getCount() > 0;
                    }
                    return false;
                } catch(Exception e) {
                    Log.i("isTableExists", "Table " + tableName + " does not exist.");
                    return false;
                }
            }
 */

            private void concatenateNavTableIndices( final int startingIndex, final int originalNavSize ) {//should be called whenever an entity is deleted (except for the last entity).
                //finally it's ok to have e.g. 4 elements like the following indices 0 3 1 2
                if( startingIndex < originalNavSize - 1 ) { //startingIndex is passed such that it corresponds for the just deleted element
                    Log.i("Youssef", "concatenateNavTableIndices. size of navEntityList = " + originalNavSize);
                    for( int i = startingIndex ; i < originalNavSize - 1 ; i++ ) {
                        Log.i("Youssef", "concatenateNavTableIndices. i = " + i);
                        NavEntity navEntity = permanentDao.getNav(i + 1 );
                        navEntity.index = i;
                        permanentDao.updateNav( navEntity );
                    }
                }
            }
        }.start();
    }

    void switchNavItems_Upwards( final int lowerItemOrder ) { //lowerItemOrder is the old lower item index. By lower I mean lower in position (thus higher in index)
        ( new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                final List<NavEntity> navEntityList = permanentDao.getAllNav();
                NavEntity navEntity2 = navEntityList.get( lowerItemOrder );
                NavEntity navEntity1 = navEntityList.get( lowerItemOrder - 1 );

                db.beginTransaction();
                try {
                    String oldBottomBarTableName = generateBbTableName( lowerItemOrder );
                    int intermediateItemOrder = 1000;
                    String intermediateBottomBarTableName = generateBbTableName( intermediateItemOrder );
                    String newBottomBarTableName = generateBbTableName( lowerItemOrder - 1 );
                    boolean oldBbExists, newBbExists;

                    int oldBottomNavContentTableCount;
                    Cursor cursor_bb;
                    if( navEntity2.bottombar_backgroundColorTag.equals("none") ) {
                        oldBottomNavContentTableCount = 1;
                        oldBbExists = false;
                    } else {
                        cursor_bb = db.query("SELECT * FROM '" + oldBottomBarTableName + "'");
                        oldBottomNavContentTableCount = cursor_bb.getCount();
                        oldBbExists = true;
                    }
                    int newBottomNavContentTableCount;
                    if( navEntity1.bottombar_backgroundColorTag.equals("none") ) {
                        newBottomNavContentTableCount = 1;
                        newBbExists = false;
                    } else {
                        cursor_bb = db.query("SELECT * FROM '" + newBottomBarTableName + "'");
                        newBottomNavContentTableCount = cursor_bb.getCount();
                        newBbExists = true;
                    }

                    Log.i("switchNavItems_Upwards", "First try");
                    for( int j = 0 ; j < oldBottomNavContentTableCount ; j++ ) {
                        String oldBottomNavContentTableName = generateBottomNavContentTableName( lowerItemOrder, j );
                        String intermediateBottomNavContentTableName = generateBottomNavContentTableName( intermediateItemOrder , j );
                        Log.i("switchNavItems_Upwards", oldBottomNavContentTableName + " to " + intermediateBottomNavContentTableName);
                        db.execSQL("ALTER TABLE " + oldBottomNavContentTableName + " RENAME TO " + intermediateBottomNavContentTableName + ";");
                    }
                    if( oldBbExists ) {
                        Log.i("switchNavItems_Upwards", oldBottomBarTableName + " to " + intermediateBottomBarTableName);
                        db.execSQL("ALTER TABLE " + oldBottomBarTableName + " RENAME TO " + intermediateBottomBarTableName + ";");
                    }

                    Log.i("switchNavItems_Upwards", "Second try");
                    for( int j = 0 ; j < newBottomNavContentTableCount ; j++ ) {
                        String oldBottomNavContentTableName = generateBottomNavContentTableName( lowerItemOrder, j );
                        String newBottomNavContentTableName = generateBottomNavContentTableName( lowerItemOrder - 1, j );
                        Log.i("switchNavItems_Upwards", newBottomNavContentTableName + " to " + oldBottomNavContentTableName);
                        db.execSQL("ALTER TABLE " + newBottomNavContentTableName + " RENAME TO " + oldBottomNavContentTableName + ";");
                    }
                    if( newBbExists ) {
                        Log.i("switchNavItems_Upwards", newBottomBarTableName + " to " + oldBottomBarTableName);
                        db.execSQL("ALTER TABLE " + newBottomBarTableName + " RENAME TO " + oldBottomBarTableName + ";");
                    }

                    Log.i("switchNavItems_Upwards", "Third try");
                    for( int j = 0 ; j < oldBottomNavContentTableCount ; j++ ) {
                        String intermediateBottomNavContentTableName = generateBottomNavContentTableName( intermediateItemOrder, j );
                        String newBottomNavContentTableName = generateBottomNavContentTableName( lowerItemOrder - 1, j );
                        Log.i("switchNavItems_Upwards", intermediateBottomNavContentTableName + " to " + newBottomNavContentTableName);
                        db.execSQL("ALTER TABLE " + intermediateBottomNavContentTableName + " RENAME TO " + newBottomNavContentTableName + ";");
                    }
                    if( oldBbExists ) {
                        Log.i("switchNavItems_Upwards", intermediateBottomBarTableName + " to " + newBottomBarTableName);
                        db.execSQL("ALTER TABLE " + intermediateBottomBarTableName + " RENAME TO " + newBottomBarTableName + ";");
                    }
                    Log.i("switchNavItems_Upwards", "just before committing");
                    db.setTransactionSuccessful();
                } catch(Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                            Log.i("error", "position 1.");
                        }
                    });
                } finally {
                    db.endTransaction();
                }

                //Now switching all columns but the index
                int intermediateIndex = navEntity1.index;
                navEntity1.index = navEntity2.index;
                navEntity2.index = intermediateIndex;
                int intermediateUId = navEntity1.uid;
                navEntity1.uid = navEntity2.uid;
                navEntity2.uid = intermediateUId;
                //Log.i("switchNavItems","position 1");
                permanentDao.updateNav( navEntity1 ); //the update is actually based on uid (consider it as a hidden argument)
                //Log.i("switchNavItems","position 2");
                permanentDao.updateNav( navEntity2 );
                //Log.i("switchNavItems","position 3");
            }
        } ).start();
    }

    void onDestroy() {
        db_room.close(); //should not be called onStop, otherwise when we go the activity of choosing an image from gallery, this will be called and this hurts the consistency of the app.
    }

    private AppDatabase db_room;
    private PermanentDao permanentDao;
    private SupportSQLiteDatabase db; //https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase

    private String generateBbTableName( int navIndex ) {
        return "bb_" + navIndex;
    }

    void setBottomBarTable() {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final String bottomBarTableName = generateBbTableName( activity.navOperations.getCheckedItemOrder() );
        try {
            createBbTable( bottomBarTableName );
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 2.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    private String generateBottomNavContentTableName( int navIndex, int bottomNavIndex ) {
        return "table" + navIndex + "_" + bottomNavIndex;
    }

    private void setBottomBarTableAndFirstBottomNavContentTable( final int navIndex ) {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final String bottomBarTableName = generateBbTableName( navIndex );
        final String firstBottomNavContentTableName = generateBottomNavContentTableName( navIndex, 0 );
        try {
            createBbTable( bottomBarTableName );
            Log.i("fatal","after createBbTable of " + bottomBarTableName );
            //normally we have to add 4 rows (if bb_0 table is made for the first time.) But I won't. I will keep everything to default as the first time the user (not the admin) opens the app, it will be similar to the admin's app
            db.execSQL("CREATE TABLE IF NOT EXISTS " + firstBottomNavContentTableName + " ( " +
                    "uid INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "type TEXT, " +
                    "content TEXT);");
            Log.i("fatal","after firstBottomNavContentTableName which is " + firstBottomNavContentTableName );
            //I can't insert anything here because it's up to the user to insert either images or texts or whatever.
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            Log.e("fatal", "I got an error", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 3.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    private void createBbTable( String bottomBarTableName ) {
        //here we must create a table called bb_0 (which is actually bottombar of the first nav) if not yet created, and it will contain (now we will fetch) all the existing bottom bar tabs info
        db.execSQL("CREATE TABLE IF NOT EXISTS '" + bottomBarTableName + "' ( " +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "index1 INTEGER, " +//we cannot name "index" or 'index' they're reserved for some reason.
                "icon TEXT);");
        //adding just one item (if the table has been just created - this check is needed), which corresponds to the navIndex.
        Cursor cursor_bb = db.query("SELECT * FROM '" + bottomBarTableName + "'");
        if( cursor_bb.getCount() == 0 ) {
            ContentValues contentValues = new ContentValues();
            //contentValues.put( "uid", 0);
            contentValues.put( "title", "Option 1");
            contentValues.put( "index1", 0 );
            contentValues.put( "icon", "ic_no_icon" );
            //I guess "icon" will be null, and it's fine to be null.
            db.insert( bottomBarTableName, SQLiteDatabase.CONFLICT_NONE, contentValues ); //returns -1 if failure. https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase
        }
    }

    void loadOnNavigate( final int navIndex ) { //have to load everything
        Log.i("loadOnNavigate", "inside");
        Looper.prepare(); //needed for some reason
        loadBb( navIndex, true );
        loadStatusBar( navIndex );
        loadTopBarBackgroundColor( navIndex );
        loadTopBarHamburgerColor( navIndex );
        loadTopTitleColor( navIndex );
        Log.i("loadOnNavigate", "before loadTop3DotsColor");
        loadTop3DotsColor( navIndex );
        Looper.loop();
    }

    void loadBb( final int indexOfNavMenuItem, final boolean setAll ) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        //Looper.prepare();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                Log.i("loadBb", "does run !");
                final String bottombar_backgroundColorTag = navEntityList.get( indexOfNavMenuItem ).bottombar_backgroundColorTag;
                if( !bottombar_backgroundColorTag.equalsIgnoreCase("none") ) { //my convention
                    activity.runOnUiThread( new Runnable() {
                        @Override
                        public void run() {
                            activity.bottomNavigationView.setVisibility(BottomNavigationView.VISIBLE);
                        }
                    });
                    Log.i("loadBb", "showing bb of " + indexOfNavMenuItem);
                    if( setAll ) {
                        activity.runOnUiThread( new Runnable() {
                            @Override
                            public void run() {
                                activity.setBottomBarBackgroundColor(bottombar_backgroundColorTag);
                            }
                        });
                        addBbItems( indexOfNavMenuItem );
                    }
                } else {
                    activity.runOnUiThread( new Runnable() {
                        @Override
                        public void run() {
                            activity.bottomNavigationView.setVisibility(BottomNavigationView.INVISIBLE);
                        }
                    });
                    Log.i("loadBb", "hiding bb of " + indexOfNavMenuItem);
                }
            }

            private void addBbItems( final int indexOfNavMenuItem ) {
                //now removing UI tabs (if any) of index 1 and on
                activity.runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        activity.bottomNavOperations.keepOnly1Item();
                    }
                });

                String bottomBarTableName = generateBbTableName( indexOfNavMenuItem );
                Cursor cursor_bb = db.query("SELECT * FROM '" + bottomBarTableName + "'");
                if( cursor_bb.moveToNext() ) { //this is the first item so we rename (instead of add)
                    Log.i("loadBbItems", "first item of " + bottomBarTableName);
                    final String navTitle = cursor_bb.getString( cursor_bb.getColumnIndex("title") );
                    final String icon = cursor_bb.getString( cursor_bb.getColumnIndex("icon") );
                    if( navTitle != null && !navTitle.equals("") ) { //must be true
                        activity.runOnUiThread( new Runnable() {
                            @Override
                            public void run() {
                                Log.i("loadBbItems", "first item ");
                                activity.optionsMenu.renameBottomMenuItem(0, navTitle);
                                if( icon != null && !icon.equals("") ) {
                                    activity.bottomNavOperations.setIconOfCheckedMenuItem( icon, 0 );
                                }
                            }
                        });
                    }
                }


                //Now adding UI tabs from db
                int index = 0;
                while ( cursor_bb.moveToNext() ) { //this starts with the second item and on.
                    final int index1 = ++index;
                    Log.i("loadBbItems", "item index " + index + " of " + bottomBarTableName);
                    final String navTitle = cursor_bb.getString( cursor_bb.getColumnIndex("title") ); //index of column is 1 (but it's ok)
                    final String icon = cursor_bb.getString( cursor_bb.getColumnIndex("icon") );
                    if( navTitle != null && !navTitle.equals("") ) {
                        activity.runOnUiThread( new Runnable() {
                            @Override
                            public void run() {
                                Log.i("loadBbItems", "item index " + index1);
                                activity.optionsMenu.addBottomMenuItem( navTitle );
                                if( icon != null && !icon.equals("") ) {
                                    activity.bottomNavOperations.setIconOfCheckedMenuItem( icon, index1 );
                                }
                            }
                        });
                    }
                }
                //I have to setchecked the first bottom item tab
                activity.runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        activity.bottomMenu.getItem(0).setChecked(true);
                    }
                });
            }
        }, 100); //unfortunately needed.

    }

    private void loadTop3DotsColor(int navIndex) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        final NavEntity navEntity = navEntityList.get( navIndex );
        Log.i("loadTop3DotsColor", "does run !");
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                Log.i("loadTop3DotsColor", "inside runOnUI");
                final String backgroundColorTag = navEntity.topBar_3dotsColorTag;
                //ironically, we didn't need a 100 ms delay here ??
                if( backgroundColorTag != null && !backgroundColorTag.equalsIgnoreCase("none") ) {
                    activity.setTopBar3DotsColor( backgroundColorTag );
                } else {
                    activity.setTopBar3DotsColor( "colorWhite" );
                }
            }
        });
    }

    private void loadTopTitleColor(int navIndex) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        final NavEntity navEntity = navEntityList.get( navIndex );
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                final String backgroundColorTag = navEntity.topBar_titleColorTag;
                //ironically, we didn't need a 100 ms delay here ??
                if( backgroundColorTag != null && !backgroundColorTag.equalsIgnoreCase("none") ) {
                    activity.setTopBarTitleColor( backgroundColorTag );
                } else {
                    activity.setTopBarTitleColor( "colorWhite" );
                }
            }
        });
    }

    private void loadTopBarHamburgerColor(int navIndex) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        final NavEntity navEntity = navEntityList.get( navIndex );
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                final String backgroundColorTag = navEntity.topBar_hamburgerColorTag;

                //ironically, we didn't need a 100 ms delay here ??
                if( backgroundColorTag != null && !backgroundColorTag.equalsIgnoreCase("none") ) {
                    activity.setTopBarHamburgerColor( backgroundColorTag );
                } else {
                    activity.setTopBarHamburgerColor( "colorWhite" );
                }
            }
        });
    }

    private void loadTopBarBackgroundColor(int navIndex) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        final NavEntity navEntity = navEntityList.get( navIndex );
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                final String backgroundColorTag = navEntity.topBar_backgroundColorTag;

                //ironically, we didn't need a 100 ms delay here ??
                if( backgroundColorTag != null && !backgroundColorTag.equalsIgnoreCase("none") ) {
                    activity.setTopBarBackgroundColor( backgroundColorTag );
                } else {
                    activity.setTopBarBackgroundColor( "design_default_color_primary" );
                }
            }
        });
    }

    private void loadStatusBar( final int indexOfNavMenuItem ) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        final NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                setStatusBarColor();
                setStatusBarTint();
            }

            void setStatusBarTint() {
                final boolean isStatusBarDark = navEntity.statusBar_dark;
                activity.setStatusBarIconTint( isStatusBarDark );
                activity.setStatusBarIconTintMenuItem( isStatusBarDark );
            }

            private void setStatusBarColor() {
                final String backgroundColorTag = navEntity.statusBar_backgroundColorTag;
                if( backgroundColorTag != null && !backgroundColorTag.equalsIgnoreCase("none") ) {
                    activity.setStatusBarColor( backgroundColorTag );
                } else {
                    activity.setStatusBarColor( "colorPrimaryDark" );
                }
            }
        });
    }

    void onCreate() {
        new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                //This is for the first time the admin uses the app
                insertNavHeader();
                insertNavEntity();
                SupportSQLiteOpenHelper supportSQLiteOpenHelper = db_room.getOpenHelper(); //referring to the opened connection to the database. //very good explanation : https://stackoverflow.com/questions/17348480/how-do-i-prevent-sqlite-database-locks - related https://stackoverflow.com/questions/8104832/sqlite-simultaneous-reading-and-writing. And this is related as well https://www.sqlite.org/lockingv3.html
                db = supportSQLiteOpenHelper.getWritableDatabase(); //enableWriteAheadLogging() When write-ahead logging is not enabled (the default), it is not possible for reads and writes to occur on the database at the same time. https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#create(android.database.sqlite.SQLiteDatabase.CursorFactory)
                //It's better to make a mechanism that corrects itself in case of an error. I.e. make database tables coherent. But I will leave that for now. In case of an error, I believe the app won't crash and it will show something anyway, and for now it's up to the admin to correct whatever he wishes.
                setBottomBarTableAndFirstBottomNavContentTable(0);
                //https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#execSQL(java.lang.String,%20java.lang.Object[])
                loadNavEntities();
                loadOnNavigate(0);
            }

            private void insertNavEntity() {
                List<NavEntity> navEntityList = permanentDao.getAllNav();
                if ( navEntityList.size() == 0 ) { //I believe it won't be null the first time we enter. It's just an empty list.
                    //Log.i("Youssef", "inside MainActivity : onStart. No nav entity exists.");
                    //create a record. We must have 1 record. We fetch its name from nav menu
                    NavEntity navEntity = new NavEntity();
                    navEntity.title = activity.navMenu.getItem(0).getTitle().toString();
                    navEntity.index = 0;
                    navEntity.bottombar_backgroundColorTag = "colorWhite";
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { //getMainLooper means it runs on the UI thread
                        @Override
                        public void run() {
                            activity.setBottomBarBackgroundColor( "colorWhite" );
                            activity.navOperations.setIconOfCheckedMenuItem( "ic_action_home", 0);
                            activity.optionsMenu.setFirstOptionsMenuIcon();
                        }
                    }, 100); //I delayed because setFirstOptionsMenuIcon contains something with the toolbar
                    navEntity.iconTag = "ic_action_home";
                    Log.i("Youssef", "before inserting the first navEntity");
                    permanentDao.insertNav( navEntity );
                    Log.i("Youssef", "after inserting the first navEntity");
                } else {
                    Log.i("Youssef", "We already have an entity ??");
                    //I can't assign the UI here, not until all is inflated and so on.
                    //Now to get whether the user has a bottombar or not (for each navEntity)
                }
            }

            private void insertNavHeader() {
                NavHeaderEntity navHeaderEntity = permanentDao.getNavHeader();
                if( navHeaderEntity == null ) { //it was actually null the first time we entered.
                    //Log.i("Youssef", "inside MainActivity : onStart. No nav header entity exists.");
                    //create a record. We only need 1.
                    navHeaderEntity = new NavHeaderEntity();
                    permanentDao.insertNavHeader( navHeaderEntity ); //it worked even without specifying anything in the just-created navHeaderEntity
                } else {
                    //Log.i("Youssef", "inside MainActivity : onStart. A nav header entity already exists.");
                    //I can't assign the UI here, not until all is inflated and so on.
                }
            }

        }.start(); //I'm not sequencing threads because I'm assuming that this thread is almost intantaneous

    }

    private void loadNavEntities() {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.navOperations.updateNavItem(0, navEntityList.get(0).title, navEntityList.get(0).iconTag );
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        activity.updateToolbarTitle(0);
                    }
                }, 100); //unfortunately needed.

                //activity.navOperations.addAnItem();
                for( int i = 1 ; i < navEntityList.size() ; i++ ) {
                    activity.navOperations.addAnItem( navEntityList.get(i) );
                }
                activity.navOperations.setupNavigation();
            }
        });
    }

    void setNameOfNavItem( final int indexOfNavMenuItem, final String name ) {
        new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                final List<NavEntity> navEntityList = permanentDao.getAllNav();
                NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
                navEntity.title = name;
                permanentDao.updateNav( navEntity );
            }
        }.start();
    }

    void setBbBackgroundColorTag( final int indexOfNavMenuItem, final String bottombar_backgroundColorTag ) {
        Log.i("setBbBackgroundColor", "in setBbBackgroundColorTag, of " + indexOfNavMenuItem + " to tag "
                + bottombar_backgroundColorTag);
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        Log.i("setBbBackgroundColor", "in setBbBackgroundColorTag. position 1");
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        Log.i("setBbBackgroundColor", "in setBbBackgroundColorTag. position 2");
        navEntity.bottombar_backgroundColorTag = bottombar_backgroundColorTag;
        Log.i("setBbBackgroundColor", "in setBbBackgroundColorTag. position 3");
        permanentDao.updateNav( navEntity ); //this might cause sometimes a silent error, such that the statements after it don't work
        Log.i("setBbBackgroundColor", "in setBbBackgroundColorTag, just to make sure : " +
                permanentDao.getAllNav().get(indexOfNavMenuItem).bottombar_backgroundColorTag );
    }

    void setTopBarBackgroundColorTag( final int indexOfNavMenuItem, final String colorTag ) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        navEntity.topBar_backgroundColorTag = colorTag;
        permanentDao.updateNav( navEntity );
    }
    void setTopBarHamburgerColorTag(int indexOfNavMenuItem, String tag) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        navEntity.topBar_hamburgerColorTag = tag;
        permanentDao.updateNav( navEntity );
    }

    void setTopBarTitleColorTag( int indexOfNavMenuItem, String tag) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        navEntity.topBar_titleColorTag = tag;
        permanentDao.updateNav( navEntity );
    }

    void setTopBar3DotsColorTag(int indexOfNavMenuItem, String tag) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        navEntity.topBar_3dotsColorTag = tag;
        permanentDao.updateNav( navEntity );
    }

    void setStatusBarColorTag( final int indexOfNavMenuItem, final String colorTag ) {
        final List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
        navEntity.statusBar_backgroundColorTag = colorTag;
        permanentDao.updateNav( navEntity );
    }

    void setStatusBarIconTint( final int indexOfNavMenuItem, final boolean isChecked ) {
        new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                final List<NavEntity> navEntityList = permanentDao.getAllNav();
                NavEntity navEntity = navEntityList.get( indexOfNavMenuItem );
                navEntity.statusBar_dark = isChecked;
                permanentDao.updateNav( navEntity );
            }
        }.start();
    }

    void deleteBbTable() {
        Log.i("Youssef", "in deleteBbTable");
        final String bottomBarTableName = generateBbTableName( activity.navOperations.getCheckedItemOrder() );
        Log.i("Youssef", "in deleteBbTable");
        deleteTable( bottomBarTableName );
    }

    void deleteBottomNavContentTablesButKeepUpTo( final int startIndex ) { //e.g. 0 to keep only 0 and -1 to remove all.
        int navIndex = activity.navOperations.getCheckedItemOrder();
        int size = activity.bottomMenu.size();
        for( int i = size - 1 ; i > startIndex ; i-- ) {
            deleteTable( generateBottomNavContentTableName( navIndex, i ) );
        }
    }

    private void deleteTable( final String tableName ) {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        try {
            Log.i("deleteTable", "before deleting table " + tableName );
            db.execSQL("DROP TABLE IF EXISTS " + tableName + ";");
            Log.i("deleteTable", "after deleting table " + tableName );
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 4.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    void setIconOfCheckedNavMenuItem( final String tag, final int nav_menuitem_index ) { //we know it won't be null, so I won't protect it
        List<NavEntity> navEntityList = permanentDao.getAllNav();
        NavEntity navEntity = navEntityList.get( nav_menuitem_index );
        navEntity.iconTag  = tag;
        Log.i("setIconNav", "nav icon is set in db for " + nav_menuitem_index );
        permanentDao.updateNav( navEntity );
    }

    void setIconOfCheckedBottomNavMenuItem( String tag, int navIndex ) {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final String bottomBarTableName = generateBbTableName( navIndex );
        try {
            ContentValues contentValues = new ContentValues();
            //contentValues.put( "uid", 0);
            //contentValues.put( "title", "Option 1");
            //contentValues.put( "index1", 0 );
            contentValues.put( "icon", tag );
            final int bottomNavTab = activity.bottomNavOperations.getCheckedItemOrder();
            Log.i("setIcon", "bottomNavTab = " + bottomNavTab );
            int rowsUpdated = db.update( bottomBarTableName, SQLiteDatabase.CONFLICT_NONE, contentValues, "index1 = ?", //don't say "WHERE" before "index1 = ?", it's already there
                    new String[]{ String.valueOf( bottomNavTab ) } );
            Log.i("setIcon", "rows updated = " + rowsUpdated);
            if( rowsUpdated == 0 ) {//must not happen
                Log.i("setIcon", "failed to update");
            }
/*
            Cursor cursor_bb = db.query("SELECT 'uid' FROM '" + bottomBarTableName + "' WHERE index1 = ? ",
                    new String[]{ String.valueOf(activity.bottomNavOperations.getCheckedItemOrder() ) } ); //'uid', 'title', 'index1', 'icon'
            //SupportSQLiteQuery
            if( cursor_bb.getCount() > 0 && cursor_bb.moveToNext() ) { //we have to find it

                cursor_bb.getInt( cursor_bb.getColumnIndex("uid") )

            } else {
                Log.i("setIcon", "We haven't gotten the row instance");
            }
 */
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            Log.e("setIcon", "I got an error", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 5.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }
    void addBottomMenuItem( final int navIndex, final String userInputText ) {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final String bottomBarTableName = generateBbTableName( navIndex );
        try {
            Cursor cursor_bb = db.query("SELECT * FROM '" + bottomBarTableName + "'");
            if( cursor_bb.getCount() != 0 ) { //must be true
                ContentValues contentValues = new ContentValues();
                //contentValues.put( "uid", 0);
                contentValues.put( "title", userInputText);
                cursor_bb.moveToLast();
                final int bottomIndex = cursor_bb.getInt( cursor_bb.getColumnIndex("index1") ) + 1;
                contentValues.put( "index1", bottomIndex );
                //contentValues.put( "icon", tag );
                //I guess "icon" will be null, and it's fine to be null.
                db.insert( bottomBarTableName, SQLiteDatabase.CONFLICT_NONE, contentValues ); //returns -1 if failure. https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase
                db.execSQL("CREATE TABLE IF NOT EXISTS " + generateBottomNavContentTableName( navIndex, bottomIndex ) + " ( " +
                        "uid INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "type TEXT, " +
                        "content TEXT);");
            }
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            Log.e("setIcon", "I got an error", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 6.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    void renameBottomMenuItem( final String userInputText ) {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final String bottomBarTableName = generateBbTableName( activity.navOperations.getCheckedItemOrder() );
        try {
            ContentValues contentValues = new ContentValues();
            //contentValues.put( "uid", 0);
            contentValues.put( "title", userInputText);
            //contentValues.put( "index1", 0 );
            //contentValues.put( "icon", tag );
            int rowsUpdated = db.update( bottomBarTableName, SQLiteDatabase.CONFLICT_NONE, contentValues, "index1 = ?",
                    new String[]{ String.valueOf(activity.bottomNavOperations.getCheckedItemOrder() ) } );
            Log.i("renameBottom..", "rows updated = " + rowsUpdated);
            if( rowsUpdated == 0 ) {//must not happen
                Log.i("renameBottom..", "failed to update");
            }
            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            Log.e("renameBottom..", "I got an error", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 7.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    void deleteBottomMenuItem() {
        db.beginTransaction(); //if you're thinking in using transaction : https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
        final int navIndex = activity.navOperations.getCheckedItemOrder();
        final int bottomIndex = activity.bottomNavOperations.getCheckedItemOrder();
        final String bottomBarTableName = generateBbTableName( navIndex );
        try {
            int rowsDeleted = db.delete( bottomBarTableName,"index1 = ?",
                    new String[]{ String.valueOf( bottomIndex ) } );
            Log.i("deleteBottom..", "rows deleted = " + rowsDeleted );
            if( rowsDeleted == 0 ) { //must not happen
                Log.i("deleteBottom..", "failed to delete");
            }
            concatenateBottomItemIndexes( bottomIndex, bottomBarTableName );
            db.execSQL("DROP TABLE IF EXISTS " + generateBottomNavContentTableName( navIndex, bottomIndex ) + ";");

            db.setTransactionSuccessful(); //to commit
        } catch(Exception e) {
            Log.e("setIcon", "I got an error", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 8.");
                }
            });
        } finally {
            db.endTransaction();
        }
    }

    private void concatenateBottomItemIndexes( final int bottomIndex, final String bottomBarTableName ) {
        Cursor cursor_bb = db.query("SELECT * FROM '" + bottomBarTableName + "'");
        for( int i = bottomIndex ; i < cursor_bb.getCount() ; i++ ) { //I have deleted a row, this is why it's not "i < cursor_bb.getCount() - 1"
            Log.i("concatenateBottom..", "i = " + i);
            ContentValues contentValues = new ContentValues();
            //contentValues.put( "uid", 0);
            //contentValues.put( "title", userInputText);
            contentValues.put( "index1", i );
            //contentValues.put( "icon", tag );
            int rowsUpdated = db.update( bottomBarTableName, SQLiteDatabase.CONFLICT_NONE, contentValues, "index1 = ?",
                    new String[]{ String.valueOf( i + 1 ) } );
            Log.i("concatenateBottom..", "rows updated = " + rowsUpdated);
            if( rowsUpdated == 0 ) {//must not happen
                Log.i("concatenateBottom..", "failed to update");
            }
        }
    }

    //##########################################################################################################################
//####################### Nav Header Stuff #################################################################################
//##########################################################################################################################
    void loadNavHeaderStuff() {
        (new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                loadNavHeaderBackgroundColor();
                loadNavHeaderTitles();
                loadNavHeaderImage(); //loading the image from the last known URI (if selected by user)
            }
        }).start();
    }

    private void loadNavHeaderBackgroundColor() {
        NavHeaderEntity navHeaderEntity = permanentDao.getNavHeader();
        //Log.i("Youssef", "Loading nav host background color from the database");
        final String backgroundColorTag = navHeaderEntity.backgroundColor;
        if( backgroundColorTag != null && !backgroundColorTag.equals("") ) {
            final LinearLayout linearLayout = activity.findViewById(R.id.linearlayout_navheader);
            final int color_id = activity.getResources().getIdentifier( backgroundColorTag,
                    "color", activity.getPackageName() );
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    linearLayout.setBackgroundColor( activity.getResources().getColor( color_id ) );
                }
            });
        }
    }

    private void loadNavHeaderImage() {
        final ImageButton imageButton_navheadermain = activity.findViewById(R.id.imagebutton_navheadermain);
        String imagePath = permanentDao.getNavHeader().imagePath; //interesting how the compiler does not complain for an NPE
        if( imagePath != null && !imagePath.equals("") ) {
            //Log.i("Youssef", "imagePath is " + imagePath);
            final Uri imageUri = Uri.fromFile(new File(imagePath));
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imageButton_navheadermain.setImageURI( imageUri ); //It works even if the path contains spaces.
                }
            });
        }
    }

    private void loadNavHeaderTitles() {
        final EditText editText_navHeaderTitle = activity.findViewById(R.id.editText_navheadertitle);
        final EditText editText_navHeaderSubtitle = activity.findViewById(R.id.editText_navheadersubtitle);
        final String navHeaderTitle = permanentDao.getNavHeader().title; //interesting how the compiler does not complain for an NPE
        if( navHeaderTitle != null && !navHeaderTitle.equals("") ) {
            //Log.i("Youssef", "navHeaderTitle is " + navHeaderTitle);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    editText_navHeaderTitle.setText( navHeaderTitle );
                }
            });
        }
        final String navHeaderSubTitle = permanentDao.getNavHeader().subtitle; //interesting how the compiler does not complain for an NPE
        if( navHeaderSubTitle != null && !navHeaderSubTitle.equals("") ) {
            //Log.i("Youssef", "navHeaderSubTitle is " + navHeaderSubTitle);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    editText_navHeaderSubtitle.setText( navHeaderSubTitle );
                }
            });
        }
    }

    void saveNavHeaderBackgroundColor( final String tag ) {
        ( new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                NavHeaderEntity navHeaderEntity = permanentDao.getNavHeader();
                //Log.i("Youssef", "Saving nav host background onto the database");
                navHeaderEntity.backgroundColor = tag;
                updateNavHeader(navHeaderEntity);
            }
        } ).start();
    }

    void saveNavHeaderTitles( final EditText editText, final EditText editText_navHeaderTitle,
                              final EditText editText_navHeaderSubtitle) {
        ( new Thread() { //opening the database needs to be on a separate thread.
            public void run() {
                NavHeaderEntity navHeaderEntity = permanentDao.getNavHeader();
                if( editText.equals( editText_navHeaderTitle )) {
                    ////Log.i("Youssef", "edittext header title has lost focus");
                    navHeaderEntity.title = editText.getText().toString();
                    updateNavHeader( navHeaderEntity );
                }
                if( editText.equals( editText_navHeaderSubtitle )) {
                    ////Log.i("Youssef", "edittext header subtitle has lost focus");
                    navHeaderEntity.subtitle = editText.getText().toString();
                    updateNavHeader( navHeaderEntity );
                }
            }
        } ).start();
    }

    void saveNavHeaderImg( final String imagePath ) {
        ( new Thread() {
            public void run() {
                NavHeaderEntity navHeaderEntity = permanentDao.getNavHeader();
                navHeaderEntity.imagePath = imagePath;
                updateNavHeader(navHeaderEntity);
            }
        }).start();
    }

    private synchronized void updateNavHeader( NavHeaderEntity navHeaderEntity ) { //synchronized might not be needed as this is probably inherent.
        if (!db.isOpen()) { //will not happen.
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.toast("Unable to save data internally. Data integrity is not guaranteed.", Toast.LENGTH_LONG);
                    Log.i("error", "position 9.");
                }
            });
            return;
        }
        permanentDao.updateNavHeader(navHeaderEntity);
    }

//##########################################################################################################################
//#######################                  #################################################################################
//##########################################################################################################################

}
