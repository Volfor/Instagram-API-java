package com.github.volfor.models;

import lombok.Data;

@Data
public class ThreadItem {

    private String itemId;
    private String itemType;
    private String text;
    private long userId;
    private long timestamp;
    private String clientContext; // ??

}
