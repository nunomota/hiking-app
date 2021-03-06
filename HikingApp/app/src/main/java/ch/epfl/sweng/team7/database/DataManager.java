/*
 * Copyright 2015 EPFL. All rights reserved.
 *
 * Created by simon.schuetz on 05 Nov 2015
 */
package ch.epfl.sweng.team7.database;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLngBounds;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ch.epfl.sweng.team7.authentication.LoginRequest;
import ch.epfl.sweng.team7.network.DatabaseClient;
import ch.epfl.sweng.team7.network.DatabaseClientException;
import ch.epfl.sweng.team7.network.DefaultNetworkProvider;
import ch.epfl.sweng.team7.network.NetworkDatabaseClient;
import ch.epfl.sweng.team7.network.RatingVote;
import ch.epfl.sweng.team7.network.RawHikeComment;
import ch.epfl.sweng.team7.network.RawHikeData;
import ch.epfl.sweng.team7.network.RawUserData;

public final class DataManager {

    private final static String LOG_FLAG = "DB_DataManager";
    private static final String SERVER_URL = "https://footpath-1104.appspot.com";//"http://10.0.3.2:8080";//
    private static LocalCache sLocalCache;
    private static DatabaseClient sDatabaseClient;

    private static final String TAG = "DataManager";

    /**
     * @return the DataManager
     */
    public static DataManager getInstance() {
        return DataManagerHolder.INSTANCE;
    }

    /**
     * Static setter: Use only for testing!
     */
    public static void setLocalCache(LocalCache localCache) {
        if (localCache == null) {
            throw new IllegalArgumentException();
        }
        sLocalCache = localCache;
    }

    /**
     * Static setter: Use only for testing!
     */
    public static void setDatabaseClient(DatabaseClient databaseClient) {
        if (databaseClient == null) {
            throw new IllegalArgumentException();
        }
        sDatabaseClient = databaseClient;
    }

    /**
     * Static reset: Use only for testing!
     */
    public static void reset() {
        sDatabaseClient = new NetworkDatabaseClient(SERVER_URL, new DefaultNetworkProvider());
        sLocalCache = new DefaultLocalCache();
    }

