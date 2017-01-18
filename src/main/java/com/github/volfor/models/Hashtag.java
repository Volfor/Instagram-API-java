package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Hashtag {

    private String name;
    private long id;
    @SerializedName("media_count")
    private long mediaCount;

}
