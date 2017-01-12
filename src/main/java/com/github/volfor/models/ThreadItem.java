package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ThreadItem {

    private String text;
    private long timestamp;
    @SerializedName("item_id")
    private String itemId;
    @SerializedName("item_type")
    private String itemType;
    @SerializedName("user_id")
    private long userId;
    @SerializedName("client_context")
    private String clientContext; // ??

}
