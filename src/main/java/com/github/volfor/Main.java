package com.github.volfor;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Main {

    public static void main(String[] args) {
        Instagram instagram = new Instagram("username", "password");
        instagram.login(false);

        instagram.getFeedByTag("cat");
        JSONObject firstMedia = (JSONObject) ((JSONArray) instagram.lastJson.get("ranked_items")).get(0);

        long mediaId = (long) firstMedia.get("pk");
        instagram.like(mediaId);

        long usernameId = (long) ((JSONObject) firstMedia.get("user")).get("pk");
        instagram.getUserFollowers(usernameId, null);
    }

}
