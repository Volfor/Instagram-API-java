package com.github.volfor.responses;

import com.github.volfor.models.Media;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class PopularFeedResponse extends Response {

    private List<Media> items;
    @SerializedName("next_max_id")
    private String nextMaxId;
    @SerializedName("more_available")
    private boolean moreAvailable;
    @SerializedName("auto_load_more_enabled")
    private boolean autoLoadMoreEnabled;
    @SerializedName("num_results")
    private int numResults;
    @SerializedName("max_id")
    private String maxId;

}
