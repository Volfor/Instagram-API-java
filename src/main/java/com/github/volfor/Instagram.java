package com.github.volfor;

import com.github.volfor.models.*;
import com.github.volfor.responses.*;
import com.google.gson.JsonObject;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
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
import java.util.Map;
import java.util.logging.Logger;

import static com.github.volfor.Constants.*;
import static com.github.volfor.Utils.*;

import retrofit2.Response;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Instagram {

    private final static Logger LOG = Logger.getLogger(Instagram.class.getName());

    private ApiService service;
    private Retrofit retrofit;
    private OkHttpClient httpClient;

    private Session session = new Session();

    public Instagram() {
        setup();
    }

    private void setup() {
        httpClient = new OkHttpClient.Builder()
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

        setupRetrofit(httpClient);
    }

    private void setupRetrofit(OkHttpClient httpClient) {
        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build();

        service = retrofit.create(ApiService.class);
    }

    // proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("host", "port"));
    public void setProxy(Proxy proxy) {
        httpClient = httpClient.newBuilder().proxy(proxy).build();
        setupRetrofit(httpClient);
    }

    public void setHttpLogging(boolean enabled) {
        OkHttpClient.Builder builder = httpClient.newBuilder();
        if (enabled) {
            builder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        } else {
            builder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE));
        }
        httpClient = builder.build();
        setupRetrofit(httpClient);
    }

    public void login(String username, String password) throws IOException {
        session.setDeviceId(generateDeviceId(getHexdigest(username, password)));

        try {
            syncFeatures();
            service.fetchHeaders(generateUUID(false)).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        JsonObject data = new JsonObject();
        data.addProperty("phone_id", generateUUID(true));
        data.addProperty("username", username);
        data.addProperty("guid", session.getUuid());
        data.addProperty("adid", session.getAdid());
        data.addProperty("device_id", session.getDeviceId());
        data.addProperty("password", password);
        data.addProperty("login_attempt_count", 0);

        Call<LoginResponse> login = service.login(SIG_KEY_VERSION, generateSignature(data));
        Response<LoginResponse> response = login.execute();

        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }

        session.setCookies(((OkHttpClient) retrofit.callFactory()).cookieJar()
                .loadForRequest(login.request().url()));

        session.setLoggedInUser(response.body().getLoggedInUser());
    }

    public void login(final String username, final String password, final com.github.volfor.Callback<Session> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        session.setDeviceId(generateDeviceId(getHexdigest(username, password)));
        try {
            syncFeatures();
        } catch (IOException e) {
            e.printStackTrace();
        }

        service.fetchHeaders(generateUUID(false)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                } else {
                    JsonObject data = new JsonObject();
                    data.addProperty("phone_id", generateUUID(true));
                    data.addProperty("username", username);
                    data.addProperty("guid", session.getUuid());
                    data.addProperty("device_id", session.getDeviceId());
                    data.addProperty("password", password);
                    data.addProperty("login_attempt_count", "0");

                    service.login(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<LoginResponse>() {
                        @Override
                        public void onResponse(Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                            if (!response.isSuccessful()) {
                                callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                            } else {
                                session.setCookies(((OkHttpClient) retrofit.callFactory()).cookieJar()
                                        .loadForRequest(call.request().url()));

                                session.setLoggedInUser(response.body().getLoggedInUser());

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

    public void syncFeatures() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("experiments", EXPERIMENTS);

        service.sync(SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void syncFeatures(final com.github.volfor.Callback<List<Experiment>> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
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

    public List<User> autocompleteUserList() throws IOException {
        Response<AutocompleteUserListResponse> response = service.autocompleteUserList().execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getUsers();
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

    public TimelineFeedResponse timelineFeed() throws IOException {
        Response<TimelineFeedResponse> response = service.timeline(session.getRankToken(), System.currentTimeMillis()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void timelineFeed(final com.github.volfor.Callback<TimelineFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.timeline(session.getRankToken(), System.currentTimeMillis()).enqueue(new Callback<TimelineFeedResponse>() {
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

    public V2InboxResponse getv2Inbox() throws IOException {
        Response<V2InboxResponse> response = service.directv2Inbox(System.currentTimeMillis()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getv2Inbox(final com.github.volfor.Callback<V2InboxResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.directv2Inbox(System.currentTimeMillis()).enqueue(new Callback<V2InboxResponse>() {
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

    public RecentActivityResponse getRecentActivity() throws IOException {
        Response<RecentActivityResponse> response = service.newsInbox(System.currentTimeMillis()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getRecentActivity(final com.github.volfor.Callback<RecentActivityResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.newsInbox(System.currentTimeMillis()).enqueue(new Callback<RecentActivityResponse>() {
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

    public TagFeedResponse getFeedByTag(String tag) throws IOException {
        Response<TagFeedResponse> response = service.tagFeed(tag, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getFeedByTag(String tag, final com.github.volfor.Callback<TagFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.tagFeed(tag, session.getRankToken()).enqueue(new Callback<TagFeedResponse>() {
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

    public void like(final long mediaId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", mediaId);

        service.like(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void like(long mediaId, final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
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

    public FollowersResponse getUserFollowers(long userId, String maxId) throws IOException {
        Response<FollowersResponse> response = service.followers(userId, session.getRankToken(), maxId, System.currentTimeMillis()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getUserFollowers(long userId, String maxId, final com.github.volfor.Callback<FollowersResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.followers(userId, session.getRankToken(), maxId, System.currentTimeMillis()).enqueue(new Callback<FollowersResponse>() {
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

    public FollowersResponse getUserFollowers(long userId) throws IOException {
        return getUserFollowers(userId, "");
    }

    public void getUserFollowers(long userId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(userId, "", callback);
    }

    public FollowersResponse getSelfFollowers() throws IOException {
        return getUserFollowers(session.getUsernameId());
    }

    public void getSelfFollowers(com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(session.getUsernameId(), callback);
    }

    public FollowersResponse getSelfFollowers(String maxId) throws IOException {
        return getUserFollowers(session.getUsernameId(), maxId);
    }

    public void getSelfFollowers(String maxId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(session.getUsernameId(), maxId, callback);
    }

    public boolean megaphoneLog() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("type", "feed_aysf");
        params.put("action", "seen");
        params.put("_uuid", session.getUuid());
        params.put("device_id", session.getDeviceId());
        params.put("_csrftoken", session.getToken());

        Response<MegaphoneLogResponse> response = service.megaphone(params, System.currentTimeMillis()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().isSuccess();
    }

    public void megaphoneLog(final com.github.volfor.Callback<MegaphoneLogResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        Map<String, String> params = new HashMap<>();
        params.put("type", "feed_aysf");
        params.put("action", "seen");
        params.put("_uuid", session.getUuid());
        params.put("device_id", session.getDeviceId());
        params.put("_csrftoken", session.getToken());

        service.megaphone(params, System.currentTimeMillis()).enqueue(new Callback<MegaphoneLogResponse>() {
            @Override
            public void onResponse(Call<MegaphoneLogResponse> call, Response<MegaphoneLogResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MegaphoneLogResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void logout() throws IOException {
        Response response = service.logout().execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        session.close();
    }

    public void logout(final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.logout().enqueue(new Callback<com.github.volfor.responses.Response>() {
            @Override
            public void onResponse(Call<com.github.volfor.responses.Response> call,
                                   Response<com.github.volfor.responses.Response> response) {

                if (response.isSuccessful()) {
                    session.close();
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

    public void expose() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("experiment", "ig_android_profile_contextual_feed");

        service.expose(SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void expose(final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("experiment", "ig_android_profile_contextual_feed");

        service.expose(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<com.github.volfor.responses.Response>() {
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

    public Media uploadPhoto(String filename, String caption) throws IOException {
        String uploadId = String.valueOf(System.currentTimeMillis());

        JsonObject compression = new JsonObject();
        compression.addProperty("lib_name", "jt");
        compression.addProperty("lib_version", "1.3.0");
        compression.addProperty("quality", "87");

        File photo = new File(filename);
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), photo);

        RequestBody multipart = new MultipartBody.Builder(session.getUuid())
                .setType(MultipartBody.FORM)
                .addFormDataPart("_uuid", session.getUuid())
                .addFormDataPart("_csrftoken", session.getToken())
                .addFormDataPart("upload_id", uploadId)
                .addFormDataPart("image_compression", compression.toString())
                .addFormDataPart("photo", "pending_media_" + uploadId + ".jpg", body)
                .build();

        Response response = service.uploadPhoto(multipart).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }

        return configure(uploadId, filename, caption);
    }

    public void uploadPhoto(final String filename, final String caption, final com.github.volfor.Callback<UploadPhotoResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        final String uploadId = String.valueOf(System.currentTimeMillis());

        JsonObject compression = new JsonObject();
        compression.addProperty("lib_name", "jt");
        compression.addProperty("lib_version", "1.3.0");
        compression.addProperty("quality", "87");

        File photo = new File(filename);
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), photo);

        RequestBody multipart = new MultipartBody.Builder(session.getUuid())
                .setType(MultipartBody.FORM)
                .addFormDataPart("_uuid", session.getUuid())
                .addFormDataPart("_csrftoken", session.getToken())
                .addFormDataPart("upload_id", uploadId)
                .addFormDataPart("image_compression", compression.toString())
                .addFormDataPart("photo", "pending_media_" + uploadId + ".jpg", body)
                .build();

        service.uploadPhoto(multipart).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    configure(uploadId, filename, caption, callback);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private Media configure(String uploadId, String filename, String caption) throws IOException {
        BufferedImage buffimage = ImageIO.read(new File(filename));

        int w = buffimage.getWidth();
        int h = buffimage.getHeight();

        JsonObject edits = new JsonObject();
        edits.addProperty("crop_original_size", String.format("[%s.0,%s.0]", w, h));
        edits.addProperty("crop_center", "[0.0,0.0]");
        edits.addProperty("crop_zoom", 1.0);

        JsonObject extra = new JsonObject();
        extra.addProperty("source_width", w);
        extra.addProperty("source_height", h);

        JsonObject data = new JsonObject();
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_folder", "Instagram");
        data.addProperty("source_type", 4);
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("caption", caption);
        data.addProperty("upload_id", uploadId);
        data.addProperty("device", getDeviceSetting());
        data.add("edits", edits);
        data.add("extra", extra);

        Response<UploadPhotoResponse> response = service.configure(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }

        try {
            expose();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response.body().getMedia();
    }

    private void configure(String uploadId, String filename, String caption,
                           final com.github.volfor.Callback<UploadPhotoResponse> callback) {

        try {
            BufferedImage buffimage = ImageIO.read(new File(filename));

            int w = buffimage.getWidth();
            int h = buffimage.getHeight();

            JsonObject edits = new JsonObject();
            edits.addProperty("crop_original_size", String.format("[%s.0,%s.0]", w, h));
            edits.addProperty("crop_center", "[0.0,0.0]");
            edits.addProperty("crop_zoom", 1.0);

            JsonObject extra = new JsonObject();
            extra.addProperty("source_width", w);
            extra.addProperty("source_height", h);

            JsonObject data = new JsonObject();
            data.addProperty("_csrftoken", session.getToken());
            data.addProperty("media_folder", "Instagram");
            data.addProperty("source_type", 4);
            data.addProperty("_uid", session.getUsernameId());
            data.addProperty("_uuid", session.getUuid());
            data.addProperty("caption", caption);
            data.addProperty("upload_id", uploadId);
            data.addProperty("device", getDeviceSetting());
            data.add("edits", edits);
            data.add("extra", extra);

            service.configure(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<UploadPhotoResponse>() {
                @Override
                public void onResponse(Call<UploadPhotoResponse> call, Response<UploadPhotoResponse> response) {
                    if (response.isSuccessful()) {
                        try {
                            expose();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        callback.onSuccess(response.body());
                    } else {
                        callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                    }
                }

                @Override
                public void onFailure(Call<UploadPhotoResponse> call, Throwable t) {
                    callback.onFailure(t);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Media editMedia(long mediaId, String captionText) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("caption_text", captionText);

        Response<MediaResponse> response = service.editMedia(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getMedia();
    }

    public void editMedia(long mediaId, String captionText, final com.github.volfor.Callback<MediaResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("caption_text", captionText);

        service.editMedia(mediaId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<MediaResponse>() {
            @Override
            public void onResponse(Call<MediaResponse> call, Response<MediaResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public Media removeSelfTag(long mediaId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        Response<MediaResponse> response = service.removeUsertag(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getMedia();
    }

    public void removeSelfTag(long mediaId, final com.github.volfor.Callback<MediaResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.removeUsertag(mediaId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<MediaResponse>() {
            @Override
            public void onResponse(Call<MediaResponse> call, Response<MediaResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public MediaInfoResponse mediaInfo(long mediaId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", mediaId);

        Response<MediaInfoResponse> response = service.mediaInfo(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void mediaInfo(long mediaId, final com.github.volfor.Callback<MediaInfoResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", mediaId);

        service.mediaInfo(mediaId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<MediaInfoResponse>() {
            @Override
            public void onResponse(Call<MediaInfoResponse> call, Response<MediaInfoResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaInfoResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public Comment comment(long mediaId, String commentText) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("comment_text", commentText);

        Response<CommentResponse> response = service.comment(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getComment();
    }

    public void comment(long mediaId, String commentText, final com.github.volfor.Callback<CommentResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("comment_text", commentText);

        service.comment(mediaId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<CommentResponse>() {
            @Override
            public void onResponse(Call<CommentResponse> call, Response<CommentResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<CommentResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void deleteComment(long mediaId, long commentId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.deleteComment(mediaId, commentId, SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void deleteComment(long mediaId, long commentId,
                              final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {

        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.deleteComment(mediaId, commentId, SIG_KEY_VERSION, generateSignature(data))
                .enqueue(new Callback<com.github.volfor.responses.Response>() {
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

    public ExploreResponse explore() throws IOException {
        Response<ExploreResponse> response = service.explore().execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void explore(final com.github.volfor.Callback<ExploreResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.explore().enqueue(new Callback<ExploreResponse>() {
            @Override
            public void onResponse(Call<ExploreResponse> call, Response<ExploreResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ExploreResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public ProfileData getProfileData() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        Response<ProfileDataResponse> response = service.profile(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getProfileData();
    }

    public void getProfileData(final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.profile(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ProfileDataResponse>() {
            @Override
            public void onResponse(Call<ProfileDataResponse> call, Response<ProfileDataResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getProfileData());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ProfileDataResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public ProfileData editProfile(Profile profile) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("username", session.getLoggedInUser().getUsername());
        data.addProperty("email", profile.getEmail());
        if (profile.getUrl() != null) data.addProperty("external_url", profile.getUrl());
        if (profile.getPhone() != null) data.addProperty("phone_number", profile.getPhone());
        if (profile.getName() != null) data.addProperty("full_name", profile.getName());
        if (profile.getBiography() != null) data.addProperty("biography", profile.getBiography());
        if (profile.getGender() != null) data.addProperty("gender", profile.getGender());

        Response<ProfileDataResponse> response = service.editProfile(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getProfileData();
    }

    public void editProfile(Profile profile, final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("username", session.getLoggedInUser().getUsername());
        data.addProperty("email", profile.getEmail());
        if (profile.getUrl() != null) data.addProperty("external_url", profile.getUrl());
        if (profile.getPhone() != null) data.addProperty("phone_number", profile.getPhone());
        if (profile.getName() != null) data.addProperty("full_name", profile.getName());
        if (profile.getBiography() != null) data.addProperty("biography", profile.getBiography());
        if (profile.getGender() != null) data.addProperty("gender", profile.getGender());

        service.editProfile(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ProfileDataResponse>() {
            @Override
            public void onResponse(Call<ProfileDataResponse> call, Response<ProfileDataResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getProfileData());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ProfileDataResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public User getUserInfo(long usernameId) throws IOException {
        Response<UserInfoResponse> response = service.userInfo(usernameId).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getUser();
    }

    public void getUserInfo(long usernameId, final com.github.volfor.Callback<User> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.userInfo(usernameId).enqueue(new Callback<UserInfoResponse>() {
            @Override
            public void onResponse(Call<UserInfoResponse> call, Response<UserInfoResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getUser());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public User getSelfInfo() throws IOException {
        return getUserInfo(session.getUsernameId());
    }

    public void getSelfInfo(com.github.volfor.Callback<User> callback) {
        getUserInfo(session.getUsernameId(), callback);
    }

    public FollowingRecentActivityResponse getFollowingRecentActivity() throws IOException {
        Response<FollowingRecentActivityResponse> response = service.news().execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getFollowingRecentActivity(final com.github.volfor.Callback<FollowingRecentActivityResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.news().enqueue(new Callback<FollowingRecentActivityResponse>() {
            @Override
            public void onResponse(Call<FollowingRecentActivityResponse> call, Response<FollowingRecentActivityResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FollowingRecentActivityResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public UsertagsResponse getUsertags(long usernameId) throws IOException {
        Response<UsertagsResponse> response = service.usertags(usernameId, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getUsertags(long usernameId, final com.github.volfor.Callback<UsertagsResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.usertags(usernameId, session.getRankToken()).enqueue(new Callback<UsertagsResponse>() {
            @Override
            public void onResponse(Call<UsertagsResponse> call, Response<UsertagsResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<UsertagsResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public UsertagsResponse getSelfUsertags() throws IOException {
        return getUsertags(session.getUsernameId());
    }

    public void getSelfUsertags(com.github.volfor.Callback<UsertagsResponse> callback) {
        getUsertags(session.getUsernameId(), callback);
    }

    public List<User> getMediaLikers(long mediaId) throws IOException {
        Response<MediaLikersResponse> response = service.likers(mediaId).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getUsers();
    }

    public void getMediaLikers(long mediaId, final com.github.volfor.Callback<MediaLikersResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.likers(mediaId).enqueue(new Callback<MediaLikersResponse>() {
            @Override
            public void onResponse(Call<MediaLikersResponse> call, Response<MediaLikersResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaLikersResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public List<GeoMedia> getGeoMedia(long usernameId) throws IOException {
        Response<GeoMediaResponse> response = service.geoMedia(usernameId).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getGeoMedia();
    }

    public void getGeoMedia(long usernameId, final com.github.volfor.Callback<List<GeoMedia>> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.geoMedia(usernameId).enqueue(new Callback<GeoMediaResponse>() {
            @Override
            public void onResponse(Call<GeoMediaResponse> call, Response<GeoMediaResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getGeoMedia());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<GeoMediaResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public List<GeoMedia> getSelfGeoMedia() throws IOException {
        return getGeoMedia(session.getUsernameId());
    }

    public void getSelfGeoMedia(com.github.volfor.Callback<List<GeoMedia>> callback) {
        getGeoMedia(session.getUsernameId(), callback);
    }

    public FbSearchResponse fbUserSearch(String query) throws IOException {
        Response<FbSearchResponse> response = service.fbSearch(query, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void fbUserSearch(String query, final com.github.volfor.Callback<FbSearchResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.fbSearch(query, session.getRankToken()).enqueue(new Callback<FbSearchResponse>() {
            @Override
            public void onResponse(Call<FbSearchResponse> call, Response<FbSearchResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FbSearchResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public SearchUserResponse searchUsers(String query) throws IOException {
        Response<SearchUserResponse> response = service.searchUser(SIG_KEY_VERSION, query, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void searchUsers(String query, final com.github.volfor.Callback<SearchUserResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.searchUser(SIG_KEY_VERSION, query, session.getRankToken()).enqueue(new Callback<SearchUserResponse>() {
            @Override
            public void onResponse(Call<SearchUserResponse> call, Response<SearchUserResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<SearchUserResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public User searchUsername(String username) throws IOException {
        Response<UserInfoResponse> response = service.search(username).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getUser();
    }

    public void searchUsername(String username, final com.github.volfor.Callback<User> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.search(username).enqueue(new Callback<UserInfoResponse>() {
            @Override
            public void onResponse(Call<UserInfoResponse> call, Response<UserInfoResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getUser());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public List<Tag> searchTags(String query) throws IOException {
        Response<SearchTagResponse> response = service.searchTags(query, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getResults();
    }

    public void searchTags(String query, final com.github.volfor.Callback<SearchTagResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.searchTags(query, session.getRankToken()).enqueue(new Callback<SearchTagResponse>() {
            @Override
            public void onResponse(Call<SearchTagResponse> call, Response<SearchTagResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<SearchTagResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public UserFeedResponse getUserFeed(long usernameId, String maxId, long minTimestamp) throws IOException {
        Response<UserFeedResponse> response = service.feed(usernameId, maxId, minTimestamp, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getUserFeed(long usernameId, String maxId, long minTimestamp,
                            final com.github.volfor.Callback<UserFeedResponse> callback) {

        if (callback == null) throw new NullPointerException("callback == null");

        service.feed(usernameId, maxId, minTimestamp, session.getRankToken()).enqueue(new Callback<UserFeedResponse>() {
            @Override
            public void onResponse(Call<UserFeedResponse> call, Response<UserFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<UserFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public UserFeedResponse getSelfFeed(String maxId, long minTimestamp) throws IOException {
        return getUserFeed(session.getUsernameId(), maxId, minTimestamp);
    }

    public void getSelfFeed(String maxId, long minTimestamp, com.github.volfor.Callback<UserFeedResponse> callback) {
        getUserFeed(session.getUsernameId(), maxId, minTimestamp, callback);
    }

    public TagFeedResponse getHashtagFeed(String hashtag, String maxId) throws IOException {
        Response<TagFeedResponse> response = service.hashtagFeed(hashtag, maxId, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getHashtagFeed(String hashtag, String maxId, final com.github.volfor.Callback<TagFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.hashtagFeed(hashtag, maxId, session.getRankToken()).enqueue(new Callback<TagFeedResponse>() {
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

    public List<LocationItem> searchLocation(String query) throws IOException {
        Response<SearchLocationResponse> response = service.fbSearchLocation(query, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getItems();
    }

    public void searchLocation(String query, final com.github.volfor.Callback<SearchLocationResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.fbSearchLocation(query, session.getRankToken()).enqueue(new Callback<SearchLocationResponse>() {
            @Override
            public void onResponse(Call<SearchLocationResponse> call, Response<SearchLocationResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<SearchLocationResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public LocationFeedResponse getLocationFeed(long locationId, String maxId) throws IOException {
        Response<LocationFeedResponse> response = service.locationFeed(locationId, maxId, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getLocationFeed(long locationId, String maxId, final com.github.volfor.Callback<LocationFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.locationFeed(locationId, maxId, session.getRankToken()).enqueue(new Callback<LocationFeedResponse>() {
            @Override
            public void onResponse(Call<LocationFeedResponse> call, Response<LocationFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<LocationFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public PopularFeedResponse getPopularFeed() throws IOException {
        Response<PopularFeedResponse> response = service.popular(session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getPopularFeed(final com.github.volfor.Callback<PopularFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.popular(session.getRankToken()).enqueue(new Callback<PopularFeedResponse>() {
            @Override
            public void onResponse(Call<PopularFeedResponse> call, Response<PopularFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<PopularFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public FollowersResponse getUserFollowings(long usernameId, String maxId) throws IOException {
        Response<FollowersResponse> response = service.following(usernameId, maxId, SIG_KEY_VERSION, session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getUserFollowings(long usernameId, String maxId, final com.github.volfor.Callback<FollowersResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.following(usernameId, maxId, SIG_KEY_VERSION, session.getRankToken()).enqueue(new Callback<FollowersResponse>() {
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

    public FollowersResponse getUserFollowings(long usernameId) throws IOException {
        return getUserFollowings(usernameId, "");
    }

    public void getUserFollowings(long usernameId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(usernameId, "", callback);
    }

    public FollowersResponse getSelfFollowings() throws IOException {
        return getUserFollowings(session.getUsernameId());
    }

    public void getSelfFollowings(com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(session.getUsernameId(), callback);
    }

    public FollowersResponse getSelfFollowings(String maxId) throws IOException {
        return getUserFollowings(session.getUsernameId(), maxId);
    }

    public void getSelfFollowings(String maxId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(session.getUsernameId(), maxId, callback);
    }

    public void unlike(long mediaId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", mediaId);

        service.unlike(mediaId, SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void unlike(long mediaId, final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", mediaId);

        service.unlike(mediaId, SIG_KEY_VERSION, generateSignature(data))
                .enqueue(new Callback<com.github.volfor.responses.Response>() {
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

    public MediaCommentsResponse getMediaComments(long mediaId) throws IOException {
        Response<MediaCommentsResponse> response = service.comments(mediaId).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getMediaComments(long mediaId, final com.github.volfor.Callback<MediaCommentsResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.comments(mediaId).enqueue(new Callback<MediaCommentsResponse>() {
            @Override
            public void onResponse(Call<MediaCommentsResponse> call, Response<MediaCommentsResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaCommentsResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void setNameAndPhone(String name, String phone) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("first_name", name);
        data.addProperty("phone_number", phone);
        data.addProperty("_csrftoken", session.getToken());

        service.phoneName(SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void setNameAndPhone(String name, String phone,
                                final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {

        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("first_name", name);
        data.addProperty("phone_number", phone);
        data.addProperty("_csrftoken", session.getToken());

        service.phoneName(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<com.github.volfor.responses.Response>() {
            @Override
            public void onResponse(Call<com.github.volfor.responses.Response> call, Response<com.github.volfor.responses.Response> response) {
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

    public FriendshipStatus follow(long userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        Response<FriendshipResponse> response = service.follow(userId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getFriendshipStatus();
    }

    public void follow(long userId, final com.github.volfor.Callback<FriendshipStatus> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        service.follow(userId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<FriendshipResponse>() {
            @Override
            public void onResponse(Call<FriendshipResponse> call, Response<FriendshipResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getFriendshipStatus());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FriendshipResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public FriendshipStatus unfollow(long userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        Response<FriendshipResponse> response = service.unfollow(userId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getFriendshipStatus();
    }

    public void unfollow(long userId, final com.github.volfor.Callback<FriendshipStatus> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        service.unfollow(userId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<FriendshipResponse>() {
            @Override
            public void onResponse(Call<FriendshipResponse> call, Response<FriendshipResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getFriendshipStatus());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FriendshipResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public FriendshipStatus block(long userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        Response<FriendshipResponse> response = service.block(userId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getFriendshipStatus();
    }

    public void block(long userId, final com.github.volfor.Callback<FriendshipStatus> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        service.block(userId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<FriendshipResponse>() {
            @Override
            public void onResponse(Call<FriendshipResponse> call, Response<FriendshipResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getFriendshipStatus());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FriendshipResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public FriendshipStatus unblock(long userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        Response<FriendshipResponse> response = service.unblock(userId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getFriendshipStatus();
    }

    public void unblock(long userId, final com.github.volfor.Callback<FriendshipStatus> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        service.unblock(userId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<FriendshipResponse>() {
            @Override
            public void onResponse(Call<FriendshipResponse> call, Response<FriendshipResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getFriendshipStatus());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FriendshipResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public FriendshipStatus userFriendship(long userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        Response<FriendshipStatus> response = service.friendship(userId, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void userFriendship(long userId, final com.github.volfor.Callback<FriendshipStatus> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        service.friendship(userId, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<FriendshipStatus>() {
            @Override
            public void onResponse(Call<FriendshipStatus> call, Response<FriendshipStatus> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<FriendshipStatus> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public LikedFeedResponse getLikedMedia(String maxId) throws IOException {
        Response<LikedFeedResponse> response = service.liked(maxId).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body();
    }

    public void getLikedMedia(String maxId, final com.github.volfor.Callback<LikedFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.liked(maxId).enqueue(new Callback<LikedFeedResponse>() {
            @Override
            public void onResponse(Call<LikedFeedResponse> call, Response<LikedFeedResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<LikedFeedResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public LikedFeedResponse getLikedMedia() throws IOException {
        return getLikedMedia("");
    }

    public void getLikedMedia(com.github.volfor.Callback<LikedFeedResponse> callback) {
        getLikedMedia("", callback);
    }

    public boolean deleteMedia(long mediaId) throws IOException {
        String id = String.format("%s_%s", mediaId, session.getUsernameId());

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", id);

        Response<MediaDeleteResponse> response = service.deleteMedia(id, SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().isDidDelete();
    }

    public void deleteMedia(long mediaId, final com.github.volfor.Callback<MediaDeleteResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        String id = String.format("%s_%s", mediaId, session.getUsernameId());

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", id);

        service.deleteMedia(id, SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<MediaDeleteResponse>() {
            @Override
            public void onResponse(Call<MediaDeleteResponse> call, Response<MediaDeleteResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<MediaDeleteResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public void changePassword(String oldPassword, String newPassword) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("old_password", oldPassword);
        data.addProperty("new_password1", newPassword);
        data.addProperty("new_password2", newPassword);

        service.changePassword(SIG_KEY_VERSION, generateSignature(data)).execute();
    }

    public void changePassword(String oldPassword, String newPassword,
                               final com.github.volfor.Callback<com.github.volfor.responses.Response> callback) {

        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("old_password", oldPassword);
        data.addProperty("new_password1", newPassword);
        data.addProperty("new_password2", newPassword);

        service.changePassword(SIG_KEY_VERSION, generateSignature(data)).enqueue(
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

    public ProfileData removeProfilePicture() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        Response<ProfileDataResponse> response = service.removeProfilePic(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getProfileData();
    }

    public void removeProfilePicture(final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.removeProfilePic(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ProfileDataResponse>() {
            @Override
            public void onResponse(Call<ProfileDataResponse> call, Response<ProfileDataResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getProfileData());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ProfileDataResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public ProfileData setPrivateAccount() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        Response<ProfileDataResponse> response = service.setPrivate(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getProfileData();
    }

    public void setPrivateAccount(final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.setPrivate(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ProfileDataResponse>() {
            @Override
            public void onResponse(Call<ProfileDataResponse> call, Response<ProfileDataResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getProfileData());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ProfileDataResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public ProfileData setPublicAccount() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        Response<ProfileDataResponse> response = service.setPublic(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            throw new IOException(response.errorBody().string());
        }
        return response.body().getProfileData();
    }

    public void setPublicAccount(final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        service.setPublic(SIG_KEY_VERSION, generateSignature(data)).enqueue(new Callback<ProfileDataResponse>() {
            @Override
            public void onResponse(Call<ProfileDataResponse> call, Response<ProfileDataResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().getProfileData());
                } else {
                    callback.onFailure(new Throwable(parseErrorMessage(response.errorBody())));
                }
            }

            @Override
            public void onFailure(Call<ProfileDataResponse> call, Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public Session getSession() {
        return session;
    }

}
