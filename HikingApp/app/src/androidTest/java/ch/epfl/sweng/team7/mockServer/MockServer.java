package ch.epfl.sweng.team7.mockServer;

import android.graphics.drawable.Drawable;

import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.epfl.sweng.team7.authentication.LoginRequest;
import ch.epfl.sweng.team7.authentication.SignedInUser;
import ch.epfl.sweng.team7.network.DatabaseClient;
import ch.epfl.sweng.team7.network.DatabaseClientException;
import ch.epfl.sweng.team7.network.HikeParseException;
import ch.epfl.sweng.team7.network.RatingVote;
import ch.epfl.sweng.team7.network.RawHikeComment;
import ch.epfl.sweng.team7.network.RawHikeData;
import ch.epfl.sweng.team7.network.RawHikePoint;
import ch.epfl.sweng.team7.network.RawUserData;

/**
 * This is a local implementation of the DatabaseClient, which is used in testing
 * to make sure that tests do not depend on a working online server.
 *
 * Created by pablo on 6/11/15.
 */
public class MockServer implements DatabaseClient {
    private static final String PROPER_JSON_ONEHIKE = "{\n"
            + "  \"hike_id\": 1,\n"
            + "  \"owner_id\": 153,\n"
            + "  \"date\": 123201,\n"
            + "  \"hike_data\": [\n"
            + "    [0.0, 0.0, 123201, 1.0],\n"
            + "    [0.1, 0.1, 123202, 2.0],\n"
            + "    [0.2, 0.0, 123203, 1.1],\n"
            + "    [0.3,89.9, 123204, 1.2],\n"
            + "    [0.4, 0.0, 123205, 2.0]\n"
            + "  ],\n"
            + "  \"comments\": [\n"
            + "  ],\n"
            + "  \"title\": \"test hike title\"\n"
            + "}\n";
    //Same as DefaultLocalCache
    private final int HIKES_CACHE_MAX_SIZE = 100;
    private final HashMap<Long, RawHikeData> mHikeDataBase = new FixedSizeHashMap<>(HIKES_CACHE_MAX_SIZE);
    private int mAssignedHikeID = 10;
    private final HashMap<Long, RawHikeComment> mHikeCommentDataBase = new FixedSizeHashMap<>(HIKES_CACHE_MAX_SIZE);
    private int mAssignedCommentID = 10;
    private int mAssignedUserId = 10;

    private List<RawUserData> mUsers;


    public MockServer() throws DatabaseClientException {
        createMockHikeOne();
        mUsers = new ArrayList<>();
        mUsers.add(new RawUserData(12345, "Bort", "bort@googlemail.com", -1));
    }

    /**
     * Method to fetch a single RawHikeData with the given hikeID
     *
     * @param hikeId The numeric ID of one hike in the database
     * @throws DatabaseClientException
     */
    @Override
    public RawHikeData fetchSingleHike(long hikeId) throws DatabaseClientException {
        //Hike 1 should always exists
        if (hasHike(hikeId)) {
            return getHike(hikeId);
        } else {
            throw new DatabaseClientException("No hike on the server with ID " + hikeId);
        }
    }

    /**
     * Return a list of of RawHikeData with the given hikeIds
     *
     * @param hikeIds The numeric IDs of multiple hikes in the database
     * @throws DatabaseClientException
     */
    @Override
    public List<RawHikeData> fetchMultipleHikes(List<Long> hikeIds) throws DatabaseClientException {
        List<RawHikeData> listRawHikeData = new ArrayList<>();
        for (Long hikeId : hikeIds) {
            listRawHikeData.add(fetchSingleHike(hikeId));
        }
        return listRawHikeData;
    }

    /**
     * Return the hikeIds of hikes that are in the given window
     *
     * @param bounds Boundaries (window) of the
     * @throws DatabaseClientException
     */
    @Override
    public List<Long> getHikeIdsInWindow(LatLngBounds bounds) throws DatabaseClientException {
        List<Long> hikeIdsInWindow = new ArrayList<>();
        for (RawHikeData rawHikeData : mHikeDataBase.values()) {
            for (RawHikePoint rawHikePoint : rawHikeData.getHikePoints()) {
                if (bounds.contains(rawHikePoint.getPosition())) {
                    hikeIdsInWindow.add(rawHikeData.getHikeId());
                    break;
                }
            }
        }
        return hikeIdsInWindow;
    }

