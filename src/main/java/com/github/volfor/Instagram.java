package com.github.volfor;

import com.github.volfor.models.Experiment;
import com.github.volfor.responses.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.volfor.Constants.*;
import static com.github.volfor.Utils.*;

public class Instagram {

    private final static Logger LOG = Logger.getLogger(Instagram.class.getName());

    private ApiService service;
    private Retrofit retrofit;

    private String username;
    private String password;
    private String uuid;
    private String deviceId;
    private long usernameId;
    private String rankToken;
    private String token;
    private boolean isLoggedIn;

    public JsonObject lastJson;

    private Session session = new Session();

    public Instagram(String username, String password) {
        setup(username, password, null);
    }

    public Instagram(String username, String password, Proxy proxy) {
        // proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("host", "port"));
        setup(username, password, proxy);
    }

    private void setup(String username, String password, Proxy proxy) {
        this.username = username;
        this.password = password;

        this.uuid = generateUUID(true);
        this.deviceId = generateDeviceId(getHexdigest(username, password));

        OkHttpClient httpClient = new OkHttpClient.Builder()
//                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(new AddCookiesInterceptor(session.getCookies()))
                .cookieJar(new CookieJar() {
                    private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url);
                        return cookies != null ? cookies : new ArrayList<Cookie>();
                    }
                })
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder()
                                .header("Connection", "close")
                                .header("Accept", "*/*")
                                .header("Content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                                .header("Cookie2", "$Version=1")
                                .header("Accept-Language", "en-US")
                                .header("User-Agent", USER_AGENT)
                                .build();

                        return chain.proceed(request);
                    }
                })
                .build();

        if (proxy != null) httpClient = httpClient.newBuilder().proxy(proxy).build();

        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build();

        service = retrofit.create(ApiService.class);
    }

    public void login(boolean force, final com.github.volfor.Callback<Session> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        if (!isLoggedIn || force) {
            service.fetchHeaders(generateUUID(false)).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                    if (!response.isSuccessful()) {
                        callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("phone_id", generateUUID(true));
                        data.addProperty("username", username);
                        data.addProperty("guid", uuid);
                        data.addProperty("device_id", deviceId);
                        data.addProperty("password", password);
                        data.addProperty("login_attempt_count", "0");

                        service.login(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<LoginResponse>() {
                            @Override
                            public void onResponse(Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                                if (!response.isSuccessful()) {
                                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                                } else {
                                    isLoggedIn = true;

                                    session.setCookies(((OkHttpClient) retrofit.callFactory()).cookieJar()
                                            .loadForRequest(call.request().url()));

                                    session.setLoggedInUser(response.body().getLoggedInUser());

                                    usernameId = session.getLoggedInUser().getPk();
                                    token = session.getToken();
                                    rankToken = String.format("%s_%s", usernameId, uuid);

                                    try {
                                        syncFeatures();
                                        autocompleteUserList();
                                        timelineFeed();
                                        getv2Inbox();
                                        getRecentActivity();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    callback.onSuccess(session);
                                }
                            }

                            @Override
                            public void onFailure(Call<LoginResponse> call, Throwable t) {
                                callback.onFailure(t);
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    callback.onFailure(t);
                }
            });
        }
    }

    private void syncFeatures() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("id", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("experiments", EXPERIMENTS);

        Response<ResponseBody> response = service.sync(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "syncFeatures",
                    "Syncing features failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void syncFeatures(final com.github.volfor.Callback<List<Experiment>> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("id", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("experiments", EXPERIMENTS);

        service.sync(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(new SyncResponse(response.body()).getExperiments());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private void autocompleteUserList() throws IOException {
        Response response = service.autocompleteUserList().execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "autoCompleteUserList",
                    "Getting autocomplete user list failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void autocompleteUserList(final com.github.volfor.Callback<AutocompleteUserListResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.autocompleteUserList().enqueue(new Callback<AutocompleteUserListResponse>() {
            @Override
            public void onResponse(Call<AutocompleteUserListResponse> call, Response<AutocompleteUserListResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<AutocompleteUserListResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private void timelineFeed() throws IOException {
        Response response = service.timeline().execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "timelineFeed",
                    "Getting timeline feed failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void timelineFeed(final com.github.volfor.Callback<TimelineFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.timeline().enqueue(new Callback<TimelineFeedResponse>() {
            @Override
            public void onResponse(Call<TimelineFeedResponse> call, Response<TimelineFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<TimelineFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private void getv2Inbox() throws IOException {
        Response response = service.directv2Inbox().execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "getv2Inbox",
                    "Getting direct v2 inbox failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void getv2Inbox(final com.github.volfor.Callback<V2InboxResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.directv2Inbox().enqueue(new Callback<V2InboxResponse>() {
            @Override
            public void onResponse(Call<V2InboxResponse> call, Response<V2InboxResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<V2InboxResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private void getRecentActivity() throws IOException {
        Response response = service.newsInbox().execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "getRecentActivity",
                    "Getting recent activity failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void getRecentActivity(final com.github.volfor.Callback<RecentActivityResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.newsInbox().enqueue(new Callback<RecentActivityResponse>() {
            @Override
            public void onResponse(Call<RecentActivityResponse> call, Response<RecentActivityResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<RecentActivityResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void getFeedByTag(String tag, final com.github.volfor.Callback<TagFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.tagFeed(tag, rankToken).enqueue(new Callback<TagFeedResponse>() {
            @Override
            public void onResponse(Call<TagFeedResponse> call, Response<TagFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<TagFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void like(long mediaId, final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("media_id", mediaId);

        service.like(mediaId, SIG_KEY_VERSION, generateSignature(data)).enqueue(
                new Callback<com.github.volfor.responses.Response>() {
                    @Override
                    public void onResponse(Call<com.github.volfor.responses.Response> call,
                                           Response<com.github.volfor.responses.Response> response) {

                        if (response.isSuccessful()) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                        }
                    }

                    @Override
                    public void onFailure(Call<com.github.volfor.responses.Response> call, Throwable t) {
                        callback.onFailure(t);
                    }
                });
    }

    public void getUserFollowers(long userId, String maxId, final com.github.volfor.Callback<FollowersResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.followers(userId, rankToken, maxId).enqueue(new Callback<FollowersResponse>() {
            @Override
            public void onResponse(Call<FollowersResponse> call, Response<FollowersResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FollowersResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void getUserFollowers(long userId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(userId, "", callback);
    }

    public void getSelfFollowers(com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(usernameId, callback);
    }

    public void getSelfFollowers(String maxId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(usernameId, maxId, callback);
    }

    public void megaphoneLog() {
        String data = String.format("type=feed_aysf&action=seen&reason=&_uuid=%s&device_id=%s&_csrftoken=%s", uuid, deviceId, token);
        sendRequest("megaphone/log/", data);
    }

    public void logout() {
        sendRequest("accounts/logout/", null);
    }

    public void expose() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("id", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("experiment", "ig_android_profile_contextual_feed");

        sendRequest("qe/expose/", generateSignature(data));
    }

    public void uploadPhoto(String filename, String caption, String uploadId) {
        if (uploadId == null) {
            uploadId = String.valueOf(System.currentTimeMillis());
        }

        try {
            File photo = new File(filename);
            RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), photo);

            JsonObject compression = new JsonObject();
            compression.addProperty("lib_name", "jt");
            compression.addProperty("lib_version", "1.3.0");
            compression.addProperty("quality", "87");

            RequestBody multipart = new MultipartBody.Builder(uuid)
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("_uuid", uuid)
                    .addFormDataPart("_csrftoken", token)
                    .addFormDataPart("upload_id", uploadId)
                    .addFormDataPart("image_compression", compression.toString())
                    .addFormDataPart("photo", "pending_media_" + uploadId + ".jpg", body)
                    .build();

            Request request = new Request.Builder()
                    .header("X-IG-Capabilities", "3Q4=")
                    .header("X-IG-Connection-Type", "WIFI")
                    .header("Cookie2", "$Version=1")
                    .header("Accept-Language", "en-US")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Content-type", multipart.contentType().toString())
                    .header("Connection", "close")
                    .header("User-Agent", USER_AGENT)
                    .url(API_URL + "upload/photo/")
                    .post(multipart)
                    .build();

            okhttp3.Response response = retrofit.callFactory().newCall(request).execute();
            if (response.code() == 200) {
                if (configure(uploadId, filename, caption)) {
                    expose();
                }
            }
            System.out.println(response.code() + ": " + response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean configure(String uploadId, String filename, String caption) throws IOException {
        BufferedImage bimage = ImageIO.read(new File(filename));
        int w = bimage.getWidth();
        int h = bimage.getHeight();

        JsonObject edits = new JsonObject();
        edits.addProperty("crop_original_size", String.format("[%s.0,%s.0]", w, h));
        edits.addProperty("crop_center", "[0.0,0.0]");
        edits.addProperty("crop_zoom", 1.0);

        JsonObject extra = new JsonObject();
        extra.addProperty("source_width", w);
        extra.addProperty("source_height", h);

        JsonObject data = new JsonObject();
        data.addProperty("_csrftoken", token);
        data.addProperty("media_folder", "Instagram");
        data.addProperty("source_type", 4);
        data.addProperty("_uid", usernameId);
        data.addProperty("_uuid", uuid);
        data.addProperty("caption", caption);
        data.addProperty("upload_id", uploadId);
        data.addProperty("device", getDeviceSetting());
        data.add("edits", edits);
        data.add("extra", extra);

        return sendRequest("media/configure/?", generateSignature(data));
    }

    public void editMedia(long mediaId, String captionText) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("caption_text", captionText);

        sendRequest("media/" + mediaId + "/edit_media/", generateSignature(data));
    }

    public void removeSelftag(long mediaId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("usertags/" + mediaId + "/remove/", generateSignature(data));
    }

    public void mediaInfo(long mediaId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("media_id", mediaId);

        sendRequest("media/" + mediaId + "/info/", generateSignature(data));
    }

    public void comment(long mediaId, String commentText) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("comment_text", commentText);

        sendRequest("media/" + mediaId + "/comment/", generateSignature(data));
    }

    public void deleteComment(long mediaId, long commentId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("media/" + mediaId + "/comment/" + commentId + "/delete/", generateSignature(data));
    }

    public void explore() {
        sendRequest("discover/explore/", null);
    }

    public void getProfileData() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("accounts/current_user/?edit=true", generateSignature(data));
    }

    public void editProfile(String url, String phone, String name, String biography, String email, int gender) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("external_url", url);
        data.addProperty("phone_number", phone);
        data.addProperty("username", username);
        data.addProperty("full_name", name);
        data.addProperty("biography", biography);
        data.addProperty("email", email);
        data.addProperty("gender", gender);

        sendRequest("accounts/edit_profile/", generateSignature(data));
    }

    public void getUsernameInfo(long usernameId) {
        sendRequest("users/" + usernameId + "/info/", null);
    }

    public void getSelfUsernameInfo() {
        getUsernameInfo(usernameId);
    }

    public void getFollowingRecentActivity() {
        sendRequest("news/?", null);
    }

    public void getUserTags(long usernameId) {
        sendRequest("usertags/" + usernameId + "/feed/?rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void getSelfUserTags() {
        getUserTags(usernameId);
    }

    public void getMediaLikers(long mediaId) {
        sendRequest("media/" + mediaId + "/likers/?", null);
    }

    public void getGeoMedia(long usernameId) {
        sendRequest("maps/user/" + usernameId + "/", null);
    }

    public void getSelfGeoMedia() {
        getGeoMedia(usernameId);
    }

    public void fbUserSearch(String query) {
        sendRequest("fbsearch/topsearch/?context=blended&query=" + query + "&rank_token=" + rankToken, null);
    }

    public void searchUsers(String query) {
        sendRequest("users/search/?ig_sig_key_version=" + SIG_KEY_VERSION + "&is_typeahead=true&query="
                + query + "&rank_token=" + rankToken, null);
    }

    public void searchUsername(String usernameName) {
        sendRequest("users/" + usernameName + "/usernameinfo/", null);
    }

    public void searchTags(String query) {
        sendRequest("tags/search/?is_typeahead=true&q=" + query + "&rank_token=" + rankToken, null);
    }

    public void getTimeline() {
        sendRequest("feed/timeline/?rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void getUserFeed(long usernameId, String maxid, String minTimestamp) {
        sendRequest("feed/user/" + usernameId + "/?max_id=" + maxid + "&min_timestamp=" + minTimestamp
                + "&rank_token=" + rankToken + "&ranked_content=true", null);
    }

    public void getSelfUserFeed(String maxid, String minTimestamp) {
        getUserFeed(usernameId, maxid, minTimestamp);
    }

    public void getHashtagFeed(String hashtag, String maxid) {
        sendRequest("feed/tag/" + hashtag + "/?max_id=" + maxid + "&rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void searchLocation(String query) {
        sendRequest("fbsearch/places/?rank_token=" + rankToken + "&query=" + query, null);
    }

    public void getLocationFeed(long locationId, String maxid) {
        sendRequest("feed/location/" + locationId + "/?max_id=" + maxid + "&rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void getPopularFeed() {
        sendRequest("feed/popular/?people_teaser_supported=1&rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void getUserFollowings(long usernameId, String maxid) {
        sendRequest("friendships/" + usernameId + "/following/?max_id=" + maxid
                + "&ig_sig_key_version=" + SIG_KEY_VERSION + "&rank_token=" + rankToken, null);
    }

    public void getSelfUsersFollowing() {
        getUserFollowings(usernameId, "");
    }

    public void unlike(long mediaId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("media_id", mediaId);

        sendRequest("media/" + mediaId + "/unlike/", generateSignature(data));
    }

    public void getMediaComments(long mediaId) {
        sendRequest("media/" + mediaId + "/comments/?", null);
    }

    public void setNameAndPhone(String name, String phone) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("first_name", name);
        data.addProperty("phone_number", phone);
        data.addProperty("_csrftoken", token);

        sendRequest("accounts/set_phone_and_name/", generateSignature(data));
    }

    public void getDirectShare() {
        sendRequest("direct_share/inbox/?", null);
    }

    public void follow(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", token);

        sendRequest("friendships/create/" + userId + "/", generateSignature(data));
    }

    public void unfollow(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", token);

        sendRequest("friendships/destroy/" + userId + "/", generateSignature(data));
    }

    public void block(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", token);

        sendRequest("friendships/block/" + userId + "/", generateSignature(data));
    }

    public void unblock(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", token);

        sendRequest("friendships/unblock/" + userId + "/", generateSignature(data));
    }

    public void userFriendship(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", token);

        sendRequest("friendships/show/" + userId + "/", generateSignature(data));
    }

    public void getLikedMedia(String maxid) {
        sendRequest("feed/liked/?max_id=" + maxid, null);
    }

    public void deleteMedia(long mediaId) {
        String id = String.format("%s_%s", mediaId, usernameId);

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("media_id", id);

        sendRequest("media/" + id + "/delete/", generateSignature(data));
    }

    public void changePassword(String newPassword) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);
        data.addProperty("old_password", password);
        data.addProperty("new_password1", newPassword);
        data.addProperty("new_password2", newPassword);

        sendRequest("accounts/change_password/", generateSignature(data));
    }

    public void removeProfilePicture() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("accounts/remove_profile_picture/", generateSignature(data));
    }

    public void setPrivateAccount() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("accounts/set_private/", generateSignature(data));
    }

    public void setPublicAccount() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", uuid);
        data.addProperty("_uid", usernameId);
        data.addProperty("_csrftoken", token);

        sendRequest("accounts/set_public/", generateSignature(data));
    }

    /* TODO check this features */

    public List<Object> getTotalFollowers(long usernameId) {
        List<Object> followers = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
//            getUserFollowers(usernameId, nextMaxId);
            JsonObject temp = lastJson;

            for (Object item : temp.getAsJsonArray("users")) {
                followers.add(item);
            }

            if (!temp.get("big_list").getAsBoolean()) {
                return followers;
            }

            nextMaxId = temp.get("next_max_id").getAsString();
        }
    }

    public List<Object> getTotalFollowings(long usernameId) {
        List<Object> followers = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
            getUserFollowings(usernameId, nextMaxId);
            JsonObject temp = lastJson;

            for (Object item : temp.getAsJsonArray("users")) {
                followers.add(item);
            }

            if (!temp.get("big_list").getAsBoolean()) {
                return followers;
            }

            nextMaxId = temp.get("next_max_id").getAsString();
        }
    }

    public List<Object> getTotalUserFeed(long usernameId, String minTimestamp) {
        List<Object> userFeed = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
            getUserFeed(usernameId, nextMaxId, minTimestamp);
            JsonObject temp = lastJson;

            for (Object item : temp.getAsJsonArray("items")) {
                userFeed.add(item);
            }

            if (!temp.get("more_available").getAsBoolean()) {
                return userFeed;
            }

            nextMaxId = temp.get("next_max_id").getAsString();
        }
    }

    public List<Object> getTotalSelfUserFeed(String minTimestamp) {
        return getTotalUserFeed(usernameId, minTimestamp);
    }

    public List<Object> getTotalSelfFollowers() {
        return getTotalFollowers(usernameId);
    }

    public List<Object> getTotalSelfFollowings() {
        return getTotalFollowings(usernameId);
    }

    public List<Object> getTotalLikedMedia(int scanRate) {
        scanRate = 1;
        String nextId = "";
        List<Object> likedItems = new ArrayList<>();

        for (int i = 0; i < scanRate; i++) {
            getLikedMedia(nextId);
            JsonObject temp = lastJson;
            nextId = temp.get("next_max_id").getAsString();

            for (Object item : temp.getAsJsonArray("items")) {
                likedItems.add(item);
            }
        }

        return likedItems;
    }

    public void syncFromAdressBook(JsonObject contacts) {
        sendRequest("address_book/link/?include=extra_display_name,thumbnails", "contacts=" + contacts.toString());
    }

    private void setUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.uuid = generateUUID(true);
    }

    private boolean sendRequest(String endpoint, String post) {
        endpoint = API_URL + endpoint;

        Request request = new Request.Builder()
                .header("Connection", "close")
                .header("Accept", "*/*")
                .header("Content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Cookie2", "$Version=1")
                .header("Accept-Language", "en-US")
                .header("User-Agent", USER_AGENT)
                .url(endpoint)
                .build();

        if (post != null) { //POST
            request = request.newBuilder()
                    .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"), post))
                    .build();
        }

        try {
            okhttp3.Response response = retrofit.callFactory().newCall(request).execute();
            String body = response.body().string();
            if (response.code() == 200) {
                lastJson = (JsonObject) new JsonParser().parse(body);

                System.out.println(response.code() + ": " + body);
                return true;
            } else {
                System.err.println(response.code() + ": " + body);

                if (!isLoggedIn) {
                    throw new NotLoggedInException();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

}
