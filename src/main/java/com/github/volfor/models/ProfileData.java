package com.github.volfor.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ProfileData {

    private String username;
    private String biography;
    private String email;
    private long pk;
    @SerializedName("phone_number")
    private String phoneNumber;
    @SerializedName("is_private")
    private boolean isPrivate;
    @SerializedName("hd_profile_pic_url_info")
    private Picture hdProfilePicUrlInfo;
    @SerializedName("profile_pic_id")
    private String profilePicId;
    @SerializedName("has_anonymous_profile_picture")
    private boolean hasAnonymousProfilePicture;
    @SerializedName("external_url")
    private String externalUrl;
    @SerializedName("profile_pic_url")
    private String profilePicUrl;
    @SerializedName("is_verified")
    private boolean isVerified;

    private String birthday; // ?
    private int gender;
    @SerializedName("full_name")
    private String fullName;
    @SerializedName("show_conversion_edit_entry")
    private boolean showConversionEditEntry;

}
