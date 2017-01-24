package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class User {

    private String username;
    @SerializedName("has_anonymous_profile_picture")
    private boolean hasAnonProfilePic;
    @SerializedName("is_favorite")
    private boolean isFavorite;
    @SerializedName("profile_pic_url")
    private String profilePicUrl;
    @SerializedName("full_name")
    private String fullName;
    @SerializedName("pk")
    private long pk;
    @SerializedName("is_verified")
    private boolean isVerified;
    @SerializedName("is_private")
    private boolean isPrivate;
    @SerializedName("coeff_weight")
    private double coeffWeight;
    @SerializedName("friendship_status")
    private FriendshipStatus friendshipStatus;

    private String byline;
    private String biography;
    @SerializedName("hd_profile_pic_versions")
    private List<Picture> hdProfilePicVersions;
    @SerializedName("search_social_context")
    private String searchSocialContext;
    @SerializedName("unseen_count")
    private int unseenCount;
    @SerializedName("mutual_followers_count")
    private double mutualFollowersCount;
    @SerializedName("follower_count")
    private int followerCount;
    @SerializedName("social_context")
    private String socialContext;
    @SerializedName("media_count")
    private int mediaCount;
    @SerializedName("following_count")
    private int followingCount;
    @SerializedName("is_business")
    private boolean isBusiness;
    @SerializedName("usertags_count")
    private int usertagsCount;
    @SerializedName("profile_context")
    private String profileContext;
    @SerializedName("geo_media_count")
    private int geoMediaCount;
    @SerializedName("is_unpublished")
    private boolean isUnpublished;

}
