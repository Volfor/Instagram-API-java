package com.github.volfor.responses;

import com.github.volfor.models.GeoMedia;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class GeoMediaResponse extends Response {

    @SerializedName("geo_media")
    private List<GeoMedia> geoMedia;

}