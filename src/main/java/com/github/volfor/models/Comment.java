package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Comment {

    private User user;
    @SerializedName("user_id")
    private long userId;
    @SerializedName("created_at_utc")
    private long createdAtUtc;
    @SerializedName("created_at")
    private long createdAt;
    @SerializedName("bit_flags")
    private int bitFlags;

    private long pk;
    private int type;
    private String status;
    private String text;
    @SerializedName("content_type")
    private String contentType;
    @SerializedName("media_id")
    private long mediaId;
    @SerializedName("has_translation")
    private boolean hasTranslation;

}
