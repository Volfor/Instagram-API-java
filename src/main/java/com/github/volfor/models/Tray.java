package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Tray {

    private User user;
    private long id;
    private int seen;
    @SerializedName("ranked_position")
    private int rankedPosition;
    @SerializedName("expiring_at")
    private long expiringAt;
    @SerializedName("can_reply")
    private boolean canReply;
    @SerializedName("prefetch_count")
    private int prefetchCount;
    @SerializedName("latest_reel_media")
    private long latestReelMedia;
    @SerializedName("source_token")
    private String sourceToken;
    @SerializedName("seen_ranked_position")
    private int seenRankedPosition;

}
