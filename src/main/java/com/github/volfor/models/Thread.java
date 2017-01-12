package com.github.volfor.models;

import lombok.Data;

import java.util.List;

@Data
public class Thread {

    private boolean named;
    private List<User> users;
    private boolean hasNewer;
    private long viewerId;
    private String threadId;
    private long lastActivityAt;
    private String newestCursor;
    private boolean isSpam;
    private boolean hasOlder;
    private String oldestCursor;

    private List<User> leftUsers;
    private boolean muted;

    private List<ThreadItem> items;
    private String threadType;
    private String threadTitle;
    private boolean canonical;

    private User inviter;
    private boolean pending;

}
