/*
 * Copyright 2015 EPFL. All rights reserved.
 *
 * Created by simon.schuetz on 03 Nov 2015
 */
package ch.epfl.sweng.team7.database;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.epfl.sweng.team7.network.RawHikeData;
import ch.epfl.sweng.team7.network.RawHikePoint;

import static android.location.Location.distanceBetween;

public class DefaultHikeData implements HikeData {

    private final static String LOG_FLAG = "DB_DefaultHikeData";

    private final long mHikeId;    // Database hike ID of this hike
    private final long mOwnerId;   // Database user ID of owner
    private final Date mDate;      // A UTC time stamp
    private final List<HikePoint> mHikePoints;
    private final double mDistance;
    private final LatLngBounds mBoundingBox;
    private final LatLng mHikeLocation;
    private final LatLng mStartLocation;
    private final LatLng mFinishLocation;
    private final ElevationBounds mElevationBounds;

    /**
     * A HikeData object is created from a RawHikeData, but calculates much more information
     * that can later be queried by the user interface.
     */
    public DefaultHikeData(RawHikeData rawHikeData) {
        mHikeId = rawHikeData.getHikeId();
        mOwnerId = rawHikeData.getOwnerId();
        mDate = rawHikeData.getDate();

        List<RawHikePoint> rawHikePoints = rawHikeData.getHikePoints();
        mHikePoints = new ArrayList<>();
        for (RawHikePoint rawHikePoint : rawHikePoints){
            mHikePoints.add(new DefaultHikePoint(rawHikePoint));
        }

        mDistance = calculateDistance(rawHikePoints);
        mBoundingBox = calculateBoundingBox(rawHikePoints);
        mHikeLocation = getBoundingBox().getCenter();
        mStartLocation = rawHikePoints.get(0).getPosition();
        mFinishLocation = rawHikePoints.get(rawHikePoints.size() - 1).getPosition();
        mElevationBounds = calculateElevationBounds(rawHikePoints);
    }
    /**
     * @return the hike ID.
     */
    public long getHikeId() {
        return mHikeId;
    }

    /**
     * @return the owner ID.
     */
    public long getOwnerId() {
        return mOwnerId;
    }

    /**
     * @return the date of the hike
     */
    public Date getDate() {
        return mDate;
    }

    /**
     * @return the rating of the hike
     * This feature is not yet implemented in the backend
     */
    public double getRating() {
        return 3;
    }

    /**
     * @return an ordered list of the waypoints on this hike
     */
    public List<HikePoint> getHikePoints() {
        return mHikePoints;
    }

    /**
     * @return the total distance covered by the hike
     */
    public double getDistance() {
        return mDistance;
    }

    /**
     * @return the total elevation gain,
     * which is the sum of all position elevation differences, ignoring all downhill
     * This feature is not yet implemented in the backend
     */
    public double getElevationGain() {
        return mElevationBounds.mElevationGain;
    }

    /**
     * @return the total elevation loss,
     * which is the sum of all negative elevation differences, ignoring all uphill
     * This feature is not yet implemented in the backend
     */
    public double getElevationLoss() {
        return mElevationBounds.mElevationLoss;
    }

    /**
     * * This feature is not yet implemented in the backend
     */
    public double getMaxElevation() {
        return mElevationBounds.mMaxElevation;
    }

    /**
     * * This feature is not yet implemented in the backend
     */
    public double getMinElevation() {
        return mElevationBounds.mMinElevation;
    }

    /**
     * @return a box around the hike.
     * Queried by the backend/interface to see whether hike should be displayed.
     */
    public LatLngBounds getBoundingBox() {
        return mBoundingBox;
    }

    /**
     * @return a representative point for this hike, where the pin will be placed on the map
     */
    public LatLng getHikeLocation() {
        return mHikeLocation;
    }

    public LatLng getStartLocation() {
        return mStartLocation;
    }

    public LatLng getFinishLocation() {
        return mFinishLocation;
    }

    private double calculateDistance(List<RawHikePoint> rawHikePoints) {
        double distance = 0;
        for (int i = 0; i < rawHikePoints.size() - 1; i++){
            LatLng currentLoc = rawHikePoints.get(i).getPosition();
            LatLng nextLoc = rawHikePoints.get(i + 1).getPosition();
            float[] distanceBetween = new float[1];
            distanceBetween(currentLoc.latitude, currentLoc.longitude,
                    nextLoc.latitude, nextLoc.longitude, distanceBetween);
            distance += (double) distanceBetween[0]/1000;
        }
        return distance;
    }

    private LatLngBounds calculateBoundingBox(List<RawHikePoint> rawHikePoints) {
        LatLngBounds.Builder boundingBoxBuilder =  new LatLngBounds.Builder();
        for (RawHikePoint rawHikePoint : rawHikePoints){
            LatLng pos = rawHikePoint.getPosition();
            boundingBoxBuilder.include(pos);
        }
        return boundingBoxBuilder.build();
    }

    private ElevationBounds calculateElevationBounds(List<RawHikePoint> rawHikePoints) {

        ElevationBounds elevationBounds = new ElevationBounds();

        // Initialize elevation
        double lastElevation = rawHikePoints.get(0).getElevation();
        elevationBounds.mMaxElevation = lastElevation;
        elevationBounds.mMinElevation = lastElevation;

        // Traverse hike points in order
        for(int i = 1; i < rawHikePoints.size(); ++i) {
            final double thisElevation = rawHikePoints.get(i).getElevation();
            final double deltaElevation  = thisElevation - lastElevation;

            if(deltaElevation > 0) {
                elevationBounds.mElevationGain += deltaElevation;
            } else {
                // ElevationLoss is always positive, but deltaElevation is negative here
                elevationBounds.mElevationLoss += (-deltaElevation);
            }

            if(thisElevation > elevationBounds.mMaxElevation) {
                elevationBounds.mMaxElevation = thisElevation;
            }

            if(thisElevation < elevationBounds.mMinElevation) {
                elevationBounds.mMinElevation = thisElevation;
            }

            lastElevation = thisElevation;
        }
        return elevationBounds;
    }

    // A simple storage container for elevation-related data.
    // ElevationGain and ElevationLoss are both positive numbers.
    private class ElevationBounds {
        public double mMinElevation;
        public double mMaxElevation;
        public double mElevationGain;
        public double mElevationLoss;
    }

}
