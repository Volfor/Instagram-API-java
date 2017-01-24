package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Inbox {

    private List<Thread> threads;
    @SerializedName("unseen_count")
    private int unseenCount;
    @SerializedName("has_older")
    private boolean hasOlder;
    @SerializedName("unseen_count_ts")
    private long unseenCountTs;

}
