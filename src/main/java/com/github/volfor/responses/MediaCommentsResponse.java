package com.github.volfor.responses;

import com.github.volfor.models.Comment;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class MediaCommentsResponse extends Response {

    private List<Comment> comments;
    private Comment caption;
    @SerializedName("comment_count")
    private int commentCount;
    @SerializedName("comment_likes_enabled")
    private boolean commentLikesEnabled;
    @SerializedName("next_max_id")
    private String nextMaxId;
    @SerializedName("has_more_comments")
    private boolean hasMoreComments;
    @SerializedName("caption_is_edited")
    private boolean captionIsEdited;
    @SerializedName("preview_comments")
    private List<Comment> previewComments;

}
