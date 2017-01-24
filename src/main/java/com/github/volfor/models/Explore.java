package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Explore {

    private String explanation;
    @SerializedName("actor_id")
    private long actorId;
    @SerializedName("source_token")
    private String sourceToken;

}
