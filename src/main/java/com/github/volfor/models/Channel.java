package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Channel {

    private String title;
    private Media media;
    private String header;
    private String context;
    @SerializedName("channel_type")
    private String channelType;
    @SerializedName("media_count")
    private int mediaCount;
    @SerializedName("channel_id")
    private String channelId;

}
