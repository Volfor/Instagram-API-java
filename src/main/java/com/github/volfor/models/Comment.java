package com.github.volfor.models;

import lombok.Data;

@Data
public class Comment {

    private String status;
    private long userId;
    private long createdAtUtc;
    private long createdAt;
    private int bitFlags;

    private User user;
    private long pk;
    private String text;

    private int type;
    private String contentType;
    private long mediaId;

}
