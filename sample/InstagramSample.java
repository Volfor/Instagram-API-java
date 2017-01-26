package com.github.volfor.example;

import com.github.volfor.Callback;
import com.github.volfor.Instagram;
import com.github.volfor.Session;
import com.github.volfor.responses.FollowersResponse;
import com.github.volfor.responses.TagFeedResponse;

public class InstagramSample {

    interface OnLoggedInCallback {
        void onLoggedIn();
    }

    public static void main(String[] args) {
        final Instagram instagram = new Instagram();

        final OnLoggedInCallback callback = new OnLoggedInCallback() {
            @Override
            public void onLoggedIn() {
                instagram.getFeedByTag("cat", new Callback<TagFeedResponse>() {
                    @Override
                    public void onSuccess(TagFeedResponse response) {
                        long mediaId = response.getItems().get(0).getPk();
                        instagram.like(mediaId);

                        final String username = response.getItems().get(0).getUser().getUsername();
                        long usernameId = response.getItems().get(0).getUser().getPk();
                        instagram.getUserFollowers(usernameId, new Callback<FollowersResponse>() {
                            @Override
                            public void onSuccess(FollowersResponse response) {
                                int followersCount = response.getUsers().size();
                                System.out.println(username + " has " + followersCount + " followers");
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                System.err.println("Getting user followers failed: " + t.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        System.err.println("Getting feed by tag failed: " + t.getMessage());
                    }
                });
            }
        };

        instagram.login("username", "password", false, new Callback<Session>() {
            @Override
            public void onSuccess(Session session) {
                System.out.println("Welcome back, " + session.getLoggedInUser().getUsername());
                callback.onLoggedIn();
            }

            @Override
            public void onFailure(Throwable t) {
                System.out.println("Login failed with message: " + t.getMessage());
            }
        });
    }

}
