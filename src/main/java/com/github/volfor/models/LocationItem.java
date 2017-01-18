package com.github.volfor.models;

import lombok.Data;

@Data
public class LocationItem {

    private String subtitle;
    private String title;
    private Location location;
//    @SerializedName("media_bundles")
//    private List<> mediaBundles; // ?

}
