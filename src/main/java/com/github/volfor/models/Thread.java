package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Thread {

    private boolean named;
    private List<User> users;
    private boolean muted;
    private boolean canonical;
    private List<ThreadItem> items;
    private User inviter;
    private boolean pending;

    @SerializedName("viewer_id")
    private long viewerId;
    @SerializedName("thread_id")
    private String threadId;
    @SerializedName("last_activity_at")
    private long lastActivityAt;
    @SerializedName("is_spam")
    private boolean isSpam;
    @SerializedName("has_newer")
    private boolean hasNewer;
    @SerializedName("has_older")
    private boolean hasOlder;
    @SerializedName("newest_cursor")
    private String newestCursor;
    @SerializedName("oldest_cursor")
    private String oldestCursor;

    @SerializedName("left_users")
    private List<User> leftUsers;
    @SerializedName("thread_type")
    private String threadType;
    @SerializedName("thread_title")
    private String threadTitle;

}
