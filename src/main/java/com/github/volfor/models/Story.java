package com.github.volfor.models;

import lombok.Data;

@Data
public class Story {

    private String pk;
    private Counts counts;
    private Args args;
    private int type;

}
