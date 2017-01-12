package com.github.volfor.responses;

import com.github.volfor.models.User;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class FollowersResponse extends Response {

    private List<User> users;
    @SerializedName("next_max_id")
    private String nextMaxId;
    @SerializedName("big_list")
    private boolean bigList;

}