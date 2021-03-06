package ch.epfl.sweng.team7.hikingapp;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;

import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ch.epfl.sweng.team7.database.Annotation;
import ch.epfl.sweng.team7.database.DataManager;
import ch.epfl.sweng.team7.database.DataManagerException;
import ch.epfl.sweng.team7.database.GPSPathConverter;
import ch.epfl.sweng.team7.database.HikeData;
import ch.epfl.sweng.team7.database.HikePoint;
import ch.epfl.sweng.team7.database.UserData;
import ch.epfl.sweng.team7.gpsService.GPSManager;
import ch.epfl.sweng.team7.gpsService.containers.coordinates.GeoCoords;
import ch.epfl.sweng.team7.hikingapp.guiProperties.GUIProperties;
import ch.epfl.sweng.team7.hikingapp.mapActivityElements.BottomInfoView;
import ch.epfl.sweng.team7.network.RawHikePoint;

import static android.location.Location.distanceBetween;

public class MapActivity extends FragmentActivity {

    private final static String LOG_FLAG = "Activity_Map";
    private final static int DEFAULT_ZOOM = 15;
    private final static int BOTTOM_TABLE_ACCESS_ID = 1;
    private final static String OPEN_CAMERA = "android.media.action.IMAGE_CAPTURE";
    private final static String EXTRA_HIKE_ID =
            "ch.epfl.sweng.team7.hikingapp.HIKE_ID";
    private final static String EXTRA_EXIT = "exit";
    private static final int HIKE_LINE_COLOR = 0xff000066;
    private static final int HIKE_LINE_COLOR_SELECTED = Color.RED;
    private static LatLng mUserLocation;
    private static int mScreenWidth;
    private static int mScreenHeight;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private final GPSManager mGps = GPSManager.getInstance();
    private final BottomInfoView mBottomTable = BottomInfoView.getInstance();
    private DataManager mDataManager = DataManager.getInstance();
    private List<HikeData> mHikesInWindow;
    private Map<Marker, Long> mMarkerByHike = new HashMap<>();

    private List<DisplayedHike> mDisplayedHikes = new ArrayList<>();


    private boolean mFollowingUser = false;
    private Polyline mPolyRef;
    private Polyline mPrevPolyRef = null;
    private SearchView mSearchView;
    private ListView mSuggestionListView;
    private List<Address> mSuggestionList = new ArrayList<>();
    private SuggestionAdapter mSuggestionAdapter;
    private Geocoder mGeocoder;
    private ImageView mImageView;
    private ArrayList<Annotation> mListAnnotations = null;

