package com.github.volfor.models;

import lombok.Data;

import java.util.List;

@Data
public class Args {

    private MediaInfo media;
    private List<Link> links;
    private String text;
    private long profileId;
    private String profileImage;
    private long timestamp;

}
