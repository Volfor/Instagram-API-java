package com.github.volfor.responses;

import com.github.volfor.models.ExploreItem;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class ExploreResponse extends Response {

    private List<ExploreItem> items;
    @SerializedName("num_results")
    private int numResults;
    @SerializedName("auto_load_more_enabled")
    private boolean autoLoadMoreEnabled;
    @SerializedName("more_available")
    private boolean moreAvailable;
    @SerializedName("next_max_id")
    private String nextMaxId;
    @SerializedName("max_id")
    private String maxId;

}
