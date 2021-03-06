package ch.epfl.sweng.team7.hikingapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Polyline;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import ch.epfl.sweng.team7.authentication.SignedInUser;
import ch.epfl.sweng.team7.database.Annotation;
import ch.epfl.sweng.team7.database.DataManager;
import ch.epfl.sweng.team7.database.DataManagerException;
import ch.epfl.sweng.team7.database.DefaultHikeComment;
import ch.epfl.sweng.team7.database.HikeComment;
import ch.epfl.sweng.team7.database.HikeData;
import ch.epfl.sweng.team7.database.HikePoint;
import ch.epfl.sweng.team7.database.UserData;
import ch.epfl.sweng.team7.network.RawHikeComment;


/**
 * Class which controls and updates the visual part of the view, not the interaction
 */
public class HikeInfoView {
    private final DataManager dataManager = DataManager.getInstance();

    private final static String LOG_FLAG = "Activity_HikeInfoView";

    private long hikeId;
    private long userId;
    private long hikeOwnerId;
    private TextView hikeOwner;
    private TextView hikeName;
    private TextView hikeDistance;
    private RatingBar hikeRatingBar;
    private LinearLayout imgLayout;
    private TextView hikeElevation;
    private View view;
    private Context context;
    private ArrayList<ImageView> galleryImageViews; // make ImageViews accessible in controller.
    private Button backButton;
    private GoogleMap mapPreview;
    private GraphView hikeGraph;
    private ListView navDrawerList;
    private LinearLayout commentList;
    private HikeComment newComment;
    private Button exportButton;
    private HikeData displayedHike;
    private View overlayView;
    private LinearLayout root;
    private ArrayList<Drawable> hikePictureList;


