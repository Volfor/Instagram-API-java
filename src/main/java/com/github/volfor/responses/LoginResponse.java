package com.github.volfor.responses;

import com.github.volfor.models.User;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class LoginResponse extends Response {

    @SerializedName("logged_in_user")
    private User loggedInUser;

}
