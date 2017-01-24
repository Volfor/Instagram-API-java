package com.github.volfor.models;

import lombok.Data;

@Data
public class Profile {

    public class Gender {
        public static final int MALE = 1;
        public static final int FEMALE = 2;
        public static final int NONE = 3;
    }

    private String name;
    private String url;
    private String phone;
    private String biography;
    private String email;
    private Integer gender;

    public Profile(String email) {
        this.email = email;
    }

    public Profile(String phone, String email) {
        this.phone = phone;
        this.email = email;
    }

}
