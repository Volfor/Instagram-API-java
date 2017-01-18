package com.github.volfor.responses;

import com.github.volfor.models.Tag;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class SearchTagResponse extends Response {

    private List<Tag> results;
    @SerializedName("has_more")
    private boolean hasMore;

}
