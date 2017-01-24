package com.github.volfor.responses;

import com.github.volfor.models.Media;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class UsertagsResponse extends Response {

    private List<Media> items;
    @SerializedName("auto_load_more_enabled")
    private boolean autoLoadMoreEnabled;
    @SerializedName("new_photos")
    private List<Media> newPhotos;
    @SerializedName("num_results")
    private int numResults;
    @SerializedName("more_available")
    private boolean moreAvailable;
    @SerializedName("requires_review")
    private boolean requiresReview;
    @SerializedName("total_count")
    private int totalCount;
    @SerializedName("next_max_id")
    private String nextMaxId;

}