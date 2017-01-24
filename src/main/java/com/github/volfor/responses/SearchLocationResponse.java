package com.github.volfor.responses;

import com.github.volfor.models.LocationItem;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class SearchLocationResponse extends Response {

    private List<LocationItem> items;
    @SerializedName("has_more")
    private boolean hasMore;

}
