package com.github.volfor.models;

import lombok.Data;

import java.util.List;

@Data
public class Inbox {

    private int unseenCount;
    private boolean hasOlder;
    private long unseenCountTs;
    private List<Thread> threads;

}
