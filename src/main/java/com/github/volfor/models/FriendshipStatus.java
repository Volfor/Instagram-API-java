package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class FriendshipStatus {

    private boolean blocking;
    private boolean following;
    @SerializedName("followed_by")
    private boolean followedBy;
    @SerializedName("incoming_request")
    private boolean incomingRequest;
    @SerializedName("outgoing_request")
    private boolean outgoingRequest;
    @SerializedName("is_private")
    private boolean isPrivate;
    @SerializedName("is_blocking_reel")
    private boolean isBlockingReel;
    @SerializedName("is_muting_reel")
    private boolean isMutingReel;

}
