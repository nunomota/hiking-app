/*
 * Copyright 2015 EPFL. All rights reserved.
 *
 * Created by simon.schuetz on 01 Nov 2015
 * based on HikingApp QuizQuestion class
 */

package ch.epfl.sweng.team7.network;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ch.epfl.sweng.team7.authentication.SignedInUser;
import ch.epfl.sweng.team7.database.Annotation;


/**
 * Encapsulates the data of a hike, as represented in the backend server.
 * Additional annotations will be added here rather than in the RawHikePoint class, to simplify
 * serialization and reduce data rate in communication.
 */
public class RawHikeData {

    private final static String LOG_FLAG = "Network_RawHikeData";
    public static final long HIKE_ID_UNKNOWN = -1;

    private long mHikeId;    // Database hike ID of this hike
    private long mOwnerId;   // Database user ID of owner
    private Date mDate;      // A UTC time stamp
    private List<RawHikePoint> mHikePoints;   // Points of the hike, in chronological order
    private List<RawHikeComment> mComments;
    private Rating mRating;
    private String mTitle;
    private List<Annotation> mAnnotations;

    /**
     * Creates a new RawHikeData instance from the data provided as arguments.
     *
     * @param hikeId     the database ID (user id) of the hike, HIKE_ID_UNKNOWN if unknown
     * @param ownerId    the owner ID (user id) of the hike
     * @param date       the time/date when this hike was done
     * @param hikePoints the list of points on this hike, must be >= 1 point
     * @throws IllegalArgumentException
     */


    public RawHikeData(long hikeId, long ownerId, Date date, List<RawHikePoint> hikePoints,
                       List<RawHikeComment> comments, String title, List<Annotation> annotations) {


        // Argument checks
        if (hikeId < 0 && hikeId != HIKE_ID_UNKNOWN) {
            throw new IllegalArgumentException("Hike ID must be positive");
        }
        if (ownerId < 0) {
            throw new IllegalArgumentException("Owner ID must be positive");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date not specified");
        }
        if (hikePoints == null) {
            throw new IllegalArgumentException("HikePoints not specified");
        }
        if (hikePoints.size() < 1) {
            throw new IllegalArgumentException("Hike must contain at least one point");
        }
        //No check on annotations cause a hike it not always have annotations.

        mHikeId = hikeId;
        mOwnerId = ownerId;
        mDate = date;
        mHikePoints = hikePoints;
        if (comments == null) {
            mComments = new ArrayList<>();
        } else {
            mComments = comments;
        }
        mRating = new Rating();
        mTitle = title;
        mAnnotations = annotations;
    }

    /**
     * Returns the hike ID.
     */
    public long getHikeId() {
        return mHikeId;
    }

    /**
     * Returns the owner ID.
     */
    public long getOwnerId() {
        return mOwnerId;
    }

    /**
     * Returns the date.
     */
    public Date getDate() {
        return (Date) mDate.clone();
    }

    /**
     * Returns a list of the hike points.
     */
    public List<RawHikePoint> getHikePoints() {
        return new ArrayList<>(mHikePoints);
    }

    public List<RawHikeComment> getAllComments() {
        return mComments;
    }

