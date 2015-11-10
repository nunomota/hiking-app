package ch.epfl.sweng.team7.gpsService;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.util.Log;

import ch.epfl.sweng.team7.gpsService.containers.GPSFootPrint;
import ch.epfl.sweng.team7.gpsService.containers.GPSPath;
import ch.epfl.sweng.team7.gpsService.containers.coordinates.GeoCoords;

/**
 * Class used to read device's GPS-related information
 * (such has latitude, longitude, altitude and time).
 */
public final class GPSManager {

    private final static String LOG_FLAG = "GPS_Manager";
    private static GPSManager instance = new GPSManager();

    //GPS stored information
    private GPSPath gpsPath = null;
    private boolean isTracking = false;
    private GPSFootPrint lastFootPrint = null;

    //GPS service communication
    private GPSService gpsService;
    private ServiceConnection serviceConnection;

    public static GPSManager getInstance() {
        return instance;
    }

    /**
     * Method called to toggle hike tracking
     * on/off, according to previous state.
     */
    public void toggleTracking() {
        if (!isTracking) {
            startTracking();
        } else {
            stopTracking();
        }
    }

    /**
     * Method called to get user's last known coordinates.
     * @return GeoCoords object containing user's last known coordinates
     * @throws NullPointerException whenever there is no last known position
     */
    public GeoCoords getCurrentCoords() throws NullPointerException {
        if (this.lastFootPrint == null) {
            throw new NullPointerException("Trying to access a null gps footprint");
        }
        return this.lastFootPrint.getGeoCoords();
    }

    /**
     * Method called to update user's last know coordinates
     * @param newLocation Location object containing GPS fetched data
     */
    public void updateCurrentLocation(Location newLocation) {
        if (newLocation != null) {
            this.lastFootPrint = new GPSFootPrint(GeoCoords.fromLocation(newLocation), newLocation.getTime());
            if (this.isTracking) gpsPath.addFootPrint(this.lastFootPrint);
        }
    }

    @Override
    public String toString() {
        String gpsPathInformation = (isTracking && gpsPath != null)?String.format("yes -> %s", gpsPath.toString()):"No";
        String lastFootPrintCoords = (this.lastFootPrint != null)?this.lastFootPrint.getGeoCoords().toString():"null";
        long lastFootPrintTimeStamp = (this.lastFootPrint != null)?this.lastFootPrint.getTimeStamp():0;
        return String.format("\n|---------------------------\n" +
                "| Saving to memory: %s\n" +
                "| Last Coordinates: %s\n" +
                "| TimeStamp: %d\n" +
                "|---------------------------", gpsPathInformation, lastFootPrintCoords, lastFootPrintTimeStamp);
    }

    private GPSManager() {
        setupServiceConnection();
    }

    /**
     * Private method to setup communication with the
     * GPSService that will be running in the background.
     */
    private void setupServiceConnection() {
        serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the service has been
                // established
                gpsService = ((GPSService.LocalBinder)service).getService();
            }

            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected
                gpsService = null;
            }
        };
    }

    /**
     * Method called to start tracking - start
     * storing user's coordinates.
     */
    private void startTracking() {
        this.isTracking = true;
        gpsPath = new GPSPath();
    }

    /**
     * Method called to stop tracking - stop
     * storing user's coordinates and store the
     * previous ones.
     */
    private void stopTracking() {
        this.isTracking = false;
        //TODO send GPSPath to another class, maybe DB, to store it in memory/upload it
        Log.d(LOG_FLAG, "Saving GPSPath to memory: " + gpsPath.toString());
        gpsPath = null;
    }
}
