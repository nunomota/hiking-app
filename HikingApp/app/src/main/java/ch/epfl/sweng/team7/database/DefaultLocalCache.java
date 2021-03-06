/*
 * Copyright 2015 EPFL. All rights reserved.
 *
 * Created by simon.schuetz on 04 Nov 2015
 */
package ch.epfl.sweng.team7.database;


import java.util.ArrayList;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


class DefaultLocalCache implements LocalCache {

    private final static String LOG_FLAG = "DB_DefaultLocalCache";
    private final int HIKES_CACHE_MAX_SIZE = 100;//TODO this should be higher
    private final HashMap<Long, HikeData> mHikesCache = new FixedSizeHashMap<>(HIKES_CACHE_MAX_SIZE);
    private final HashMap<Long, UserData> mUsersCache = new FixedSizeHashMap<>(HIKES_CACHE_MAX_SIZE);
    private final HashMap<Long, Drawable> mPicturesCache = new FixedSizeHashMap<>(HIKES_CACHE_MAX_SIZE);

    public boolean hasHike(long hikeId) {
        return mHikesCache.containsKey(hikeId);
    }

    public HikeData getHike(long hikeId) {
        return mHikesCache.get(hikeId);
    }

    public void putHike(HikeData hikeData) {
        if (hikeData != null) {
            mHikesCache.put(hikeData.getHikeId(), hikeData);
        }
    }

    public void removeHike(long hikeId) {
        mHikesCache.remove(hikeId);
    }

    public int cachedHikesCount() {
        return mHikesCache.size();
    }

    public void setUserData(UserData userData) {

        if (userData != null) {
            // keep old hike list and updates the rest
            if (mUsersCache.get(userData.getUserId()) != null) {
                userData.setHikeList(mUsersCache.get(userData.getUserId()).getHikeList());
            }
            mUsersCache.put(userData.getUserId(), userData);
        }
    }

    public UserData getUserData(long userId) {
        return mUsersCache.get(userId);
    }
    
    public Drawable getPicture(long pictureId) { return mPicturesCache.get(pictureId); }

    @Override
    public void putPicture(Drawable picture, long id) {
        if (picture != null) {
            mPicturesCache.put(id, picture);
        }
    }
    




    private class FixedSizeHashMap<K, V> extends LinkedHashMap<K, V> {
        private final int MAX_ENTRIES;

        FixedSizeHashMap(int maxEntries) {
            super(16, 0.75f, true);
            MAX_ENTRIES = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    }

    public List<Long> searchHike(String query) {

        String[] tokens = query.split("\\s+");
        List<Long> hikeIds = new ArrayList<>();
        for (Map.Entry<Long, HikeData> entry : mHikesCache.entrySet()) {
            for(String token : tokens) {
                if (entry.getValue().getTitle().contains(token)) {
                    hikeIds.add(entry.getValue().getHikeId());
                }
            }
        }

        return hikeIds;
    }
}
