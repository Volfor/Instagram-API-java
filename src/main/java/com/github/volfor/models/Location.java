package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Location {

    private String name;
    private double lat;
    private double lng;
    private long pk;
    private String address;
    private String city;
    @SerializedName("external_source")
    private String externalSource;
    @SerializedName("facebook_places_id")
    private long facebookPlacesId;

}
