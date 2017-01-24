package com.github.volfor.responses;

import com.github.volfor.models.User;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class SearchUserResponse extends Response {

    private List<User> users;
    @SerializedName("num_results")
    private int numResults;
    @SerializedName("has_more")
    private boolean hasMore;
    @SerializedName("next_max_id")
    private String nextMaxId;

}