    public final static String EXTRA_BOUNDS =
            "ch.epfl.sweng.team7.hikingapp.BOUNDS";
    private static final int MAX_SEARCH_SUGGESTIONS = 10;
    private static final int MIN_QUERY_LENGTH_FOR_SUGGESTIONS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.navigation_drawer);
        mGps.startService(this);

        if (getIntent().getBooleanExtra(EXTRA_EXIT, false)) {
            finish();
        }


        // nav drawer setup
        View navDrawerView = getLayoutInflater().inflate(R.layout.navigation_drawer, null);
        FrameLayout mainContentFrame = (FrameLayout) findViewById(R.id.main_content_frame);
        View mapView = getLayoutInflater().inflate(R.layout.activity_map, null);
        mainContentFrame.addView(mapView);

        setUpMapIfNeeded();

        // load items into the Navigation drawer and add listeners
        ListView navDrawerList = (ListView) findViewById(R.id.nav_drawer);
        NavigationDrawerListFactory navDrawerListFactory = new NavigationDrawerListFactory(
                navDrawerList, navDrawerView.getContext(), this);

        //creates a start/stop tracking button
        createTrackingToggleButton();

        //creates a pause/resume tracking button
        createPauseTrackingButton();

        //creates a AddAnnotation button
        createAnnotationButton();

        //Initializes the BottomInfoView
        createBottomInfoView();

        GUIProperties.setupButton(this, R.id.go_hikes_button, R.drawable.button_hike_list, 0);

        setGoToHikesButtonListener();

        setUpSearchView();

        mGeocoder = new Geocoder(this);

        if(mMap != null) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                @Override
                public View getInfoWindow(Marker marker) {
                    return null;
                }

                @Override
                public View getInfoContents(Marker marker) {
                    return null;
                }
            });
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        mGps.bindService(this);

        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.nav_drawer_layout);
        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGps.unbindService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGps.stopService();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processNewIntent();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(EXTRA_EXIT, true);
        startActivity(intent);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private LatLngBounds getBounds() {
        return mMap.getProjection().getVisibleRegion().latLngBounds;
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;
        mUserLocation = getUserPosition();
        LatLngBounds initialBounds = guessNewLatLng(mUserLocation, mUserLocation, 0.5);
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(initialBounds, mScreenWidth, mScreenHeight, 30));

        List<HikeData> hikesFound = new ArrayList<>();
        boolean firstHike = true;
        new DownloadHikeList().execute(new DownloadHikeParams(hikesFound, initialBounds, firstHike));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                mSearchView.onActionViewCollapsed(); // remove focus from searchview
                changePolyColor(mPrevPolyRef, 0);
                onMapClickHelper(point);
            }
        });

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                onCameraChangeHelper();
                mFollowingUser = false;
            }
        });

        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                if (mGps.tracking()) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    if (mFollowingUser) focusOnLatLng(latLng);
                    if (mPolyRef == null) {
                        startHikeDisplay();
                    }
                    if (!mGps.paused()) {
                        List<LatLng> points = mPolyRef.getPoints();
                        points.add(latLng);
                        mPolyRef.setPoints(points);
                    }
                }
            }
        });
    }

    private void processNewIntent() {
        Intent intent = getIntent();
        boolean displaySingleHike = false;

        if (intent != null && intent.hasExtra(HikeInfoActivity.HIKE_ID)) {
            String hikeIdStr = intent.getStringExtra(HikeInfoActivity.HIKE_ID);

            if (hikeIdStr != null) {
                long intentHikeId = Long.valueOf(hikeIdStr);
                for (DisplayedHike displayedHike : mDisplayedHikes) {
                    if (intentHikeId == displayedHike.getId()) {
                        displaySingleHike = true;
                    }
                }
                if (displaySingleHike) {
                    for (DisplayedHike displayedHike : mDisplayedHikes) {
                        if (displayedHike.getId() != intentHikeId) {
                            displayedHike.getPolyline().remove();
                            displayedHike.getStartMarker().remove();
                            displayedHike.getFinishMarker().remove();
                        }
                    }
                    for (HikeData hikeData : mHikesInWindow) {
                        if (hikeData.getHikeId() == intentHikeId) {
                            LatLngBounds newBounds = hikeData.getBoundingBox();
                            int displayHeight = (int) (mScreenHeight * 0.7);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(newBounds, mScreenWidth, displayHeight, 30));
                        }
                    }

                }
            }
        }
    }

    private static class DownloadHikeParams {
        List<HikeData> mHikesFound;
        LatLngBounds mOldBounds;
        boolean mFirstHike;

        DownloadHikeParams(List<HikeData> hikesFound, LatLngBounds oldBounds, boolean firstHike) {
            mHikesFound = hikesFound;
            mOldBounds = oldBounds;
            mFirstHike = firstHike;
        }
    }

    private class DownloadHikeList extends AsyncTask<DownloadHikeParams, Void, DownloadHikeParams> {
        @Override
        protected DownloadHikeParams doInBackground(DownloadHikeParams... params) {
            try {
                LatLngBounds oldBounds = params[0].mOldBounds;
                boolean firstHike = params[0].mFirstHike;
                List<HikeData> hikesFound = mDataManager.getHikesInWindow(oldBounds);
                return new DownloadHikeParams(hikesFound, oldBounds, firstHike);
            } catch (DataManagerException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(DownloadHikeParams postExecuteParams) {

            // Fixes bug #114: On error, doInBackground will abort with null
            if (postExecuteParams == null) {
                return;
            }

            List<HikeData> hikesFound = postExecuteParams.mHikesFound;
            LatLngBounds oldBounds = postExecuteParams.mOldBounds;
            boolean firstHike = postExecuteParams.mFirstHike;

            if (hikesFound != null) {
                if (hikesFound.size() > 0) {
                    displayMap(hikesFound, oldBounds, firstHike);
                } else {
                    LatLngBounds newBounds = guessNewLatLng(oldBounds.southwest, oldBounds.northeast, 0.5);
                    new DownloadHikeList().execute(new DownloadHikeParams(hikesFound, newBounds, firstHike));
                }
            }
        }
    }

    private void displayMap(List<HikeData> hikesFound, LatLngBounds bounds, boolean firstHike) {

        mHikesInWindow = hikesFound;
        LatLngBounds.Builder boundingBoxBuilder = new LatLngBounds.Builder();

        for (int i = 0; i < mHikesInWindow.size(); i++) {
            HikeData hike = mHikesInWindow.get(i);

            displayMarkers(hike);
            displayAnnotations(hike);
            displayHike(hike);

            Polyline polyline = displayHike(hike);
            Pair<Marker, Marker> markers = displayMarkers(hike);
            mDisplayedHikes.add(new DisplayedHike(hike.getHikeId(), polyline, markers.first, markers.second));
            boundingBoxBuilder.include(hike.getStartLocation());
            boundingBoxBuilder.include(hike.getFinishLocation());
        }

        if (firstHike) {
            boundingBoxBuilder.include(mUserLocation);
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundingBoxBuilder.build(), mScreenWidth, mScreenHeight, 30));
        }
    }

    private void displayAnnotations(final HikeData hike) {

        if (hike.getAnnotations() != null) {
            List<MarkerOptions> annotations = new ArrayList<>();
            for (Annotation annotation : hike.getAnnotations()) {
                if (annotation.getAnnotation() != null) {
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(annotation.getRawHikePoint().getPosition())
                            .title("Annotation")
                            .snippet(annotation.getAnnotation())
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_annotate_hike));
                    annotations.add(markerOptions);
                    mMap.addMarker(markerOptions);
                }
            }
        }
    }


    private Pair<Marker, Marker> displayMarkers(final HikeData hike) {

        MarkerOptions startMarkerOptions = new MarkerOptions()
                .position(hike.getStartLocation())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_start_hike));
        MarkerOptions finishMarkerOptions = new MarkerOptions()
                .position(hike.getFinishLocation())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_finish_hike));

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker marker) {
                for (DisplayedHike displayedHike : mDisplayedHikes) {
                    if (marker.equals(displayedHike.getStartMarker()) || marker.equals(displayedHike.getFinishMarker())) {

                        changePolyColor(displayedHike.getPolyline(), 1);
                        changePolyColor(mPrevPolyRef, 0);
                        mPrevPolyRef = displayedHike.getPolyline();
                        Log.d(LOG_FLAG, "Setting color of hike to selected");
                    }
                }
                return onMarkerClickHelper(marker);
            }
        });

        Marker startMarker = mMap.addMarker(startMarkerOptions);
        Marker finishMarker = mMap.addMarker(finishMarkerOptions);

        return Pair.create(startMarker, finishMarker);
    }

    private void changePolyColor(Polyline polyline, int mode) {
        int color = (mode == 0) ? HIKE_LINE_COLOR : HIKE_LINE_COLOR_SELECTED;
        if (polyline != null) {
            //TODO change polyline color
        }
    }

    private DisplayedHike getDisplayedHike(Polyline polyline) {
        for (DisplayedHike hike : mDisplayedHikes) {
            if (hike.getPolyline().equals(polyline)) {
                return hike;
            }
        }
        return null;
    }

    private boolean onMarkerClickHelper(Marker marker) {
        marker.showInfoWindow();
        for (DisplayedHike displayedHike : mDisplayedHikes) {
            if (marker.equals(displayedHike.getStartMarker())
                    || marker.equals(displayedHike.getFinishMarker())) {
                long hikeId = displayedHike.getId();
                try {
                    new DisplayHikeInfo().execute(mDataManager.getHike(hikeId));
                } catch (DataManagerException e) {
                    e.printStackTrace();
                }
                return true;
            }
        }
        return true;
    }

    private Polyline displayHike(final HikeData hike) {
        PolylineOptions polylineOptions = new PolylineOptions();
        List<HikePoint> databaseHikePoints = hike.getHikePoints();
        for (HikePoint hikePoint : databaseHikePoints) {
            polylineOptions.add(hikePoint.getPosition())
                    .width(5)
                    .color(HIKE_LINE_COLOR);
        }
        return mMap.addPolyline(polylineOptions);
    }

    private void onMapClickHelper(LatLng point) {
        if (mHikesInWindow != null) {
            for (int i = 0; i < mHikesInWindow.size(); i++) {
                HikeData hike = mHikesInWindow.get(i);
                double shortestDistance = 100;
                List<HikePoint> hikePoints = hike.getHikePoints();

                for (HikePoint hikePoint : hikePoints) {

                    float[] distanceBetween = new float[1];
                    //Computes the approximate distance (in meters) between polyLinePoint and point.
                    //Returns the result as the first element of the float array distanceBetween
                    distanceBetween(hikePoint.getPosition().latitude, hikePoint.getPosition().longitude,
                            point.latitude, point.longitude, distanceBetween);
                    double distance = distanceBetween[0];

                    if (distance < shortestDistance) {
                        new DisplayHikeInfo().execute(hike);
                        return;
                    }
                }
                BottomInfoView.getInstance().hide(BOTTOM_TABLE_ACCESS_ID);
            }
        }
    }

    private class GetOwnerFromHike extends AsyncTask<HikeData, Void, UserData> {
        @Override
        protected UserData doInBackground(HikeData... hikes) {
            try {
                return DataManager.getInstance().getUserData(hikes[0].getOwnerId());
            } catch (DataManagerException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private class DisplayHikeInfo extends AsyncTask<HikeData, Void, UserData> {

        HikeData hike = null;

        @Override
        protected UserData doInBackground(HikeData... hikes){
            hike = hikes[0];
            try {
                return DataManager.getInstance().getUserData(hikes[0].getOwnerId());
            } catch (DataManagerException e) {
                Log.d(LOG_FLAG, "Could not display hike information");
            }
            return null;
        }

        @Override
        protected void onPostExecute(UserData userData) {
            changePolyColor(mPrevPolyRef, 0);
            if (userData != null) {
                mBottomTable.setTitle(BOTTOM_TABLE_ACCESS_ID, hike.getTitle());
                mBottomTable.clearInfoLines(BOTTOM_TABLE_ACCESS_ID);
                mBottomTable.addInfoLine(BOTTOM_TABLE_ACCESS_ID, getResources().getString(R.string.hikeOwnerText, userData.getUserName()));
                mBottomTable.addInfoLine(BOTTOM_TABLE_ACCESS_ID, getResources().getString(R.string.hikeDistanceText, (long) hike.getDistance() / 1000));
                mBottomTable.setOnClickListener(BOTTOM_TABLE_ACCESS_ID, new View.OnClickListener() {
                    public void onClick(View view) {
                        Intent intent = new Intent(view.getContext(), HikeInfoActivity.class);
                        intent.putExtra(EXTRA_HIKE_ID, Long.toString(hike.getHikeId()));
                        startActivity(intent);
                    }
                });
                mBottomTable.show(BOTTOM_TABLE_ACCESS_ID);
            }
        }
    }

    private void createTrackingToggleButton() {
        Button toggleButton = new Button(this);
        toggleButton.setBackgroundResource((mGps.tracking()) ? R.drawable.button_stop_tracking : R.drawable.button_start_tracking);
        toggleButton.setId(R.id.button_tracking_toggle);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapLayout);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        setupButtonSize(lp);

        toggleButton.setLayoutParams(lp);
        layout.addView(toggleButton, lp);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mGps.enabled()) {
                    mGps.toggleTracking();
                    mGps.setAnnotations(mListAnnotations);
                    mListAnnotations = null;
                    updateButtonDisplay();
                    if (mGps.tracking() && !mGps.paused()) {
                        startHikeDisplay();
                        Log.d(LOG_FLAG, "Starting hike display");
                    }
                } else {
                    displayToast(getResources().getString(R.string.gps_location_not_enabled));

                }
            }
        });
    }

    public void updateButtonDisplay() {

        //Start/stop button update
        Button toggleButton = (Button) findViewById(R.id.button_tracking_toggle);
        Button pauseButton = (Button) findViewById(R.id.button_tracking_pause);
        Button addAnnotationButton = (Button) findViewById(R.id.button_annotation_create);
        if (mGps.tracking()) {
            toggleButton.setBackgroundResource(R.drawable.button_stop_tracking);
            pauseButton.setVisibility(View.VISIBLE);
            pauseButton.setBackgroundResource((mGps.paused()) ? R.drawable.button_resume_tracking : R.drawable.button_pause_tracking);
            addAnnotationButton.setVisibility(View.VISIBLE);
        } else {
            toggleButton.setBackgroundResource(R.drawable.button_start_tracking);
            pauseButton.setVisibility(View.INVISIBLE);
            addAnnotationButton.setVisibility(View.INVISIBLE);
        }
    }

    private void createPauseTrackingButton() {
        Button pauseButton = new Button(this);
        pauseButton.setBackgroundResource(R.drawable.button_pause_tracking);
        pauseButton.setId(R.id.button_tracking_pause);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapLayout);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.LEFT_OF, R.id.button_tracking_toggle);
        setupButtonSize(lp);

        pauseButton.setLayoutParams(lp);
        layout.addView(pauseButton, lp);

        pauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mGps.togglePause();
                updateButtonDisplay();
            }
        });
        pauseButton.setVisibility(View.INVISIBLE);
    }

    private void setupButtonSize(RelativeLayout.LayoutParams lp) {
        lp.width = GUIProperties.DEFAULT_BUTTON_SIZE;
        lp.height = GUIProperties.DEFAULT_BUTTON_SIZE;
        lp.setMargins(GUIProperties.DEFAULT_BUTTON_MARGIN, GUIProperties.DEFAULT_BUTTON_MARGIN, GUIProperties.DEFAULT_BUTTON_MARGIN, GUIProperties.DEFAULT_BUTTON_MARGIN);
    }

    private void createAnnotationButton() {
        final Button annotationButton = new Button(this);
        annotationButton.setText(R.string.button_create_annotation);
        annotationButton.setId(R.id.button_annotation_create);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapLayout);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL, R.id.button_annotation_create);
        annotationButton.setVisibility(View.INVISIBLE);

        annotationButton.setLayoutParams(lp);
        layout.addView(annotationButton, lp);
        annotationButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mGps.tracking()) {
                    displayAddAnnotationPrompt();
                }
            }
        });
    }



    private void displayAddAnnotationPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(this.getResources().getString(R.string.prompt_add_tile));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        //setup the horizontal separator
        View lnSeparator = new View(this);
        lnSeparator.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 5));
        lnSeparator.setBackgroundColor(Color.parseColor("#B3B3B3"));
        layout.addView(lnSeparator);


        //setup the hike comment input field
        final EditText annotationEditText = new EditText(this);
        annotationEditText.setHint(this.getResources().getString(R.string.prompt_annotation_hint));
        annotationEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        annotationEditText.setSingleLine(false);
        layout.addView(annotationEditText);

        builder.setView(layout);

        builder.setPositiveButton(this.getResources().getString(R.string.add_annotation), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String annotation = annotationEditText.getText().toString();
                addAnnotation(annotation);

            }
        });
        builder.setNegativeButton(this.getResources().getString(R.string.button_cancel_save), null);
        builder.setNegativeButton(this.getResources().getString(R.string.add_picture), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(OPEN_CAMERA);
                startActivityForResult(intent, 0);
            }
        });
        builder.show();
    }


    private void addAnnotation(String annotation) {
        RawHikePoint rawHikePoint = GPSPathConverter.getHikePointsFromGeoCoords(mGps.getCurrentCoords());
        if (mListAnnotations != null && mListAnnotations.size() > 0) {
            if (mListAnnotations.get(mListAnnotations.size() - 1).getRawHikePoint().getPosition().equals(rawHikePoint.getPosition())) {
                mListAnnotations.get(mListAnnotations.size() - 1).setText(annotation);
            }
        } else {
            mListAnnotations = new ArrayList<>();
            mListAnnotations.add(new Annotation(rawHikePoint, annotation, null));
        }
        Log.d(LOG_FLAG, "Text annotation added to the list" + mListAnnotations.get(0).getAnnotation());
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0 && resultCode == RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            mImageView = new ImageView(this);
            mImageView.setImageBitmap(photo);
            addPicture(mImageView.getDrawable());
        }
    }

    private void addPicture(Drawable drawable) {
        RawHikePoint rawHikePoint = GPSPathConverter.getHikePointsFromGeoCoords(mGps.getCurrentCoords());
        if (mListAnnotations != null && mListAnnotations.size() > 0) {
            if (mListAnnotations.get(mListAnnotations.size() - 1).getRawHikePoint().getPosition().equals(rawHikePoint.getPosition())) {
                mListAnnotations.get(mListAnnotations.size() - 1).setPicture(drawable);
            }
        } else {
            mListAnnotations = new ArrayList<>();
            mListAnnotations.add(new Annotation(rawHikePoint, null, drawable));
        }
        Log.d(LOG_FLAG, "Picture annotation added to the list" + drawable.toString());
    }

    private void createBottomInfoView() {
        mBottomTable.initialize(this);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapLayout);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layout.addView(mBottomTable.getView(), lp);
    }

    private void onCameraChangeHelper() {
        LatLngBounds currentBounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        List<HikeData> hikesFound = new ArrayList<>();
        boolean firstHike = false;
        new DownloadHikeList().execute(new DownloadHikeParams(hikesFound, currentBounds, firstHike));
    }

    private LatLngBounds guessNewLatLng(LatLng southWest, LatLng northEast, double delta) {
        LatLng guessSW = new LatLng(southWest.latitude - delta, southWest.longitude - delta);
        LatLng guessNE = new LatLng(northEast.latitude + delta, northEast.longitude + delta);
        return new LatLngBounds(guessSW, guessNE);
    }

    private void setGoToHikesButtonListener() {
        Button goHikeButton = (Button) findViewById(R.id.go_hikes_button);
        goHikeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LatLngBounds bounds = getBounds();
                Bundle bound = new Bundle();
                bound.putParcelable("sw", bounds.southwest);
                bound.putParcelable("ne", bounds.northeast);
                Intent intent = new Intent(v.getContext(), HikeListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra(EXTRA_BOUNDS, bound);
                startActivity(intent);
            }
        });
    }

    private void setUpSearchView() {

        mSearchView = (SearchView) findViewById(R.id.search_map_view);
        mSuggestionListView = (ListView) findViewById(R.id.search_suggestions_list);
        mSuggestionListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                mSearchView.onActionViewCollapsed();
                mSuggestionListView.setVisibility(View.GONE);

                // move the camera to the location corresponding to clicked item
                if (mSuggestionList.size() != 0) {
                    Address clickedLocation = mSuggestionList.get(position);
                    LatLng latLng = new LatLng(clickedLocation.getLatitude(), clickedLocation.getLongitude());

                    // get bounding box
                    Bundle clickedLocationExtras = clickedLocation.getExtras();
                    Object bounds = null;
                    if (clickedLocationExtras != null) {
                        bounds = clickedLocationExtras.get(EXTRA_BOUNDS);
                    }
                    if (bounds != null && bounds instanceof LatLngBounds) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds((LatLngBounds) bounds, 60));
                    } else {
                        focusOnLatLng(latLng, 10);
                    }

                    // load hikes at new location
                    onCameraChangeHelper();
                }
            }
        });

        mSuggestionAdapter = new SuggestionAdapter(this, mSuggestionList);
        mSuggestionListView.setAdapter(mSuggestionAdapter);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                new HikeSearcher().execute(new SearchHikeParams(query, true));
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                new HikeSearcher().execute(new SearchHikeParams(newText, false));
                return false;
            }
        });
    }

    private void displayToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private class HikeSearcher extends AsyncTask<SearchHikeParams, Void, Boolean> {

        /**
         * Searches for a locations from a query
         *
         * @param params - Query & boolean indicating whether the user is done typing
         * @return boolean informing postexecute to either hide or show the suggestions
         */
        @Override
        protected Boolean doInBackground(SearchHikeParams... params) {

            String query = params[0].mQuery;
            boolean isDoneTyping = params[0].mIsDoneTyping;

            if (query.length() <= MIN_QUERY_LENGTH_FOR_SUGGESTIONS && !isDoneTyping) {
                return false;
            }

            List<Address> suggestions = new ArrayList<>();

            List<HikeData> hikeDataList = new ArrayList<>();
            try {
                hikeDataList = mDataManager.searchHike(query);
            } catch (DataManagerException e) {
                Log.d(LOG_FLAG, e.getMessage());
            }
            // check if local results and add to suggestions
            for (HikeData hikeData : hikeDataList) {

                Address address = new Address(Locale.ROOT);

                address.setFeatureName(hikeData.getTitle());
                LatLng latLng = hikeData.getHikeLocation();
                address.setLatitude(latLng.latitude);
                address.setLongitude(latLng.longitude);

                Bundle boundingBox = new Bundle();
                boundingBox.putParcelable(EXTRA_BOUNDS, hikeData.getBoundingBox());
                address.setExtras(boundingBox);

                suggestions.add(address);
            }

            try {
                List<Address> locationAddressList = mGeocoder.getFromLocationName(query, MAX_SEARCH_SUGGESTIONS);
                for (Address address : locationAddressList) {
                    suggestions.add(address);
                }
                if (isDoneTyping && suggestions.size() == 0) {
                    Address address = new Address(Locale.ROOT);
                    address.setFeatureName(getResources().getString(R.string.search_no_results));
                    suggestions.add(address);
                }
            } catch (IOException e) {
                Address address = new Address(Locale.ROOT);
                address.setFeatureName(getResources().getString(R.string.search_error));
                suggestions.add(address);
            }

            mSuggestionList.clear();
            mSuggestionList.addAll(suggestions);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean listIsVisible) {
            if (listIsVisible) {
                mSuggestionListView.setVisibility(View.VISIBLE);
            } else {
                mSuggestionListView.setVisibility(View.GONE);
            }
            mSuggestionAdapter.notifyDataSetChanged();
        }
    }

    private LatLng getUserPosition() {
        if (mGps.enabled()) {
            GeoCoords userGeoCoords = mGps.getCurrentCoords();
            return userGeoCoords.toLatLng();
        } else {
            double epflLatitude = 46.519244;
            double epflLongitude = 6.569287;
            return new LatLng(epflLatitude, epflLongitude);
        }
    }

    private void focusOnLatLng(LatLng latLng) {
        if (latLng != null) {
            CameraUpdate target = CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM);
            mMap.animateCamera(target);
        }
    }

    private void focusOnLatLng(LatLng latLng, int zoom) {
        if (latLng != null) {
            CameraUpdate target = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            mMap.animateCamera(target);
        }
    }

    public void startFollowingUser() {
        mFollowingUser = true;
    }

    private void startHikeDisplay() {
        PolylineOptions mCurHike = new PolylineOptions();
        mPolyRef = mMap.addPolyline(mCurHike);
    }

    public void stopHikeDisplay() {
        mPolyRef.remove();
        onCameraChangeHelper();
    }


    private class SuggestionAdapter extends ArrayAdapter<Address> {

        private List<Address> mAddressList;

        public SuggestionAdapter(Context context, List<Address> addressList) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, addressList);
            mAddressList = addressList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView upperText = (TextView) view.findViewById(android.R.id.text1);
            TextView lowerText = (TextView) view.findViewById(android.R.id.text2);

            upperText.setText(mAddressList.get(position).getFeatureName());
            if (mAddressList.get(position).getCountryName() != null) {
                lowerText.setText(mAddressList.get(position).getCountryName());
            } else {
                lowerText.setText("");
            }
            return view;
        }
    }

    private class SearchHikeParams {

        String mQuery;
        boolean mIsDoneTyping;

        SearchHikeParams(String query, boolean isDoneTyping) {
            mQuery = query;
            mIsDoneTyping = isDoneTyping;
        }
    }

}
