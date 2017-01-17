package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Stories {

    private long id;
    private List<Tray> tray;
    @SerializedName("is_portrait")
    private boolean isPortrait;

}
