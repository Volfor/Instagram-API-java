package com.github.volfor.responses;

import com.github.volfor.models.User;
import lombok.Data;

@Data
public class UserInfoResponse extends Response {

    private User user;

}