package com.github.volfor.models;

import lombok.Data;

@Data
public class Caption {

    private String status;
    private long userId;
    private long createdAtUtc;
    private long createdAt;
    private int bitFlags;

    private User user;
    private String contentType;
    private String text;
    private long mediaId;
    private long pk;
    private int type;
    private boolean hasTranslation;

}
