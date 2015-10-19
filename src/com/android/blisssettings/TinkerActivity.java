/*
 * Copyright (C) 2015 The Pure Nexus Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.purenexussettings;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.Preference;
import android.provider.Settings;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Surface;
import android.view.View;

import java.util.Arrays;
import java.util.Stack;

import eu.chainfire.libsuperuser.Shell;

public class TinkerActivity extends AppCompatActivity {

    private DrawerLayout mDrawerLayout;
    private NavigationView mNavView;
    private ActionBarDrawerToggle mDrawerToggle;
    public static String mPackageName;
    private FragmentManager fragmentManager;

    // this allows first # entries in stringarray to be skipped from navdrawer
    public static int FRAG_ARRAY_START;

    // stuff for widget calls to open fragments
    public static final String EXTRA_START_FRAGMENT = "com.android.purenexussettings.tinkerings.EXTRA_START_FRAGMENT";

    public static final int REQUEST_CREATE_SHORTCUT = 3;
    public static final String PROJFI_PACKAGE_NAME = "com.google.android.apps.tycho";

    // example - used to retain slidetab position
    public static int LAST_SLIDE_BAR_TAB;

    // nav drawer title
    private CharSequence mDrawerTitle;

    // used to store app title/position
    private CharSequence mTitle;
    private int mItemPosition;

    // for backstack tracking
    private Stack<String> fragmentStack;

    // various bools for this or that
    private boolean mBackPress;
    private boolean mIgnoreBack;
    private boolean mFromClick;
    private boolean mIgnore;
    private boolean mMenu;
    private boolean fullyClosed;
    private boolean openingHalf;

    // slide menu items
    private String[] navMenuTitles;
    private String[] navMenuFrags;

    // info for buildprop editor
    public static String mEditName;
    public static String mEditKey;

    // info for app picker
    public static String mPrefKey;
    public static int mTitleArray;
    public static int mIconArray;
    public static int mKeyArray;

    // For handling quick back/About presses
    Handler myHandler = new Handler();

    private class Startup extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog = null;
        private Context context = null;
        private View parentView = null;
        private boolean suAvailable = false;

        public Startup setContext(Context context, View view) {
            this.context = context;
            this.parentView = view;
            return this;
        }

        @Override
        protected void onPreExecute() {
            // The progress dialog here is so the user will wait until root stuff is figured out.

            dialog = new ProgressDialog(context);
            dialog.setTitle("Hold on a sec");
            dialog.setMessage("Trying to find root...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);

            // A semi-hack way to prevent FCs when orientation changes during progress dialog showing
            TinkerActivity.lockCurrentOrientation((TinkerActivity) context);

            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Let's check to see if SU is there...
            suAvailable = Shell.SU.available();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            ((TinkerActivity) context).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            dialog.dismiss();

            if (!suAvailable) {
                String str = "Root not available?!? Some functions may not work as intended!";
                Snackbar.make(parentView, str, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tinker);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // set up some defaults
        FRAG_ARRAY_START = getResources().getIntArray(R.array.nav_drawer_cat_nums)[0];
        mTitle = mDrawerTitle = getTitle();
        mPackageName = getPackageName();
        LAST_SLIDE_BAR_TAB = 0;
        mBackPress = false;
        mIgnoreBack = false;
        mFromClick = false;
        mMenu = false;
        fullyClosed = true;
        openingHalf = true;

        // for backstack tracking
        fragmentStack = new Stack<>();

        // load slide menu items - titles and frag names
        navMenuTitles = getResources().getStringArray(R.array.nav_drawer_items);
        navMenuFrags = getResources().getStringArray(R.array.nav_drawer_fragments);

        // nav drawer icons from resources
        TypedArray navMenuIcons = getResources().obtainTypedArray(R.array.nav_drawer_icons);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNavView = (NavigationView) findViewById(R.id.slidermenu);

        // create navigationview items
        Menu menu = mNavView.getMenu();
        // pulled in crap menu in xml, need to clear it first
        menu.clear();

        // pull in category names and numbers in each
        String[] navMenuCats = getResources().getStringArray(R.array.nav_drawer_cats);
        int[] navMenuCatCounts = getResources().getIntArray(R.array.nav_drawer_cat_nums);

        // set up some counters
        int j=0;
        int total=0;
        SubMenu submenu=null;
        // go through the total possible menu list
        for (int i=0; i < navMenuTitles.length; i++) {
            // when the count equals a threshold value, increment/sum and add submenu
            if (i == (total + navMenuCatCounts[j])) {
                total += navMenuCatCounts[j];
                // format submenu headings
                SpannableString strcat= new SpannableString(navMenuCats[j]);
                strcat.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.alphawhite)), 0, strcat.length(),0);
                strcat.setSpan(new RelativeSizeSpan(0.85f), 0, strcat.length(), 0);
                strcat.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, strcat.length(), 0);
                // is the 10 * (j + 1) bit needed...? Maybe not... meh
                submenu = menu.addSubMenu((j + 1), 10 * (j + 1), 10 * (j + 1), strcat);
                j++;
            }
            // assuming all are skipped before first submenu, only add menu items if total <> 0
            if (total > 0) {
                // format menu item title
                SpannableString stritem= new SpannableString(navMenuTitles[i]);
                stritem.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.alphawhite)), 0, stritem.length(),0);
                // group id is j, i is item id and order..., then title
                submenu.add(j, i, i, stritem);
                // an attempt to add icon if included...
                if (navMenuIcons.getResourceId(i, -1) != -1) {
                    menu.getItem(i).setIcon(navMenuIcons.getResourceId(i, -1));
                }
            }
        }

        mNavView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                mItemPosition = item.getItemId();
                mFromClick = true;
                setTitle(navMenuTitles[mItemPosition]);
                removeCurrent();
                mDrawerLayout.closeDrawer(mNavView);
                return true;
            }
        });

        // Recycle the typed array
        navMenuIcons.recycle();

        // enabling action bar app icon and behaving it as toggle button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                toolbar, //nav menu toggle icon
                R.string.app_name, // nav drawer open - description for accessibility
                R.string.app_name // nav drawer close - description for accessibility
        ){
            @Override
            public void onDrawerClosed(View view) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mNavView);
                getSupportActionBar().setTitle(mTitle);
                // calling onPrepareOptionsMenu() to show action bar icons
                openingHalf = true;
                invalidateOptionsMenu();
                // now that the drawer animation is done - load fragment
                if (mIgnore || !mFromClick ) {
                    mIgnore = false;
                } else {
                    displayView(mItemPosition);
                }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setTitle(mDrawerTitle);
                // calling onPrepareOptionsMenu() to hide action bar icons
                openingHalf = false;
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                fullyClosed = (slideOffset == 0.0f);
                if (slideOffset < 0.5f && !openingHalf) {
                    openingHalf = true;
                    invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                } else if (slideOffset > 0.5f && openingHalf) {
                    openingHalf = false;
                    invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                }
            }

        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        fragmentManager = getFragmentManager();

        // check for root stuff
        (new Startup()).setContext(this, findViewById(R.id.frame_container)).execute();

        if (savedInstanceState == null) {
            // on first time display view for first nav item
            displayView(mItemPosition = getIntent().getIntExtra(EXTRA_START_FRAGMENT, 0));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private boolean checkPosition(int position) {
        // list out positions that should skip stack clearing
        // buildprop editprop apppicker fiswitch
        return position == 1 || position == 2 || position == 3 || position == 4;
    }

    private boolean checkEditProp(int position) {
        // see if current frag is editprop to force buildprop on backpress
        return position == 2;
    }

    /* Displaying fragment view for selected nav drawer list item */
    private void displayView(int position) {
        // before anything else - check to see if position matches intent-launching "frags" - for example
        // if ( navMenuTitles[position].equals("TARGETNAME") ) { position = 0; do something}
        boolean mKeepStack = checkPosition(position);

        // update the main content by replacing fragments
        Fragment frags = null;
        String fragname = navMenuFrags[position];
        try {
            frags = (Fragment)Class.forName(mPackageName + "." + fragname).newInstance();
            }
        catch (Exception e) {
            frags = null;
            }
        if (frags != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

            FragmentTransaction fragtrans = fragmentManager.beginTransaction();
            if (mFromClick || mMenu || mBackPress) {
                fragtrans.setCustomAnimations(R.anim.fadein, R.anim.fadeout, R.anim.fadein, R.anim.fadeout);
            }
            fragtrans.add(R.id.frame_container, frags);
            // The backstack should be cleared if not coming from a fragment flagged as stack keeping or from a backpress
            // After clearing the only entry should be About/main
            if (!mKeepStack && !mBackPress) {
                fragmentStack.clear();
                fragmentStack.push(navMenuFrags[0]);
            }
            // add fragment name to custom stack for backstack tracking
            // only do it if not a backpress, flagged as stack keeping, or dup of last entry
            if (!mBackPress && !mKeepStack && !(fragmentStack.size() >= 1 && fragmentStack.peek().equals(navMenuFrags[position]))) {
                fragmentStack.push(navMenuFrags[position]);
            }
            fragtrans.commit();

            // update selected item and title, then close the drawer
            if (mFromClick || mBackPress) {
                mFromClick = false;
                mBackPress = false;
            } else {
                setTitle(navMenuTitles[position]);
                if (mMenu) {
                    mMenu = false;
                    mItemPosition = position;
                } else {
                    mDrawerLayout.closeDrawer(mNavView);
                }
            }
            invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
        } else {
            // error in creating fragment
            Log.e("TinkerActivity", "Error in creating fragment");
        }
    }

    private void removeCurrent() {
        // update the main content by replacing fragments, first by removing the old
        FragmentTransaction fragtrans = fragmentManager.beginTransaction();
        fragtrans.setCustomAnimations(R.anim.fadein, R.anim.fadeout, R.anim.fadein, R.anim.fadeout);
        fragtrans.remove(fragmentManager.findFragmentById(R.id.frame_container));
        fragtrans.commit();
    }

    public void displayAppPicker(Preference object, int titles, int icons, int keys) {
        // stuff for apppicker fragment
        mPrefKey = object.getKey();
        mTitleArray = titles;
        mIconArray = icons;
        mKeyArray = keys;

        myHandler.removeCallbacksAndMessages(null);
        mMenu = true;
        removeCurrent();
        // below replicates the visual delay seen when launching frags from navdrawer
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayView(3);
            }
        }, 400);
    }

    public void displayBuildPropEditor() {
        myHandler.removeCallbacksAndMessages(null);
        mMenu = true;
        removeCurrent();
        // below replicates the visual delay seen when launching frags from navdrawer
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayView(1);
            }
        }, 400);
    }

    public void displayEditProp(String name, String key) {
        // put the name and key strings in here for editprop access
        mEditName = name;
        mEditKey = key;

        myHandler.removeCallbacksAndMessages(null);
        mMenu = true;
        removeCurrent();
        // below replicates the visual delay seen when launching frags from navdrawer
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayView(2);
            }
        }, 400);
    }

    public void displayFiSwitch() {
        myHandler.removeCallbacksAndMessages(null);
        mMenu = true;
        removeCurrent();
        // below replicates the visual delay seen when launching frags from navdrawer
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayView(4);
            }
        }, 400);
    }

    @Override
    public void onBackPressed() {
        boolean mKeepStack = checkPosition(mItemPosition);
        boolean mIsEditProp = checkEditProp(mItemPosition);

        if (!fullyClosed || mDrawerLayout.isDrawerOpen(mNavView)) {
            // backpress closes drawer if open
            mDrawerLayout.closeDrawer(mNavView);
        } else if (fragmentStack.size() > 1 || mKeepStack) {
            if (!mIgnoreBack) {
                mIgnoreBack = true;

                // cancels any pending postdelays just in case
                myHandler.removeCallbacksAndMessages(null);

                // removes latest (current) entry in custom stack if it wasn't one flagged for stack keeping and not added to stack
                if (!mKeepStack) {
                    fragmentStack.pop();
                }
                // uses fragment name to find displayview-relevant position
                final int position = Arrays.asList(navMenuFrags).indexOf(fragmentStack.lastElement());
                // set position based on above or 1 if iseditprop
                mItemPosition = mIsEditProp ? 1 : position;
                // a setup similar to onclickitem
                setTitle(navMenuTitles[mItemPosition]);
                removeCurrent();
                // below replicates the visual delay seen when launching frags from navdrawer
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBackPress = true;
                        displayView(mItemPosition);
                        mIgnoreBack = false;
                    }
                }, 400);
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tinker, menu);
        menu.findItem(R.id.action_launchhide).setChecked(!isLauncherIconEnabled());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Handle action bar actions click
        switch (item.getItemId()) {
            case R.id.action_about:
                if ( mItemPosition != 0 ) {
                    myHandler.removeCallbacksAndMessages(null);
                    mMenu = true;
                    removeCurrent();
                    // below replicates the visual delay seen when launching frags from navdrawer
                    myHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            displayView(0);
                        }
                    }, 400);
                }
                return true;
            case R.id.action_launchhide:
                boolean checked = item.isChecked();
                item.setChecked(!checked);
                setLauncherIconEnabled(checked);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* Called when invalidateOptionsMenu() is triggered */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // if nav drawer is opened/opening, hide the action items
        // add in bits to enable/disable menu items that are fragment specific
        if ( openingHalf ) {
            menu.setGroupVisible(R.id.action_items, true);
            boolean isbuildprop = (mItemPosition == 1);
            boolean iseditprop = (mItemPosition == 2);
            boolean isapppicker = (mItemPosition == 3);
            boolean isfiswitch = (mItemPosition == 4);
            menu.findItem(R.id.action_backup).setVisible(isbuildprop);
            menu.findItem(R.id.action_restore).setVisible(isbuildprop);
            menu.findItem(R.id.action_search).setVisible(isbuildprop);
            menu.findItem(R.id.action_discard).setVisible(iseditprop);
            menu.findItem(R.id.action_delete).setVisible(iseditprop);
            menu.findItem(R.id.action_fabhide).setVisible(isfiswitch);
            menu.findItem(R.id.action_launchhide).setVisible(!(isbuildprop || iseditprop || isapppicker ||isfiswitch));
            menu.findItem(R.id.action_about).setVisible(!(isbuildprop || iseditprop || isapppicker || isfiswitch));
        } else {
            menu.setGroupVisible(R.id.action_items, false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    /* When using the mDrawerToggle, you must call it during onPostCreate() and onConfigurationChanged()... */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggle
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /* Methods related to showing/hiding app from app drawer */
    public void setLauncherIconEnabled(boolean enabled) {
        int newState;
        PackageManager packman = getPackageManager();
        if (enabled) {
            newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } else {
            newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }
        packman.setComponentEnabledSetting(new ComponentName(this, LauncherActivity.class), newState, PackageManager.DONT_KILL_APP);
    }

    public boolean isLauncherIconEnabled() {
        PackageManager packman = getPackageManager();
        return (packman.getComponentEnabledSetting(new ComponentName(this, LauncherActivity.class)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    @Override
    protected void onActivityResult(int paramRequest, int paramResult, Intent paramData) {
        super.onActivityResult(paramRequest, paramResult, paramData);
        if (paramResult != -1 || paramData == null) {
            return;
        }
        switch (paramRequest)
        {
            case REQUEST_CREATE_SHORTCUT:
                Intent localIntent = paramData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
                localIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, paramData.getStringExtra(Intent.EXTRA_SHORTCUT_NAME));

                String keystring = localIntent.toUri(0).replaceAll("com.android.contacts.action.QUICK_CONTACT", Intent.ACTION_VIEW);

                Settings.Global.putString(getContentResolver(), TinkerActivity.mPrefKey, keystring);
                onBackPressed();

                return;
            default:
        }
    }

    public static void lockCurrentOrientation(Activity activity) {
        int currentRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = activity.getResources().getConfiguration().orientation;
        int frozenRotation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        switch (currentRotation) {
            case Surface.ROTATION_0:
                frozenRotation = orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case Surface.ROTATION_90:
                frozenRotation = orientation == Configuration.ORIENTATION_PORTRAIT
                        ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case Surface.ROTATION_180:
                frozenRotation = orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            case Surface.ROTATION_270:
                frozenRotation = orientation == Configuration.ORIENTATION_PORTRAIT
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
        }
        activity.setRequestedOrientation(frozenRotation);
    }

}