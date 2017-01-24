package com.github.volfor.responses;

import com.github.volfor.models.User;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class MediaLikersResponse extends Response {

    private List<User> users;
    @SerializedName("user_count")
    private int userCount;

}