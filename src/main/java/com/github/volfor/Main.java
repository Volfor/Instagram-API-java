package com.github.volfor;

public class Main {

    public static void main(String[] args) {
        Instagram instagram = new Instagram("login", "password");
        instagram.login(false);
    }

}
