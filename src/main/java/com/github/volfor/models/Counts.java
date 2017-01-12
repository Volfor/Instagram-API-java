package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Counts {

    private String relationships; // ??
    private int requests;
    @SerializedName("photos_of_you")
    private int photosOfYou;

}
