package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Usertag {

    private List<In> in;
    @SerializedName("photo_of_you")
    private boolean photoOfYou; // no

}
