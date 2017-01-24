package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class In {

    private User user;
    private double[] position;
    @SerializedName("time_in_video")
    private long timeInVideo; // ??

}
