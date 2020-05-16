package com.youssefdirani.advertise_admin;

import android.app.AlertDialog;
import android.content.ComponentCallbacks2;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.jetbrains.annotations.NotNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;

public class MainActivity extends AppCompatActivity {

    public boolean isMemoryLow= false; //this can be checked before we enter or enlarge the database
    @Override
    public void onTrimMemory(int level) { //implemented by ComponentCallbacks2 automatically in AppCompatActivity
        // Determine which lifecycle or system event was raised.
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */
                //break;
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */
                //break;
            default:
                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                isMemoryLow = true;
                System.gc();
                break;
        }
    }

    Menu navMenu;
    Toolbar toolbar;
    BottomNavigationView bottomNavigationView;
    Menu bottomMenu;

    DbOperations dbOperations;
    NavOperations navOperations;
    BottomNavOperations bottomNavOperations = new BottomNavOperations( MainActivity.this );
    public MutableLiveData<String> lastBottomNav = new MutableLiveData<>();
    OptionsMenu optionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("Youssef", "inside MainActivity : onCreate");
        dbOperations = new DbOperations( this );
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        navOperations = new NavOperations( MainActivity.this, toolbar );
        navMenu = navOperations.getNavigationView().getMenu();
        navOperations.startingSetup( navMenu );

        bottomNavigationView = findViewById( R.id.bottomnavview);
        bottomMenu = bottomNavigationView.getMenu();
        optionsMenu = new OptionsMenu( MainActivity.this, bottomMenu );
        bottomNavOperations.setupBottomNavigation();
    }

    //Called when the user presses on the stack navigation icon in order to navigate https://developer.android.com/reference/android/support/v7/app/AppCompatActivity#onSupportNavigateUp()
    @Override
    public boolean onSupportNavigateUp() {
        //Log.i("Youssef", "MainActivity - inside onSupportNavigateUp");
        return navOperations.onSupportNavigateUp()
                || super.onSupportNavigateUp();
    }

    public void hideOptionsMenuAndBottomMenu() {
        toolbar.getMenu().setGroupVisible( R.id.optionsmenu_actionitemgroup,false );
        bottomNavigationView.setVisibility( BottomNavigationView.INVISIBLE );
    }

    public void showOptionsMenuAndBottomMenu( int indexOfNavMenuItem ) {
        toolbar.getMenu().setGroupVisible( R.id.optionsmenu_actionitemgroup,true );

        //we need to get it from the database to know whether  to show it or not.
        bottomNavigationView.setVisibility( BottomNavigationView.VISIBLE );

    }


    void toast( String textToToast, int duration ) {
        Toast.makeText(MainActivity.this, textToToast, duration).show();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("Youssef", "inside MainActivity : onStart");
        //dbOperations
        bottomNavOperations.showOnly1Item();
        dbOperations.setInitials();
        dbOperations.loadNavEntities();
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.i("Youssef", "inside MainActivity : onResume");

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("Youssef", "inside MainActivity : onPause");
    }
    @Override
    public void onStop() {
        super.onStop();
        Log.i("Youssef", "inside MainActivity : onStop");
        //dbOperations.onStop();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("Youssef", "inside MainActivity : onDestroy");
    }

    //Called when user presses the 3 vertical dots on the right. Initiated when the user presses the 3 vertical dots.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);//this should be before adding menus maybe. findItem https://stackoverflow.com/questions/16500415/findviewbyid-for-menuitem-returns-null (and using menu.findItem may be better than findViewById ?, not sure)
        Log.i("Youssef", "MainActivity - inside onCreateOptionsMenu");

        return true;
    }

    public void setFirstOptionsMenuIcon() { //for technical reasons - weird - it's ChooseMenuIconFragment the reason.
        optionsMenu.setFirstOptionsMenuIcon();
    }

    public void setLayoutColor( int linearlayout_id, final String tag ) {
        navOperations.setLayoutColor( linearlayout_id, tag );
    }

    public void setIconOfCheckedMenuItem( String tag, int nav_menuitem_index, String menu ) {
        //since getCheckedItemOrder() when called from ChooseNavMenuIconFragment can't know the index (order), so we're using nav_menuitem_index
        ////Log.i("seticon", "item index is " + getCheckedItemOrder());
        if( tag.equalsIgnoreCase("ic_no_icon") ) {
            if( menu.equals("nav menu") ) {
                MenuItem menuItem = navMenu.getItem( nav_menuitem_index );
                menuItem.setIcon(0);
            } else if( menu.equals("bottom nav menu") ) {
                //it's good that the bottom bar still remembers the checked item order after returning back from ChooseNavMenuIconFragment. Here we won't be using nav_menuitem_index, it's been useful for the toolbar
                int bottomNavIndex = bottomNavOperations.getCheckedItemOrder();
                MenuItem menuItem = bottomMenu.getItem( bottomNavIndex );
                menuItem.setIcon(0);
            }
            return;
        }
        int icon_drawable_id = getResources().getIdentifier( tag, "drawable", getPackageName() );
        Drawable icon = getResources().getDrawable( icon_drawable_id );
        if( menu.equals("nav menu") ) {
            navMenu.getItem( nav_menuitem_index ).setIcon( icon );
        } else if( menu.equals("bottom nav menu") ) {
            bottomMenu.getItem( bottomNavOperations.getCheckedItemOrder() ).setIcon( icon ); //it's good that the bottom bar still remembers the checked item order after returning back from ChooseNavMenuIconFragment
        }
    }

    public void updateToolbarTitle( int indexOfNewMenuItem ) {
        toolbar.setTitle( navMenu.getItem( indexOfNewMenuItem ).getTitle() );
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Log.i("Youssef", "MainActivity - inside onPrepareOptionsMenu");
        return true;
    }

    //Called when the user presses a menu item below the 3 vertical dots.
    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item ) {
        //Log.i("Youssef", "MainActivity - inside onOptionsItemSelected");
        return optionsMenu.onOptionsItemSelected( item, super.onOptionsItemSelected(item) );
    }

    //These here are callbacks from other classes.
    public void setStatusBarColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor( ContextCompat.getColor(this, color_id ) );
        }
    }
    public void setTopBarBackgroundColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        toolbar.setBackgroundColor( ContextCompat.getColor(this, color_id) );
    }
    public void setTopBarHamburgerColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        toolbar.getNavigationIcon().setColorFilter(
                ContextCompat.getColor(this, color_id ), PorterDuff.Mode.SRC_ATOP );
    }

    public void setTopBarTitleColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        toolbar.setTitleTextColor( getResources().getColor( color_id ) ); //the action bar text
    }
    public void setTopBar3DotsColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        toolbar.getOverflowIcon().setColorFilter(
                ContextCompat.getColor(this, color_id ), PorterDuff.Mode.SRC_ATOP );
    }
    public void setBottomBarBackgroundColor( String tag ) {
        int color_id = getResources().getIdentifier( tag, "color", getPackageName() );
        bottomNavigationView.setBackgroundColor( ContextCompat.getColor(this, color_id) );
    }

    //Related to the navigation menu. Used to retrieve the image from gallery and save it
    @Override
    public void onActivityResult( int reqCode, int resultCode, Intent data ) {
        super.onActivityResult(reqCode, resultCode, data);
        navOperations.onActivityResult( reqCode, resultCode, data );
    }

    @Override
    public void onBackPressed() {
        if( navOperations.getCheckedItemOrder() != -1 ) { //root (top-level). IDK how universal this is
            finish();
        }
        super.onBackPressed();  // optional depending on your needs
    }

}