    /**
     * Get all hikes of a user
     *
     * @param userId A valid user ID
     * @return A list of hike IDs
     * @throws DatabaseClientException in case the data could not be
     *                                 retrieved for any reason external to the application (network failure, etc.)
     */
    public List<Long> getHikeIdsOfUser(long userId) throws DatabaseClientException {
        List<Long> hikeIdsInWindow = new ArrayList<>();
        for (RawHikeData rawHikeData : mHikeDataBase.values()) {
            if(rawHikeData.getOwnerId() == userId) {
                hikeIdsInWindow.add(rawHikeData.getHikeId());
            }
        }
        return hikeIdsInWindow;
    }

    /**
     * Get all hikes with given keywords
     *
     * @param keywords A string of keywords, separated by spaces. Special characters will be ignored.
     * @return A list of hike IDs
     * @throws DatabaseClientException in case the data could not be
     *                                 retrieved for any reason external to the application (network failure, etc.)
     */
    public List<Long> getHikeIdsWithKeywords(String keywords) throws DatabaseClientException {
        String[] tokens = keywords.split("\\s+");
        List<Long> hikeIds = new ArrayList<>();
        for (RawHikeData rawHikeData : mHikeDataBase.values()) {
            for(String token : tokens) {
                if (rawHikeData.getTitle().contains(token)) {
                    hikeIds.add(rawHikeData.getHikeId());
                }
            }
        }
        return hikeIds;
    }

    /**
     * Method to post a hike in the database. The database assigns a hike ID and returns that.
     *
     * @param hike to post. ID is ignored, because hike will be assigned a new ID.
     * @throws DatabaseClientException
     */
    @Override
    public long postHike(RawHikeData hike) throws DatabaseClientException {
        long hikeId = hike.getHikeId();
        if (hikeId > 0) {
            if (!hasHike(hikeId)) {
                throw new DatabaseClientException("Setting Hike that's not there.");
            }
        } else {
            hikeId = mAssignedHikeID;
            mAssignedHikeID++;
            hike.setHikeId(hikeId);
        }
        putHike(hike);
        return hikeId;
    }

    /**
     * Delete a hike from the server. A hike can only be deleted by its owner.
     *
     * @param hikeId - ID of the hike
     * @throws DatabaseClientException if unable to delete user
     */
    public void deleteHike(long hikeId) throws DatabaseClientException {
        mHikeDataBase.remove(hikeId);
    }

    /**
     * Post user data to the data base
     *
     * @param rawUserData object containing id, user name and mail address
     * @return user id
     * @throws DatabaseClientException if post is unsuccessful
     */
    @Override
    public long postUserData(RawUserData rawUserData) throws DatabaseClientException {
        // Positive user ID means the user is in the database
        if (rawUserData.getUserId() > 0) {
            for (int i = 0; i < mUsers.size(); ++i) {
                if (mUsers.get(i).getUserId() == rawUserData.getUserId()) {
                    mUsers.set(i, rawUserData);
                    return rawUserData.getUserId();
                }
            }
            throw new DatabaseClientException("User to update not found in MockServer.");
        } else {
            long newUserId = mAssignedUserId;
            mAssignedUserId++;
            rawUserData.setUserId(newUserId);
            mUsers.add(rawUserData);
            return newUserId;
        }
    }

    /**
     * Fetch data for a user from the server
     *
     * @param userId - id of the user
     * @return RawUserData
     * @throws DatabaseClientException if unable to fetch user data
     */
    @Override
    public RawUserData fetchUserData(long userId) throws DatabaseClientException {
        for (RawUserData rawUserData : mUsers) {
            if (rawUserData.getUserId() == userId) {
                return rawUserData;
            }
        }
        throw new DatabaseClientException("User to fetch not found in MockServer.");
    }

    /**
     * Delete a user from the server. A user can only delete himself.
     *
     * @param userId - ID of the user
     * @throws DatabaseClientException if unable to delete user
     */
    public void deleteUser(long userId) throws DatabaseClientException {
        for (int i = 0; i < mUsers.size(); ++i) {
            if (mUsers.get(i).getUserId() == userId) {
                mUsers.remove(i);
                return;
            }
        }
        throw new DatabaseClientException("User to delete not found in MockServer.");
    }

