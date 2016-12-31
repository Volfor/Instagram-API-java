package com.github.volfor;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Main {

    public static void main(String[] args) {
        Instagram instagram = new Instagram("username", "password");
        instagram.login(false);

        instagram.getFeedByTag("cat");
        JSONObject media_items = instagram.lastJson;

        long mediaId = (long) ((JSONObject) ((JSONArray) media_items.get("ranked_items")).get(0)).get("pk");
        instagram.like(mediaId);
    }

}