    public HikeInfoView (final View view, final Activity activity, long id, GoogleMap mapHikeInfo) {  // add model as argument when creating that
        hikeId = id;
        userId = SignedInUser.getInstance().getId();

        // initializing UI element in the layout for the HikeInfoView.
        this.context = activity.getApplicationContext();

        hikeOwner = (TextView) view.findViewById(R.id.hikeinfo_owner);
        hikeOwner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(context, UserDataActivity.class);
                i.putExtra(UserDataActivity.EXTRA_USER_ID, hikeOwnerId);
                i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                v.getContext().startActivity(i);
            }
        });

        hikeName = (TextView) view.findViewById(R.id.hikeinfo_name);

        hikeDistance = (TextView) view.findViewById(R.id.hikeinfo_distance);

        hikeRatingBar = (RatingBar) view.findViewById(R.id.hikeinfo_ratingbar);

        hikeElevation = (TextView) view.findViewById(R.id.hikeinfo_elevation_max_min);

        // Image Gallery
        imgLayout = (LinearLayout) view.findViewById(R.id.image_layout);

        mapPreview = mapHikeInfo;

        hikeGraph = (GraphView) view.findViewById(R.id.hike_graph);

        exportButton = (Button) view.findViewById(R.id.button_export_hike);

        navDrawerList = (ListView) view.findViewById(R.id.nav_drawer);
        // Add adapter and onclickmethods to the nav drawer listview
        NavigationDrawerListFactory navDrawerListFactory = new NavigationDrawerListFactory(
                navDrawerList, activity, activity);

        galleryImageViews = new ArrayList<>();
        /* ABOVE IS A HACK, IMAGES ARE NOT STORED IN THE SERVER YET; RIGHT NOW ACCESS TO
        imageViews.size() IS IN HIKEINFOACTIVITY BUT WE IT'S ASYNC SO WE HAVE AN ERROR:
        EITHER WE STORE NUMBER OF IMAGES IN THE SERVER SO WE CAN CREATE A LIST HERE OR
        ACCESS SIZE ONLY IN ASYNC CALL AND ADD LISTENER
         */

        this.view = view;

        exportButton = (Button) view.findViewById(R.id.button_export_hike);

        Button commentButton = (Button) view.findViewById(R.id.done_edit_comment);
        commentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText commentEditText = (EditText) view.findViewById(R.id.comment_text);
                String commentText = commentEditText.getText().toString();
                if (!commentText.isEmpty()) {
                    RawHikeComment rawHikeComment = new RawHikeComment(
                            RawHikeComment.COMMENT_ID_UNKNOWN,
                            hikeId, userId, commentText);
                    newComment = new DefaultHikeComment(rawHikeComment);
                    new PostCommentAsync().execute(rawHikeComment);
                    commentEditText.setText("");
                    new GetUserName().execute(userId);
                } else {
                    new AlertDialog.Builder(v.getContext())
                            .setMessage(R.string.type_comment);
                }
            }
        });

        commentList = (LinearLayout) view.findViewById(R.id.comments_list);

        root = (LinearLayout) view.findViewById(R.id.hike_info_root_layout);

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlayView = layoutInflater.inflate(R.layout.hike_info_fullscreen, null);
        backButton = (Button) overlayView.findViewById(R.id.back_button_fullscreen_image);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                root.removeView(overlayView);
            }
        });

        new GetOneHikeAsync().execute(hikeId);

    }

    private class GetOneHikeAsync extends AsyncTask<Long, Void, HikeData> {

        @Override
        protected HikeData doInBackground(Long... hikeIds) {
            try {
                return dataManager.getHike(hikeIds[0]);
            } catch (DataManagerException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(HikeData result) {
            if (result == null) {
                setErrorState();
                return;
            }
            displayHike(result);
            displayedHike = result;
        }

        private void setErrorState() {
            String name = "Hike Data Not Found";
            hikeName.setText(name);
        }

        private void displayHike(HikeData hikeData) {
            final int ELEVATION_POINT_COUNT = 100;

            if (mapPreview != null) {
                List<HikeData> hikesToDisplay = Arrays.asList(hikeData);
                List<Polyline> displayedHikes = MapDisplay.displayHikes(hikesToDisplay, mapPreview);
                MapDisplay.displayMarkers(hikesToDisplay, mapPreview);
                MapDisplay.displayAnnotations(hikesToDisplay, mapPreview);
                MapDisplay.setOnMapClick(false, displayedHikes, mapPreview);

                DisplayMetrics display = context.getResources().getDisplayMetrics();
                int screenHeight = display.heightPixels;
                int screenWidth = display.widthPixels;
                MapDisplay.setCamera(hikesToDisplay, mapPreview, screenWidth, screenHeight / 3);
            }

            double distance = hikeData.getDistance() / 1000;  // in km
            float rating = (float) hikeData.getRating().getDisplayRating();
            Double elevationMin = hikeData.getMinElevation();
            Double elevationMax = hikeData.getMaxElevation();

            Integer elevationMinInteger = elevationMin.intValue();
            Integer elevationMaxInteger = elevationMax.intValue();

            List<HikePoint> hikePoints = hikeData.getHikePoints();
            LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
            double lastElapsedTimeInHours = 0;
            for (int i = 0; i < ELEVATION_POINT_COUNT; ++i) {
                HikePoint hikePoint = hikePoints.get((i * hikePoints.size()) / ELEVATION_POINT_COUNT);

                final double elapsedTimeInMilliseconds = (hikePoint.getTime().getTime()
                        - hikePoints.get(0).getTime().getTime());
                final double elapsedTimeInHours = elapsedTimeInMilliseconds / (1000 * 60 * 60);

                // Check that data is in ascending order
                if (i > 0 && elapsedTimeInHours <= lastElapsedTimeInHours) {
                    continue;
                }
                lastElapsedTimeInHours = elapsedTimeInHours;

                series.appendData(
                        new DataPoint(elapsedTimeInHours, hikePoint.getElevation()),
                        false, // scrollToEnd
                        hikePoints.size());
            }

            hikeGraph.removeAllSeries(); // remove placeholder series
            hikeGraph.setTitle("Elevation");
            hikeGraph.getGridLabelRenderer().setHorizontalAxisTitle("Hours");

            hikeGraph.addSeries(series);

            hikeName.setText(hikeData.getTitle());

            NumberFormat numberFormat = NumberFormat.getInstance();
            numberFormat.setMaximumFractionDigits(1);
            String distanceString = numberFormat.format(distance) + " km";
            hikeDistance.setText(distanceString);

            hikeRatingBar.setRating(rating);

            String elevationString = String.format(context.getResources().getString(R.string.elevation_min_max), elevationMinInteger, elevationMaxInteger);
            hikeElevation.setText(elevationString);
            hikeOwnerId = hikeData.getOwnerId();
            new ShowOwnerName().execute(hikeOwnerId);

            List<HikeComment> comments = hikeData.getAllComments();
            commentList.removeAllViews();
            Comparator<HikeComment> comparator = new Comparator<HikeComment>() {
                public int compare(HikeComment c1, HikeComment c2) {
                    return c1.getCommentDate().compareTo(c2.getCommentDate());
                }
            };
            Collections.sort(comments, comparator);
            for (HikeComment comment : comments) {
                showNewComment(comment, "");
            }

            hikePictureList = new ArrayList<>();
            for(Annotation annotation: hikeData.getAnnotations()) {
                hikePictureList.add(annotation.getPicture());
            }

            exportButton.setVisibility(View.VISIBLE);
            loadImageScrollView();
        }

        // create imageviews and add them to the scrollview
        private void loadImageScrollView() {

            // STATIC image
            Drawable testImage = view.getContext().getApplicationContext().getDrawable(R.drawable.login_background);
            hikePictureList.add(testImage);

            for(Drawable drawable : hikePictureList) {
                // add imageviews with images to the scrollview
                imgLayout.addView(createImageView(drawable));
            }

        }

        private View createImageView(Drawable img) {

            // creating an ImageView and applying layout parameters
            ImageView imageView = new ImageView(context.getApplicationContext());
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            imageView.setAdjustViewBounds(true); // set this to true to preserve aspect ratio of image.
            layoutParams.setMargins(10, 10, 10, 10); // Margin around each image
            imageView.setLayoutParams(layoutParams);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE); // scaling down image to fit inside view
            imageView.setImageDrawable(img);
            galleryImageViews.add(imageView);

            imageView.setOnClickListener(new ImageViewClickListener());

            return imageView;

        }
    }

    private class PostCommentAsync extends AsyncTask<RawHikeComment, Void, Long> {

        @Override
        protected Long doInBackground(RawHikeComment... rawHikeComments) {
            try {
                return dataManager.postComment(rawHikeComments[0]);
            } catch (DataManagerException e) {
                e.printStackTrace();
                return (long) -1;
            }
        }

        @Override
        protected void onPostExecute(Long id) {
            if (id == -1) Log.d("failure", "post comment unsuccessful");
        }
    }

    public Button getBackButton() {
        return backButton;
    }

    public Button getExportButton() {
        return exportButton;
    }

    public RatingBar getHikeRatingBar() {

        return hikeRatingBar;
    }

    public ArrayList<ImageView> getGalleryImageViews() {
        return galleryImageViews;
    }

    public GoogleMap getMapPreview() {
        return mapPreview;
    }

    public ListView getNavDrawerList() {
        return navDrawerList;
    }

    private void showNewComment(HikeComment comment, String name) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View commentRow = inflater.inflate(R.layout.activity_comment_list_adapter, null);

        TextView commentDate = (TextView) commentRow
                .findViewById(R.id.comment_date);
        Date date;
        if (comment.getCommentDate() == null) { // new comment
            date = new Date();
        } else {
            date = comment.getCommentDate();
        }
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        dateFormat.setTimeZone(TimeZone.getTimeZone("CET"));
        String dateString = dateFormat.format(date);
        commentDate.setText(dateString);

        TextView commentName = (TextView) commentRow
                .findViewById(R.id.comment_username);
        final Long commentOwnerId = comment.getCommentOwnerId();
        if (commentOwnerId == hikeOwnerId) commentName.setTextColor(Color.RED);
        if (comment.getCommentOwnerName() == null) { // new comment
            commentName.setText(name);
        } else {
            commentName.setText(comment.getCommentOwnerName());
        }
        commentName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(v.getContext(), UserDataActivity.class);
                i.putExtra(UserDataActivity.EXTRA_USER_ID, commentOwnerId);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.getContext().startActivity(i);
            }
        });

        TextView commentText = (TextView) commentRow
                .findViewById(R.id.comment_display_text);
        commentText.setText(comment.getCommentText());

        commentList.addView(commentRow);
    }

    private class ShowOwnerName extends AsyncTask<Long, Void, UserData> {

        @Override
        protected UserData doInBackground(Long... userIds) {
            try {
                return dataManager.getUserData(userIds[0]);
            } catch (DataManagerException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(UserData userData) {
            hikeOwner.setText(userData.getUserName());
        }
    }

    private class GetUserName extends AsyncTask<Long, Void, UserData> {

        @Override
        protected UserData doInBackground(Long... userIds) {
            try {
                return dataManager.getUserData(userIds[0]);
            } catch (DataManagerException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(UserData userData) {
            if (userData != null) {
                showNewComment(newComment, userData.getUserName());
            }
        }
    }

    public HikeData getDisplayedHike() {
        return displayedHike;
    }


    public void toggleFullScreen() {
        final View infoView = view.findViewById(R.id.info_overview_layout);

        // Check which view is currently visible and switch
        if (infoView.getVisibility() == View.VISIBLE) {
            root.addView(overlayView, 0);
        } else {
            root.removeView(overlayView);
        }
    }

    public View getOverlayView() {
        return overlayView;
    }

    public LinearLayout getRootLayout() {
        return root;
    }

    private class ImageViewClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // Update image in full screen view
            ImageView imgView = (ImageView) v;
            Drawable drawable = imgView.getDrawable();

            ImageView fullScreenView = (ImageView) view.findViewById(R.id.image_fullscreen);
            fullScreenView.setImageDrawable(drawable);

            toggleFullScreen();
        }
    }

}
