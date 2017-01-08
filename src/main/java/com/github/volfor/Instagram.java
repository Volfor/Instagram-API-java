package com.github.volfor;

import com.github.volfor.helpers.Json;
import okhttp3.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.github.volfor.Constants.*;
import static com.github.volfor.Utils.*;

public class Instagram {

    private String username;
    private String password;
    private String uuid;
    private String deviceId;
    private long usernameId;
    private String rankToken;
    private String token;

    private boolean isLoggedIn = false;

    private List<Cookie> cookies = new ArrayList<>();
    private String loginSessionCookies;
    public JSONObject lastJson;

    private static OkHttpClient httpClient;

    static {
        httpClient = new OkHttpClient.Builder()
//                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
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
                .build();
    }

    public Instagram(String username, String password) {
        deviceId = generateDeviceId(getHexdigest(username, password));
        setUser(username, password);
    }

    public void login(boolean force) {
        if (!isLoggedIn || force) {
            // if you need proxy make something like this:
            // Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("host", "post"));
            // httpClient = httpClient.newBuilder().proxy(proxy).build();

            if (sendRequest("si/fetch_headers/?challenge_type=signup&guid=" + generateUUID(false), null)) {
                Json data = new Json.Builder()
                        .put("phone_id", generateUUID(true))
                        .put("_csrftoken", getCookie(cookies, "csrftoken").value())
                        .put("username", username)
                        .put("guid", uuid)
                        .put("device_id", deviceId)
                        .put("password", password)
                        .put("login_attempt_count", "0")
                        .build();

                if (sendRequest("accounts/login/", generateSignature(data))) {
                    isLoggedIn = true;
                    loginSessionCookies = getCookieString(cookies);
                    usernameId = (long) ((JSONObject) lastJson.get("logged_in_user")).get("pk");
                    rankToken = String.format("%s_%s", usernameId, uuid);
                    token = getCookie(cookies, "csrftoken").value();

                    syncFeatures();
                    autoCompleteUserList();
                    timelineFeed();
                    getv2Inbox();
                    getRecentActivity();

                    System.out.println("Login success!\n");
                }
            }
        }
    }

    private void syncFeatures() {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("id", usernameId)
                .put("_csrftoken", token)
                .put("experiments", EXPERIMENTS)
                .build();

        sendRequest("qe/sync/", generateSignature(data));
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

            request = addSessionCookies(request);

            Response response = httpClient.newCall(request).execute();
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

    private Request addSessionCookies(Request request) {
        if (loginSessionCookies != null && !loginSessionCookies.isEmpty()) {
            request = request.newBuilder()
                    .addHeader("Cookie", loginSessionCookies)
                    .build();
        }

        return request;
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

    /* TODO check this features */

    public void deleteMedia(long mediaId) {
        Json data = new Json.Builder()
                .put("_uuid", uuid)
                .put("_uid", usernameId)
                .put("_csrftoken", token)
                .put("media_id", mediaId)
                .build();

        sendRequest("media/" + mediaId + "/delete/", generateSignature(data)); // returns 400: {"message": "invalid parameters", "status": "fail"}
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

        request = addSessionCookies(request);

        if (post != null) { //POST
            request = request.newBuilder()
                    .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"), post))
                    .build();
        }

        try {
            Response response = httpClient.newCall(request).execute();
            String body = response.body().string();
            if (response.code() == 200) {
                cookies = httpClient.cookieJar().loadForRequest(HttpUrl.parse(endpoint));
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
