package com.github.volfor.responses;

import com.github.volfor.models.ProfileData;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ProfileDataResponse extends Response {

    @SerializedName("user")
    private ProfileData profileData;

}
