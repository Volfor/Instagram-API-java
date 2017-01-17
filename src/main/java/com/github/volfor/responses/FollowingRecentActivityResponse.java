package com.github.volfor.responses;

import com.github.volfor.models.Story;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class FollowingRecentActivityResponse extends Response {

    private List<Story> stories;
    @SerializedName("next_max_id")
    private String nextMaxId;
    @SerializedName("auto_load_more_enabled")
    private boolean autoLoadMoreEnabled;

}
