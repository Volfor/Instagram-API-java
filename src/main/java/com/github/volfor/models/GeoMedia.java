package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class GeoMedia {

    private double lat;
    private double lng;
    private String thumbnail;
    @SerializedName("media_id")
    private String mediaId;
    @SerializedName("display_url")
    private String displayUrl;
    @SerializedName("low_res_url")
    private String lowResUrl;

}