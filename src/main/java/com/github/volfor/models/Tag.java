package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Tag {

    private long id;
    private String name;
    @SerializedName("media_count")
    private int mediaCount;

}
