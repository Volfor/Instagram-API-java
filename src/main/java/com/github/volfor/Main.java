package com.github.volfor;

public class Main {

    public static void main(String[] args) {
        Instagram instagram = new Instagram("username", "password");
        instagram.login(false, new Callback<Session>() {
            @Override
            public void onSuccess(Session session) {
                System.out.printf("Welcome back, %s!", session.getLoggedInUser().getUsername());
            }

            @Override
            public void onFailure(Throwable t) {
                System.out.printf("Login failed with message: %s", t.getMessage());
            }
        });

//        instagram.getFeedByTag("cat");
//        JSONObject firstMedia = (JSONObject) ((JSONArray) instagram.lastJson.get("ranked_items")).get(0);
//
//        long mediaId = (long) firstMedia.get("pk");
//        instagram.like(mediaId);
//
//        long usernameId = (long) ((JSONObject) firstMedia.get("user")).get("pk");
//        instagram.getUserFollowers(usernameId, null);
    }

}
