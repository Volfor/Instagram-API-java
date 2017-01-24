package com.github.volfor.responses;

import com.github.volfor.models.Media;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class MediaInfoResponse extends Response {

    private List<Media> items;
    @SerializedName("more_available")
    private boolean moreAvailable;
    @SerializedName("auto_load_more_enabled")
    private boolean autoLoadMoreEnabled;
    @SerializedName("num_results")
    private int numResults;

}
