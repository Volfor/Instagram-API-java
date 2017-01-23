package com.github.volfor.responses;

import com.github.volfor.models.FriendshipStatus;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class FriendshipResponse extends Response {

    @SerializedName("friendship_status")
    private FriendshipStatus friendshipStatus;

}
