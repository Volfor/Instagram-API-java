package com.github.volfor.responses;

import com.github.volfor.models.Hashtags;
import com.github.volfor.models.Place;
import com.github.volfor.models.User;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class FbSearchResponse extends Response {

    private List<Place> places;
    private List<User> users;
    private List<Hashtags> hashtags;
    @SerializedName("has_more")
    private boolean hasMore;

}
