package ch.epfl.sweng.team7.hikingapp.mapActivityElements;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;

import java.util.ArrayList;

import ch.epfl.sweng.team7.hikingapp.R;

/**
 * Class used to create a display at the bottom
 * of the MapActivity, with some information.
 */
public final class BottomInfoView {

    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final BottomInfoView instance = new BottomInfoView();
    private static final float DEFAULT_TITLE_SIZE = 20f;
    private static final int DEFAULT_BG_COLOR = 0xff7f7f7f;

    private Context mContext;
    private TableLayout mapTableLayout;
    private TextView mTitle;
    private ArrayList<TextView> mInfoLines;

    private int mLockEntity = -1;

    /**
     * Method has to be called once for initialization,
     * before performing any other operations.
     * @param context Context of the MapActivity
     */
    public void initialize(Context context) {
        mContext = context;
        mapTableLayout = new TableLayout(context);
        mapTableLayout.setId(R.id.mapTextTable);
        mapTableLayout.setBackgroundColor(DEFAULT_BG_COLOR);
        mapTableLayout.setVisibility(View.INVISIBLE);
        mTitle = new TextView(context);
        mTitle.setTextSize(DEFAULT_TITLE_SIZE);
        mTitle.setTextColor(DEFAULT_TEXT_COLOR);
        mInfoLines = new ArrayList<TextView>();

        mapTableLayout.addView(mTitle);
    }

    /**
     * Method called to display the information table.
     */
    public void show(int entity) {
        if (permissionGranted(entity)) {
            mapTableLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Method called to hide the information table
     */
    public void hide(int entity) {
        if (permissionGranted(entity)) {
            mapTableLayout.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Method returns the View associated with it
     * @return View corresponding to the mapTableLayout
     */
    public View getView() {
        return mapTableLayout;
    }

    /**
     * Sets a OnClickListener for the information display.
     * @param listener View.OnClickListener of the display
     */
    public void setOnClickListener(int entity, View.OnClickListener listener) {
        if (permissionGranted(entity)) {
            mapTableLayout.setOnClickListener(listener);
        }
    }

    /**
     * Method called to set the mTitle of the information table.
     * @param title new mTitle for the table.
     */
    public void setTitle(int entity, String title) {
        if (permissionGranted(entity)) {
            mTitle.setText(title);
            mTitle.setTextColor(DEFAULT_TEXT_COLOR);
        }
    }

    /**
     * Method called to edit a specific information line.
     * @param index index of the line
     * @param infoMessage new message to display
     */
    public void setInfoLine(int entity, int index, String infoMessage) {
        if (permissionGranted(entity)) {
            try {
                mInfoLines.get(index).setText(infoMessage);
                mInfoLines.get(index).setTextColor(DEFAULT_TEXT_COLOR);
            } catch (Exception e) {
            }
        }
    }

    /**
     * Method called to add a new information line.
     * @param infoMessage new message to display
     */
    public void addInfoLine(int entity, String infoMessage) {
        if (permissionGranted(entity)) {
            TextView infoView = new TextView(mContext);
            infoView.setText(infoMessage);
            infoView.setTextColor(DEFAULT_TEXT_COLOR);
            mInfoLines.add(infoView);
            mapTableLayout.addView(infoView);
        }
    }

    /**
     * Method called to clear all the information lines.
     */
    public void clearInfoLines(int entity) {
        if(permissionGranted(entity)) {
            for (TextView infoLineView : mInfoLines) {
                mapTableLayout.removeView(infoLineView);
            }
            mInfoLines.clear();
        }
    }

    /**
     * Method called to request a lock on this information table,
     * meaning no other entity will be able to edit its values.
     * @param entity ID of the entity requesting the lock
     */
    public void requestLock(int entity) {
        if (permissionGranted(entity)) {
            mLockEntity = entity;
        }
    }

    /**
     * Method called to release a lock on this information table,
     * meaning all other entities will be, again, able to edit its values.
     */
    public void releaseLock(int entity) {
        if (permissionGranted(entity)) {
            mLockEntity = -1;
        }
    }

    public static BottomInfoView getInstance() {
        return instance;
    }

    private BottomInfoView() {
    }

    /**
     * Checks if caller entity has permission over the table.
     * @param entity calling entity
     * @return true if it has permission, false otherwise
     */
    private boolean permissionGranted(int entity) {
        return (mLockEntity == entity || mLockEntity == -1);
    }
}
