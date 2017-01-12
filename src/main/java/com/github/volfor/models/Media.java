package com.github.volfor.models;

import lombok.Data;

import java.util.List;

@Data
public class Media {

    private long takenAt;
    private long pk;
    private String id;
    private long deviceTimestamp;
    private int mediaType;
    private String code;
    private String clientCachekey;
    private int filterType;
    private ImageVersions2 imageVersions2;

    private int originalWidth;
    private int originalHeight;
    private double viewCount;
    private String organicTrackingToken;
    private boolean hasMoreComments;
    private int maxNumVisiblePreviewComments;

    private List<Comment> previewComments;
    private boolean reelMentions; // ??
    private boolean storyCta; // ??
    private int captionPosition; // ??
    private long expiringAt; // ??
    private boolean isReelMedia; // ??
    private String nextMaxId;

    private List<Comment> comments;
    private int commentСount;

    private Caption caption;
    private boolean captionIsEdited;
    private boolean photoOfYou;

    private List<Video> videoVersions;
    private boolean hasAudio;
    private double videoDuration;

    private User user;

    private List<User> likers;
    private int likeCount;

    private String preview;
    private boolean hasLiked;
    private String exploreContext;
    private String exploreSourceToken;

    private Explore explore;
    private String impressionToken;

    private Usertag usertags;
    private int mediaOrAd; // ??

    private MediaInfo media;
    private List<Story> stories;
    private List<String> topLikers;
    private boolean comment_likes_enabled;

    @Data
    class ImageVersions2 {
        private List<Picture> candidates;
    }

}
