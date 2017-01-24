package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Media {

    private long pk;
    private String id;
    private String code;
    @SerializedName("taken_at")
    private long takenAt;
    @SerializedName("device_timestamp")
    private long deviceTimestamp;
    @SerializedName("media_type")
    private int mediaType;
    @SerializedName("client_cache_key")
    private String clientCacheKey;
    @SerializedName("filter_type")
    private int filterType;
    @SerializedName("image_versions2")
    private ImageVersions2 imageVersions2;

    @SerializedName("original_width")
    private int originalWidth;
    @SerializedName("original_height")
    private int originalHeight;
    @SerializedName("view_count")
    private int viewCount;
    @SerializedName("organic_tracking_token")
    private String organicTrackingToken;
    @SerializedName("has_more_comments")
    private boolean hasMoreComments;
    @SerializedName("max_num_visible_preview_comments")
    private int maxNumVisiblePreviewComments;

    @SerializedName("preview_comments")
    private List<Comment> previewComments;
    @SerializedName("reel_mentions")
    private boolean reelMentions; // ??
    @SerializedName("story_cta")
    private boolean storyCta; // ??
    @SerializedName("caption_position")
    private int captionPosition; // ??
    @SerializedName("expiring_at")
    private long expiringAt; // ??
    @SerializedName("is_reel_media")
    private boolean isReelMedia; // ??
    @SerializedName("next_max_id")
    private String nextMaxId;

    private List<Comment> comments;
    @SerializedName("comment_count")
    private int commentCount;

    private User user;
    private Comment caption;
    @SerializedName("caption_is_edited")
    private boolean captionIsEdited;
    @SerializedName("photo_of_you")
    private boolean photoOfYou;

    @SerializedName("video_versions")
    private List<Video> videoVersions;
    @SerializedName("has_audio")
    private boolean hasAudio;
    @SerializedName("video_duration")
    private double videoDuration;

    private List<User> likers;
    @SerializedName("like_count")
    private int likeCount;

    @SerializedName("has_liked")
    private boolean hasLiked;
    @SerializedName("explore_context")
    private String exploreContext;
    @SerializedName("explore_source_token")
    private String exploreSourceToken;

    @SerializedName("impression_token")
    private String impressionToken;
    @SerializedName("media_or_ad")
    private int mediaOrAd; // ??

    private String preview;
    private Explore explore;
    private Usertag usertags;
    private List<Story> stories;
    @SerializedName("media")
    private MediaInfo mediaInfo;
    @SerializedName("top_likers")
    private List<String> topLikers;
    @SerializedName("comment_likes_enabled")
    private boolean commentLikesEnabled;

    @Data
    class ImageVersions2 {
        private List<Picture> candidates;
    }

}
