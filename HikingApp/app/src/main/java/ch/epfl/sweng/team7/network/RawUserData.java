package ch.epfl.sweng.team7.network;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Stores the user information as it's stored on the server
 */

public class RawUserData {

    public static final long USER_ID_UNKNOWN = -1;
    public static final long USER_NAME_MIN_LENGTH = 2;

    private long mUserId;
    private String mUserName;
    private String mMailAddress;
    private long mUserProfilePic;


    public RawUserData(long userId, String userName, String mailAddress, long userProfilePic) {

        // Check that are arguments are valid, otherwise throw exception
        if (userId < 0 && userId != USER_ID_UNKNOWN) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (userName.length() < USER_NAME_MIN_LENGTH) {
            throw new IllegalArgumentException("User name must be at least two characters");
        }
        if (!mailAddress.contains("@")) {
            throw new IllegalArgumentException("Invalid e-mail address");
        }

        mUserId = userId;
        mUserName = userName;
        mMailAddress = mailAddress;
        mUserProfilePic = userProfilePic;

    }

    public long getUserId() {
        return mUserId;
    }

    public String getUserName() {
        return mUserName;
    }

    public String getMailAddress() {
        return mMailAddress;
    }

    public long getUserProfilePic() {
        return mUserProfilePic;
    }

    public void setUserId(long id) {

        if (mUserId < 0 && mUserId != USER_ID_UNKNOWN) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        mUserId = id;
    }

    public void setUserName(String userName) {

        if (userName.length() < 1) {
            throw new IllegalArgumentException("User name must be at least two characters");
        }
        mUserName = userName;
    }

    public void setUserProfilePic(long picId) {
        mUserProfilePic = picId;
    }


    public JSONObject toJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user_id", mUserId);
        jsonObject.put("user_name", mUserName);
        jsonObject.put("mail_address", mMailAddress);
        jsonObject.put("profile_image_id", mUserProfilePic);
        return jsonObject;
    }

    public static RawUserData parseFromJSON(JSONObject jsonObject) throws JSONException {

        return new RawUserData(
                jsonObject.getLong("user_id"),
                jsonObject.getString("user_name"),
                jsonObject.getString("mail_address"),
                jsonObject.getLong("profile_image_id"));
    }

}
