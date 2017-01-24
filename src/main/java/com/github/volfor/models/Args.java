package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Args {

    private List<MediaInfo> media;
    private List<Link> links;
    private String text;
    private double timestamp;
    @SerializedName("profile_id")
    private long profileId;
    @SerializedName("profile_image")
    private String profileImage;

}
