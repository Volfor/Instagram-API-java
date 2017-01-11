package com.github.volfor;

import com.github.volfor.helpers.Json;
import com.github.volfor.models.Experiment;
import com.github.volfor.responses.LoginResponse;
import com.github.volfor.responses.SyncResponse;
import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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

    public JSONObject lastJson;

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
                // .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
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
                        Json data = new Json.Builder()
                                .put("phone_id", generateUUID(true))
                                .put("username", username)
                                .put("guid", uuid)
                                .put("device_id", deviceId)
                                .put("password", password)
                                .put("login_attempt_count", "0")
                                .build();

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
                                        syncFeaturesSync();

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

    private void syncFeaturesSync() throws IOException {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("id", usernameId)
                .put("_csrftoken", token)
                .put("experiments", EXPERIMENTS)
                .build();

        Response<ResponseBody> response = service.sync(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "syncFeatures",
                    "Syncing features failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void syncFeatures(final com.github.volfor.Callback<List<Experiment>> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("id", usernameId)
                .put("_csrftoken", token)
                .put("experiments", EXPERIMENTS)
                .build();

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


    private void autoCompleteUserList() {
        sendRequest("friendships/autocomplete_user_list/", null);
    }

    private void timelineFeed() {
        sendRequest("feed/timeline/", null);
    }

    private void getv2Inbox() {
        sendRequest("direct_v2/inbox/?", null);
    }

    private void getRecentActivity() {
        sendRequest("news/inbox/?", null);
    }

    public void getFeedByTag(String tag) {
        sendRequest("feed/tag/" + tag + "/?rank_token=" + rankToken + "&ranked_content=true&", null);
    }

    public void like(long mediaId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("media_id", mediaId)
                .build();

        sendRequest("media/" + mediaId + "/like/", generateSignature(data));
        System.out.println("Liked!");
    }

    public void getUserFollowers(long usernameId, String maxid) {
        if (maxid != null && !maxid.isEmpty()) {
            sendRequest("friendships/" + usernameId + "/followers/?rank_token=" + rankToken, null);
        } else {
            sendRequest("friendships/" + usernameId + "/followers/?rank_token=" + rankToken + "&max_id=" + maxid, null);
        }
    }

    public void getSelfUserFollowers() {
        getUserFollowers(usernameId, "");
    }

    public void megaphoneLog() {
        String data = String.format("type=feed_aysf&action=seen&reason=&_uuid=%s&device_id=%s&_csrftoken=%s", uuid, deviceId, token);
        sendRequest("megaphone/log/", data);
    }

    public void logout() {
        sendRequest("accounts/logout/", null);
    }

    public void expose() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("id", usernameId)
                .put("_csrftoken", token)
                .put("experiment", "ig_android_profile_contextual_feed")
                .build();

        sendRequest("qe/expose/", generateSignature(data));
    }

    public void uploadPhoto(String filename, String caption, String uploadId) {
        if (uploadId == null) {
            uploadId = String.valueOf(System.currentTimeMillis());
        }

        try {
            File photo = new File(filename);
            RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), photo);

            Json compression = new Json.Builder()
                    .put("lib_name", "jt")
                    .put("lib_version", "1.3.0")
                    .put("quality", "87")
                    .build();

            RequestBody multipart = new MultipartBody.Builder(uuid)
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("_uuid", uuid)
                    .addFormDataPart("_csrftoken", token)
                    .addFormDataPart("upload_id", uploadId)
                    .addFormDataPart("image_compression", compression.toJSONString())
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

        Json edits = new Json.Builder()
                .put("crop_original_size", String.format("[%s.0,%s.0]", w, h))
                .put("crop_center", "[0.0,0.0]")
                .put("crop_zoom", 1.0)
                .build();

        Json extra = new Json.Builder()
                .put("source_width", w)
                .put("source_height", h)
                .build();

        Json data = new Json.Builder()
                .put("_csrftoken", token)
                .put("media_folder", "Instagram")
                .put("source_type", 4)
                .put("_uid", usernameId)
                .put("_uuid", uuid)
                .put("caption", caption)
                .put("upload_id", uploadId)
                .put("device", getDeviceSetting())
                .put("edits", edits)
                .put("extra", extra)
                .build();

        return sendRequest("media/configure/?", generateSignature(data));
    }

    public void editMedia(long mediaId, String captionText) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("caption_text", captionText)
                .build();

        sendRequest("media/" + mediaId + "/edit_media/", generateSignature(data));
    }

    public void removeSelftag(long mediaId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("usertags/" + mediaId + "/remove/", generateSignature(data));
    }

    public void mediaInfo(long mediaId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("media_id", mediaId)
                .build();

        sendRequest("media/" + mediaId + "/info/", generateSignature(data));
    }

    public void comment(long mediaId, String commentText) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("comment_text", commentText)
                .build();

        sendRequest("media/" + mediaId + "/comment/", generateSignature(data));
    }

    public void deleteComment(long mediaId, long commentId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("media/" + mediaId + "/comment/" + commentId + "/delete/", generateSignature(data));
    }

    public void explore() {
        sendRequest("discover/explore/", null);
    }

    public void getProfileData() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("accounts/current_user/?edit=true", generateSignature(data));
    }

    public void editProfile(String url, String phone, String name, String biography, String email, int gender) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("external_url", url)
                .put("phone_number", phone)
                .put("username", username)
                .put("full_name", name)
                .put("biography", biography)
                .put("email", email)
                .put("gender", gender)
                .build();

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
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("media_id", mediaId)
                .build();

        sendRequest("media/" + mediaId + "/unlike/", generateSignature(data));
    }

    public void getMediaComments(long mediaId) {
        sendRequest("media/" + mediaId + "/comments/?", null);
    }

    public void setNameAndPhone(String name, String phone) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("first_name", name)
                .put("phone_number", phone)
                .put("_csrftoken", token)
                .build();

        sendRequest("accounts/set_phone_and_name/", generateSignature(data));
    }

    public void getDirectShare() {
        sendRequest("direct_share/inbox/?", null);
    }

    public void follow(long userId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("user_id", userId)
                .put("_csrftoken", token)
                .build();

        sendRequest("friendships/create/" + userId + "/", generateSignature(data));
    }

    public void unfollow(long userId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("user_id", userId)
                .put("_csrftoken", token)
                .build();

        sendRequest("friendships/destroy/" + userId + "/", generateSignature(data));
    }

    public void block(long userId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("user_id", userId)
                .put("_csrftoken", token)
                .build();

        sendRequest("friendships/block/" + userId + "/", generateSignature(data));
    }

    public void unblock(long userId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("user_id", userId)
                .put("_csrftoken", token)
                .build();

        sendRequest("friendships/unblock/" + userId + "/", generateSignature(data));
    }

    public void userFriendship(long userId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("user_id", userId)
                .put("_csrftoken", token)
                .build();

        sendRequest("friendships/show/" + userId + "/", generateSignature(data));
    }

    public void getLikedMedia(String maxid) {
        sendRequest("feed/liked/?max_id=" + maxid, null);
    }

    public void deleteMedia(long mediaId) {
        String id = String.format("%s_%s", mediaId, usernameId);
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("media_id", id)
                .build();

        sendRequest("media/" + id + "/delete/", generateSignature(data));
    }

    public void changePassword(String newPassword) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("old_password", password)
                .put("new_password1", newPassword)
                .put("new_password2", newPassword)
                .build();

        sendRequest("accounts/change_password/", generateSignature(data));
    }

    public void removeProfilePicture() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("accounts/remove_profile_picture/", generateSignature(data));
    }

    public void setPrivateAccount() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("accounts/set_private/", generateSignature(data));
    }

    public void setPublicAccount() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .build();

        sendRequest("accounts/set_public/", generateSignature(data));
    }

    /* TODO check this features */

    public List<Object> getTotalFollowers(long usernameId) {
        List<Object> followers = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
            getUserFollowers(usernameId, nextMaxId);
            JSONObject temp = lastJson;

            for (Object item : (JSONArray) temp.get("users")) {
                followers.add(item);
            }

            if (!((boolean) temp.get("big_list"))) {
                return followers;
            }

            nextMaxId = (String) temp.get("next_max_id");
        }
    }

    public List<Object> getTotalFollowings(long usernameId) {
        List<Object> followers = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
            getUserFollowings(usernameId, nextMaxId);
            JSONObject temp = lastJson;

            for (Object item : (JSONArray) temp.get("users")) {
                followers.add(item);
            }

            if (!((boolean) temp.get("big_list"))) {
                return followers;
            }

            nextMaxId = (String) temp.get("next_max_id");
        }
    }

    public List<Object> getTotalUserFeed(long usernameId, String minTimestamp) {
        List<Object> userFeed = new ArrayList<>();
        String nextMaxId = "";

        while (true) {
            getUserFeed(usernameId, nextMaxId, minTimestamp);
            JSONObject temp = lastJson;

            for (Object item : (JSONArray) temp.get("items")) {
                userFeed.add(item);
            }

            if (!((boolean) temp.get("more_available"))) {
                return userFeed;
            }

            nextMaxId = (String) temp.get("next_max_id");
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
            JSONObject temp = lastJson;
            nextId = (String) temp.get("next_max_id");

            for (Object item : (JSONArray) temp.get("items")) {
                likedItems.add(item);
            }
        }

        return likedItems;
    }

    public void syncFromAdressBook(JSONObject contacts) {
        sendRequest("address_book/link/?include=extra_display_name,thumbnails", "contacts=" + contacts.toJSONString());
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
                lastJson = (JSONObject) new JSONParser().parse(body);

                System.out.println(response.code() + ": " + body);
                return true;
            } else {
                System.err.println(response.code() + ": " + body);

                if (!isLoggedIn) {
                    throw new NotLoggedInException();
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return false;
    }

}
