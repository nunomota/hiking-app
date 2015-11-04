/*
 * Copyright 2015 EPFL. All rights reserved.
 *
 * Created by simon.schuetz on 04 Nov 2015
 */
package ch.epfl.sweng.team7.database;

import org.json.JSONException;
import org.json.JSONObject;

import ch.epfl.sweng.team7.network.DatabaseClient;
import ch.epfl.sweng.team7.network.RawHikeData;

public class DefaultLocalCache implements LocalCache {
    private final DatabaseClient mDatabaseClient;

    public DefaultLocalCache(DatabaseClient databaseClient) {
        mDatabaseClient = databaseClient;
    }

    public HikeData getHikeById(long hikeId) throws LocalCacheException {
        if(hikeId == 1) {
            final String PROPER_JSON_ONEHIKE = "{\n"
                    + "  \"hike_id\": 1,\n"
                    + "  \"owner_id\": 48,\n"
                    + "  \"date\": 123201,\n"
                    + "  \"hike_data\": [\n"
                    + "    [0.0, 0.0, 123201],\n"
                    + "    [0.1, 0.1, 123202],\n"
                    + "    [0.2, 0.0, 123203],\n"
                    + "    [0.3,89.9, 123204],\n"
                    + "    [0.4, 0.0, 123205]\n"
                    + "  ]\n"
                    + "}\n";
            try {
                return new DefaultHikeData(RawHikeData.parseFromJSON(new JSONObject(PROPER_JSON_ONEHIKE)));
            } catch(JSONException e) {
                throw new LocalCacheException(e);
            }
        }
        throw new LocalCacheException("Hike not found in database.");
    }
}
