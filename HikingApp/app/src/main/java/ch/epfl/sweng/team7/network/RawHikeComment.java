package ch.epfl.sweng.team7.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class RawHikeComment {
    private final static String LOG_FLAG = "Network_RawHikeComment";
    public static final long COMMENT_ID_UNKNOWN = -1;

    private long mCommentId;
    private long mCommentHikeId;
    private long mCommentOwnerId;
    private String mCommentOwnerName;
    private String mCommentText;
    private Date mCommentDate;

    public RawHikeComment(long commentId, long commentHikeId, long commentOwnerId,
                          String commentText) {
        mCommentId = commentId;
        mCommentHikeId = commentHikeId;
        mCommentOwnerId = commentOwnerId;
        mCommentText = commentText;
    }

    public RawHikeComment(long commentId, long commentHikeId, long commentOwnerId,
                          String commentOwnerName, String commentText, Date commentDate) {
        mCommentId = commentId;
        mCommentHikeId = commentHikeId;
        mCommentOwnerId = commentOwnerId;
        mCommentOwnerName = commentOwnerName;
        mCommentText = commentText;
        mCommentDate = commentDate;
    }

    public long getCommentId() {
        return mCommentId;
    }

    public long getCommentHikeId() {
        return mCommentHikeId;
    }

    public long getCommentOwnerId() {
        return mCommentOwnerId;
    }

    public String getCommentOwnerName() {
        return mCommentOwnerName;
    }

    public String getCommentText() {
        return mCommentText;
    }

    public Date getCommentDate() {
        return mCommentDate;
    }

    public void setCommentId(long id) {
        if (mCommentId < 0 && mCommentId != COMMENT_ID_UNKNOWN) {
            throw new IllegalArgumentException("Comment ID must be positive");
        }
        mCommentId = id;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("comment_id", mCommentId);
        jsonObject.put("hike_id", mCommentHikeId);
        jsonObject.put("user_id", mCommentOwnerId);
        jsonObject.put("comment_text", mCommentText);
        return jsonObject;
    }

    public static RawHikeComment parseFromJSON(JSONObject jsonObject) throws JSONException {
        Long dateLongForm = jsonObject.getLong("date");
        Date date = new Date(dateLongForm * 1000L);
        return new RawHikeComment(
                jsonObject.getLong("comment_id"),
                jsonObject.getLong("hike_id"),
                jsonObject.getLong("user_id"),
                jsonObject.getString("user_name"),
                jsonObject.getString("comment_text"),
                date);
    }
}