    /**
     * Get a HikeData object by its identifier.
     *
     * @return a valid HikeData object
     * @throws DataManagerException on error
     */
    public HikeData getHike(long hikeId) throws DataManagerException {

        // Check if hike is cached
        HikeData hikeData = sLocalCache.getHike(hikeId);
        if (hikeData != null) {
            return hikeData;
        }

        // Retrieve hike from the server
        try {
            RawHikeData rawHikeData = sDatabaseClient.fetchSingleHike(hikeId);
            hikeData = processAndCache(rawHikeData);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
        return hikeData;
    }

    /**
     * Get multiple HikeData objects by its identifiers
     *
     * @return a list of valid HikeData objects
     * @throws DataManagerException on error
     */
    public List<HikeData> getMultipleHikes(List<Long> hikeIdList) throws DataManagerException {

        // Compile a list of hikes to ask from the server
        List<HikeData> hikeDataList = new ArrayList<>();
        List<Long> hikeIdNotCached = new ArrayList<>();
        for (long hikeId : hikeIdList) {
            HikeData hikeData = sLocalCache.getHike(hikeId);
            if (hikeData != null) {
                hikeDataList.add(hikeData);
            } else {
                hikeIdNotCached.add(hikeId);
            }
        }

        // Ask the server
        if (hikeIdNotCached.size() > 0) {
            List<RawHikeData> rawHikeDataList;
            try {
                rawHikeDataList = sDatabaseClient.fetchMultipleHikes(hikeIdNotCached);
            } catch (DatabaseClientException e) {
                throw new DataManagerException(e);
            }

            // Convert and cache HikeData
            for (RawHikeData rawHikeData : rawHikeDataList) {
                hikeDataList.add(processAndCache(rawHikeData));
            }
        }
        return hikeDataList;
    }

    public List<HikeData> getUserHikes(Long userId) throws DataManagerException {
        List<HikeData> hikeDataList;
        try {
            List<Long> hikeIds = sDatabaseClient.getHikeIdsOfUser(userId);
            hikeDataList = getMultipleHikes(hikeIds);
        } catch (DatabaseClientException|DataManagerException e) {
            throw new DataManagerException(e);
        }
        return hikeDataList;
    }

    /**
     * Query the server and local cache for hikes corresponding to a given search query
     *
     * @param query - search string
     * @return list of hikes containing the query string.
     */
    public List<HikeData> searchHike(String query) throws DataManagerException {

        // Ask the server for the hike Ids
        List<Long> hikeIdList;
        try {
            hikeIdList = sDatabaseClient.getHikeIdsWithKeywords(query);
        } catch (DatabaseClientException e) {
            // Perform local search on network problems
            hikeIdList = sLocalCache.searchHike(query);
        }

        return getMultipleHikes(hikeIdList);
    }

    /**
     * Retrieves a list of all hikes in given boundaries
     *
     * @param bounds the boundaries of a rectangle
     * @return a list of hikes in the given rectangle
     * @throws DataManagerException on error
     */
    public List<HikeData> getHikesInWindow(LatLngBounds bounds) throws DataManagerException {

        // Ask the server for the hike Ids
        List<Long> hikeIdList;
        try {
            hikeIdList = sDatabaseClient.getHikeIdsInWindow(bounds);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }

        return getMultipleHikes(hikeIdList);
    }

    /**
     * Method to post a hike.
     * @param rawHikeData the hike to post
     * @throws DataManagerException
     */
    public long postHike(RawHikeData rawHikeData) throws DataManagerException {
        try {
            return sDatabaseClient.postHike(rawHikeData);
        } catch (DatabaseClientException e) {
            Log.d(LOG_FLAG, "DatabaseClientException in post hike in Network database");
            throw new DataManagerException(e);
        }
    }

    public long postComment(RawHikeComment rawHikeComment) throws DataManagerException {
        try {
            return sDatabaseClient.postComment(rawHikeComment);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    /**
     * Converts a RawHikeData container into a cacheable HikeData object, caches and returns it
     */
    private HikeData processAndCache(RawHikeData rawHikeData) {
        HikeData hikeData = new DefaultHikeData(rawHikeData);
        sLocalCache.putHike(hikeData);
        return hikeData;
    }

    public long storeImage(Drawable image) throws DataManagerException {
        try {
            return sDatabaseClient.postImage(image);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    public Drawable getImage(long imageId) throws DataManagerException {
        try {
            return sDatabaseClient.getImage(imageId);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    /**
     * Store user data in database and update local cache
     *
     * @param rawUserData - a raw user data object
     */
    public void setUserData(RawUserData rawUserData) throws DataManagerException {

        // update user data in cache and database
        try {
            sDatabaseClient.postUserData(rawUserData);
            UserData defaultUserData = new DefaultUserData(rawUserData);
            sLocalCache.setUserData(defaultUserData);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    /**
     * Store user data in database
     *
     * @param rawUserData - a raw user data object
     * @return userId - new id assigned by the server
     */
    public long addNewUser(RawUserData rawUserData) throws DataManagerException {

        try {
            return sDatabaseClient.postUserData(rawUserData);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }


    /**
     * Change user name
     *
     * @param newName,mailAddress the new user name and mailAddress as identifier
     */
    public void changeUserName(String newName, long userId) throws DataManagerException {

        // get current user data then update the database
        UserData userData = getUserData(userId);
        RawUserData rawUserData = new RawUserData(userData.getUserId(), newName, userData.getMailAddress(), userData.getUserProfilePic());
        setUserData(rawUserData);

    }

    public void setUserProfilePic(long newPicId, long userId) throws DataManagerException {
        UserData userData = getUserData(userId);
        RawUserData rawUserData = new RawUserData(userData.getUserId(), userData.getUserName(),
                userData.getMailAddress(), newPicId);
        setUserData(rawUserData);
    }

    /**
     * Retrieve a user data object from cache or database
     *
     *
     * @param userId - id assigned to identify user
     * @return UserData object
     */
    public UserData getUserData(long userId) throws DataManagerException {

        // check if user data is stored in cache, otherwise return from server
        if (sLocalCache.getUserData(userId) != null &&
                sLocalCache.getUserData(userId).getUserId() == userId) {
            return sLocalCache.getUserData(userId);
        } else {
            try {
                RawUserData rawUserData = sDatabaseClient.fetchUserData(userId);
                UserData userData = new DefaultUserData(rawUserData);
                sLocalCache.setUserData(userData); // update cache
                return userData;
            } catch (DatabaseClientException e) {
                throw new DataManagerException(e);
            }
        }
    }

    /**
     * Login for the user with the server.
     */

    public void loginUser(LoginRequest loginRequest) throws DataManagerException {
        try {
            sDatabaseClient.loginUser(loginRequest);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    public void postVote(RatingVote vote) throws DataManagerException {
        try {
            sLocalCache.getHike(vote.getHikeId()).getRating().update(vote);
            sDatabaseClient.postVote(vote);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }


    /**
     * Method to fetch pictures from the server
     * @param pictureId the database ID of the picture
     * @return a picture
     * @throws DatabaseClientException
     */
    public Drawable getPicture(long pictureId) throws DatabaseClientException {
        // Check if PictureAnnotation is cached
        Drawable picture = sLocalCache.getPicture(pictureId);
        if (picture != null) {
            return picture;
        }
        return sDatabaseClient.getImage(pictureId);
    }

    /**
     * Method to export the hike as a gpx file to the phone's internal storage
     * @param hikeData,context - the hike to be saved, the applications context
     * @return filepath as a string
     */

    public String saveGPX(HikeData hikeData, Context context) throws DataManagerException {

        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document doc = documentBuilder.newDocument();

            DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
            String date = format.format(hikeData.getDate());

            // Constructing the xml file by adding and linking elements
            Element rootElement = doc.createElement("gpx");
            doc.appendChild(rootElement);
            rootElement.setAttribute("version", "1.0");
            rootElement.setAttribute("creator", String.valueOf(hikeData.getOwnerId()));
            rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            rootElement.setAttribute("xmlns", "http://www.topografix.com/GPX/1/0");
            rootElement.setAttribute("xsi:schemaLocation", "http://www.topografix.com/GPX/1/0/gpx.xsd");

            Element timeElement = doc.createElement("time");
            timeElement.appendChild(doc.createTextNode(date));
            rootElement.appendChild(timeElement);

            Element trackElement = doc.createElement("trk");
            Element trackName = doc.createElement("name");
            trackName.appendChild(doc.createTextNode(hikeData.getTitle()));
            trackElement.appendChild(trackName);
            rootElement.appendChild(trackElement);

            Element trackSegment = doc.createElement("trkseg");
            trackElement.appendChild(trackSegment);

            // add the track points to construct the segment
            for (int i = 0; i < hikeData.getHikePoints().size(); i++) {

                HikePoint hikePoint = hikeData.getHikePoints().get(i);

                Element trackPoint = doc.createElement("trkpt");
                trackPoint.setAttribute("lat", String.valueOf(hikePoint.getPosition().latitude));
                trackPoint.setAttribute("lon", String.valueOf(hikePoint.getPosition().longitude));

                Element elevation = doc.createElement("ele");
                elevation.appendChild(doc.createTextNode(String.valueOf(hikePoint.getElevation())));
                trackPoint.appendChild(elevation);

                Element pointTime = doc.createElement("time");
                date = format.format(hikePoint.getTime());
                pointTime.appendChild(doc.createTextNode(date));


                trackPoint.appendChild(pointTime);
                trackSegment.appendChild(trackPoint);

            }
            // transform content into xml and save it to a file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(doc);

            String fileName = "Hike_" + String.valueOf(hikeData.getHikeId() + ".xml");
            File file = new File(context.getExternalFilesDir(null), fileName);

            Log.d(LOG_FLAG, file.getAbsolutePath());
            StreamResult hikeStreamResult = new StreamResult(file);

            // format properly before writing content to file
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(domSource, hikeStreamResult);

            return file.getAbsolutePath();

        } catch (ParserConfigurationException e) {
            Log.d(LOG_FLAG, "Failed to build xml file");
            throw new DataManagerException(e);
        } catch (TransformerException e) {
            Log.d(LOG_FLAG, "Failed to write content to file");
            throw new DataManagerException(e);
        }
    }

    public long postPicture(Drawable picture) throws DataManagerException {
        try {
            return sDatabaseClient.postImage(picture);
        } catch (DatabaseClientException e) {
            throw new DataManagerException(e);
        }
    }

    /**
     * Creates the LocalCache and DatabaseClient
     */
    private DataManager() {
        if (sDatabaseClient == null) {
            sDatabaseClient = new NetworkDatabaseClient(SERVER_URL, new DefaultNetworkProvider());
        }
        if (sLocalCache == null) {
            sLocalCache = new DefaultLocalCache();
        }
    }

    /**
     * Holder for the data manager.
     * Using initialization-on-demand to create a DataManager
     * the first time getInstance() is called.
     */
    private static class DataManagerHolder {
        private static final DataManager INSTANCE = new DataManager();
    }
}
