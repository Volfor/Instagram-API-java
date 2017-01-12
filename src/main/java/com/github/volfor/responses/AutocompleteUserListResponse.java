package com.github.volfor.responses;

import com.github.volfor.models.User;
import lombok.Data;

import java.util.List;

@Data
public class AutocompleteUserListResponse extends Response {

    private long expires;
    List<User> users;

}