    /**
     * Log user into the server, i.e. get user profile information
     *
     * @param loginRequest a login request
     * @throws DatabaseClientException
     */
    public void loginUser(LoginRequest loginRequest) throws DatabaseClientException {
        SignedInUser signedInUser = SignedInUser.getInstance();
        try {
            long userId = 0;
            for (RawUserData rawUserData : mUsers) {
                if (rawUserData.getMailAddress().equals(loginRequest.toJSON().getString("mail_address"))) {
                    userId = rawUserData.getUserId();
                    break;
                }
            }
            if(userId <= 0) {
                userId = postUserData(new RawUserData(-1, loginRequest.toJSON().getString("user_name_hint"),
                        loginRequest.toJSON().getString("mail_address"),
                        -1));
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("user_id", userId);
            jsonObject.put("mail_address", loginRequest.toJSON().getString("mail_address"));
            jsonObject.put("token", "mockserver default token");
            signedInUser.loginFromJSON(jsonObject);
        } catch (JSONException e) {
            throw new DatabaseClientException(e);
        }
    }

    /**
     * Get an image from the database
     *
     * @param imageId the database key of the image
     * @return the image
     * @throws DatabaseClientException
     */
    public Drawable getImage(long imageId) throws DatabaseClientException {
        throw new DatabaseClientException("Not implemented.");
    }

    /**
     * Post an image to the database
     *
     * @param drawable an image, here as drawable
     * @param imageId  the ID of the image if it should be changed
     * @return the database key of that image
     * @throws DatabaseClientException
     */
    public long postImage(Drawable drawable, long imageId) throws DatabaseClientException {
        throw new DatabaseClientException("Not implemented.");
    }

    /**
     * Post an image to the database
     *
     * @param drawable an image, here as drawable
     * @return the database key of that image
     * @throws DatabaseClientException
     */
    public long postImage(Drawable drawable) throws DatabaseClientException {
        return postImage(drawable, -1);
    }

    /**
     * Delete an image from the database
     *
     * @param imageId the database key of the image
     * @throws DatabaseClientException
     */
    public void deleteImage(long imageId) throws DatabaseClientException {
        throw new DatabaseClientException("Not implemented.");
    }

    /**
     * Post a comment to the database
     * @return the database key of that comment
     * @throws DatabaseClientException
     */
    public long postComment(RawHikeComment comment) throws DatabaseClientException {
        long commentId = comment.getCommentId();
        if (commentId > 0) {
            if (!hasComment(commentId)) {
                throw new DatabaseClientException("Setting Comment that's not there.");
            }
        } else {
            commentId = mAssignedCommentID;
            mAssignedCommentID++;
            comment.setCommentId(commentId);
        }
        putComment(comment);
        return commentId;
    }

    /**
     * Delete a comment from the database
     *
     * @param commentId the database key of the comment
     * @throws DatabaseClientException
     */
    public void deleteComment(long commentId) throws DatabaseClientException {
        mHikeCommentDataBase.remove(commentId);
    }

    private void putComment(RawHikeComment rawHikeComment) throws DatabaseClientException {
        if (rawHikeComment != null) {
            mHikeCommentDataBase.put(rawHikeComment.getCommentId(), rawHikeComment);
        }
    }

    /**
     * Post a vote about a hike.
     */
    public void postVote(RatingVote vote) throws DatabaseClientException {
        throw new DatabaseClientException("Not implemented.");
    }


    // Internal database management functions
    public boolean hasHike(long hikeId) {
        return mHikeDataBase.containsKey(hikeId);
    }

    // Internal database management functions
    public boolean hasComment(long commentId) {
        return mHikeCommentDataBase.containsKey(commentId);
    }

    public RawHikeData getHike(long hikeId) {
        return mHikeDataBase.get(hikeId);
    }

    private void putHike(RawHikeData rawHikeData) throws DatabaseClientException {
        if (rawHikeData != null) {
            if (rawHikeData.getHikeId() == 3) {
                throw new DatabaseClientException("The Mock server cannot have a hike 3.");
            }
            mHikeDataBase.put(rawHikeData.getHikeId(), rawHikeData);
        }
    }

    private static class FixedSizeHashMap<K, V> extends LinkedHashMap<K, V> {
        private final int MAX_ENTRIES;

        FixedSizeHashMap(int maxEntries) {
            super(16 /*initial size*/, 0.75f /*initial load factor*/, true /*update on access*/);
            MAX_ENTRIES = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    }

    /**
     * Create mock hike number 1 (should always exist).
     */
    private void createMockHikeOne() throws DatabaseClientException {
        try {
            RawHikeData rawHikeData = createHikeData();
            mHikeDataBase.put(rawHikeData.getHikeId(), rawHikeData);
        } catch (HikeParseException e) {
            throw new DatabaseClientException(e);
        }
    }

    private static RawHikeData createHikeData() throws HikeParseException {
        try {
            return RawHikeData.parseFromJSON(new JSONObject(PROPER_JSON_ONEHIKE));
        } catch (JSONException e) {
            throw new HikeParseException(e);
        }
    }


}