    public Rating getRating() {
        return mRating;
    }

    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns the list of annotations
     */
    public ArrayList<Annotation> getAnnotations() {
        if (mAnnotations == null || mAnnotations.size() < 1) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>(mAnnotations);
        }
    }

    /**
     * Sets the Hike ID. This function will usually be called after a hike has been posted
     * and the server has assigned a new hike ID.
     *
     * @param hikeId The new hike ID
     * @throws IllegalArgumentException on negative inputs
     */
    public void setHikeId(long hikeId) throws IllegalArgumentException {
        if (hikeId < 0 && hikeId != HIKE_ID_UNKNOWN) {
            throw new IllegalArgumentException("Hike ID must be positive");
        }
        mHikeId = hikeId;
    }

    public void setRating(Rating rating) {
        mRating = rating;
    }


    public void setAnnotations(List<Annotation> annotations) {
        mAnnotations = annotations;
    }

    /**
     * @return a JSON object representing this hike
     * @throws JSONException
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("hike_id", mHikeId);
        jsonObject.put("owner_id", mOwnerId);
        jsonObject.put("date", mDate.getTime());
        jsonObject.put("hike_data", parseHikePointsList(mHikePoints));
        jsonObject.put("comments", parseCommentsList(mComments));
        jsonObject.put("title", mTitle);
        jsonObject.put("annotations", parseAnnotations(mAnnotations));
        return jsonObject;
    }

    /**
     * @return a JSON array of the input
     * @throws JSONException
     */
    private JSONArray parseHikePointsList(List<RawHikePoint> hikePoints) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < hikePoints.size(); ++i) {
            jsonArray.put(hikePoints.get(i).toJSON());
        }
        return jsonArray;
    }

    /**
     * @return a JSON array of the input comments,
     * or an empty array if comments are null
     */
    private JSONArray parseCommentsList(List<RawHikeComment> comments) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        if(comments != null) {
            for (RawHikeComment comment : comments) {
                jsonArray.put(comment.toJSON());
            }
        }
        return jsonArray;
    }

    /**
     * @return a JSON array of the input annotations,
     * or an empty array if annotations are null
     */
    private JSONArray parseAnnotations(List<Annotation> annotations) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                jsonArray.put(annotation.toJSON());
            }
        }
        return jsonArray;
    }

    /**
     * Creates a new RawHikeData object by parsing a JSON object in the format
     * returned by the server.
     *
     * @param jsonObject a {@link JSONObject} encoding.
     * @return a new RawHikeData object.
     * @throws JSONException in case of malformed JSON.
     */
    public static RawHikeData parseFromJSON(JSONObject jsonObject) throws HikeParseException, JSONException {
        try {
            JSONArray jsonHikePoints = jsonObject.getJSONArray("hike_data");
            List<RawHikePoint> hikePoints = new ArrayList<>();
            for (int i = 0; i < jsonHikePoints.length(); ++i) {
                hikePoints.add(RawHikePoint.parseFromJSON(jsonHikePoints.getJSONArray(i)));
            }

            JSONArray jsonComments = jsonObject.getJSONArray("comments");
            List<RawHikeComment> comments = new ArrayList<>();
            for (int i = 0; i < jsonComments.length(); ++i) {
                comments.add(RawHikeComment.parseFromJSON(jsonComments.getJSONObject(i)));
            }
            Date date = new Date(jsonObject.getLong("date"));
            RawHikeData rawHikeData = new RawHikeData(
                    jsonObject.getLong("hike_id"),
                    jsonObject.getLong("owner_id"),
                    date,
                    hikePoints,
                    comments,
                    jsonObject.getString("title"),
                    null);
            if (jsonObject.has("rating")) {
                rawHikeData.setRating(Rating.parseFromJSON(jsonObject.getJSONObject("rating")));
            }
            if (jsonObject.has("annotations")) {
                JSONArray jsonAnnotations = jsonObject.getJSONArray("annotations");
                List<Annotation> annotations = new ArrayList<>();
                for (int i = 0; i < jsonAnnotations.length(); i++) {
                    annotations.add(Annotation.parseFromJSON(jsonAnnotations.getJSONArray(i)));
                }
                rawHikeData.setAnnotations(annotations);
            }
            return rawHikeData;

        } catch (JSONException e) {
            throw new HikeParseException(e);
        } catch (IllegalArgumentException e) {
            throw new HikeParseException("Invalid hike structure: " + e.getMessage());
        } catch (NullPointerException e) {
            throw new HikeParseException("Invalid hike structure");
        }
    }

    /**
     * Creates a new RawHikeData object by parsing a GPX track from xml file
     */
    public static RawHikeData parseFromGPXDocument(Document doc) throws HikeParseException {

        List<RawHikePoint> hikePoints = new ArrayList<>();
        List<RawHikeComment> comments = new ArrayList<>();
        String title;
        try {
            // Normalization
            doc.getDocumentElement().normalize();

            // Input check
            if (doc.getDocumentElement().getNodeName().compareTo("gpx") != 0) {
                throw new HikeParseException("gpx node not found.");
            }

            // Parse track (trk node with trkseg subnodes)
            Element trk = (Element) doc.getElementsByTagName("trk").item(0);
            title = trk.getElementsByTagName("name").item(0).getTextContent();
            Element trkseg = (Element) trk.getElementsByTagName("trkseg").item(0);
            NodeList trkptList = trkseg.getElementsByTagName("trkpt");

            for (int temp = 0; temp < trkptList.getLength(); temp++) {

                try {
                    Node trkptNode = trkptList.item(temp);

                    if (trkptNode.getNodeType() == Node.ELEMENT_NODE) {

                        Element trackPoint = (Element) trkptNode;
                        double lat = Double.parseDouble(trackPoint.getAttribute("lat"));
                        double lng = Double.parseDouble(trackPoint.getAttribute("lon"));
                        double ele = Double.parseDouble(trackPoint.getElementsByTagName("ele").item(0).getTextContent());
                        String timeString = trackPoint.getElementsByTagName("time").item(0).getTextContent();
                        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
                        Date date = format.parse(timeString);
                        hikePoints.add(new RawHikePoint(new LatLng(lat, lng), date, ele));
                    }
                } catch (Exception e) {
                    // pass
                    Log.e(LOG_FLAG, "parseFromGPXDocument failed: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            // Parsing should be very forgiving and ignore any exception.
            Log.e(LOG_FLAG, e.getMessage());
            throw new HikeParseException(e);
        }
        return new RawHikeData(HIKE_ID_UNKNOWN, SignedInUser.getInstance().getId(), hikePoints.get(0).getTime(), hikePoints, comments, title, null);
    }

    public void setTitle(String newTitle) {
        mTitle = newTitle;
    }


}
