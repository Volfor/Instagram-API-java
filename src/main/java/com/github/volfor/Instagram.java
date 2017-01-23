package com.github.volfor;

import com.github.volfor.models.*;
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
import java.util.Map;
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
        session.setDeviceId(generateDeviceId(getHexdigest(username, password)));

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
                                    isLoggedIn = true;

                                    session.setCookies(((OkHttpClient) retrofit.callFactory()).cookieJar()
                                            .loadForRequest(call.request().url()));

                                    session.setLoggedInUser(response.body().getLoggedInUser());

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
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
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
        Response response = service.timeline(session.getRankToken()).execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "timelineFeed",
                    "Getting timeline feed failed with message: " + parseErrorMessage(response.errorBody()));
        }
    }

    public void timelineFeed(final com.github.volfor.Callback<TimelineFeedResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.timeline(session.getRankToken()).enqueue(new Callback<TimelineFeedResponse>() {
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

    public void getUserFollowers(long userId, String maxId, final com.github.volfor.Callback<FollowersResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        service.followers(userId, session.getRankToken(), maxId).enqueue(new Callback<FollowersResponse>() {
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
        getUserFollowers(session.getUsernameId(), callback);
    }

    public void getSelfFollowers(String maxId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowers(session.getUsernameId(), maxId, callback);
    }

    public void megaphoneLog(final com.github.volfor.Callback<MegaphoneLogResponse> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        Map<String, String> params = new HashMap<>();
        params.put("type", "feed_aysf");
        params.put("action", "seen");
        params.put("_uuid", session.getUuid());
        params.put("device_id", session.getDeviceId());
        params.put("_csrftoken", session.getToken());

        service.megaphone(params).enqueue(new Callback<MegaphoneLogResponse>() {
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

    private void expose() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("id", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("experiment", "ig_android_profile_contextual_feed");

        Response response = service.expose(SIG_KEY_VERSION, generateSignature(data)).execute();
        if (!response.isSuccessful()) {
            LOG.logp(Level.WARNING, LOG.getName(), "expose",
                    "Expose failed with message: " + parseErrorMessage(response.errorBody()));
        }
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

    public void removeSelftag(long mediaId, final com.github.volfor.Callback<MediaResponse> callback) {
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

    public void editProfile(Profile profile, final com.github.volfor.Callback<ProfileData> callback) {
        if (callback == null) throw new NullPointerException("callback == null");

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("username", username);
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

    public void getSelfInfo(com.github.volfor.Callback<User> callback) {
        getUserInfo(session.getUsernameId(), callback);
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

    public void getSelfUsertags(com.github.volfor.Callback<UsertagsResponse> callback) {
        getUsertags(session.getUsernameId(), callback);
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

    public void getSelfGeoMedia(com.github.volfor.Callback<List<GeoMedia>> callback) {
        getGeoMedia(session.getUsernameId(), callback);
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

    public void getSelfFeed(String maxId, long minTimestamp, com.github.volfor.Callback<UserFeedResponse> callback) {
        getUserFeed(session.getUsernameId(), maxId, minTimestamp, callback);
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

    public void getUserFollowings(long usernameId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(usernameId, "", callback);
    }

    public void getSelfFollowings(com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(session.getUsernameId(), callback);
    }

    public void getSelfFollowings(String maxId, com.github.volfor.Callback<FollowersResponse> callback) {
        getUserFollowings(session.getUsernameId(), maxId, callback);
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

    public void setNameAndPhone(String name, String phone) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("first_name", name);
        data.addProperty("phone_number", phone);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("accounts/set_phone_and_name/", generateSignature(data));
    }

    public void getDirectShare() {
        sendRequest("direct_share/inbox/?", null);
    }

    public void follow(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("friendships/create/" + userId + "/", generateSignature(data));
    }

    public void unfollow(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("friendships/destroy/" + userId + "/", generateSignature(data));
    }

    public void block(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("friendships/block/" + userId + "/", generateSignature(data));
    }

    public void unblock(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("friendships/unblock/" + userId + "/", generateSignature(data));
    }

    public void userFriendship(long userId) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("user_id", userId);
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("friendships/show/" + userId + "/", generateSignature(data));
    }

    public void getLikedMedia(String maxid) {
        sendRequest("feed/liked/?max_id=" + maxid, null);
    }

    public void deleteMedia(long mediaId) {
        String id = String.format("%s_%s", mediaId, session.getUsernameId());

        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("media_id", id);

        sendRequest("media/" + id + "/delete/", generateSignature(data));
    }

    public void changePassword(String newPassword) {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());
        data.addProperty("old_password", password);
        data.addProperty("new_password1", newPassword);
        data.addProperty("new_password2", newPassword);

        sendRequest("accounts/change_password/", generateSignature(data));
    }

    public void removeProfilePicture() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("accounts/remove_profile_picture/", generateSignature(data));
    }

    public void setPrivateAccount() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

        sendRequest("accounts/set_private/", generateSignature(data));
    }

    public void setPublicAccount() {
        JsonObject data = new JsonObject();
        data.addProperty("_uuid", session.getUuid());
        data.addProperty("_uid", session.getUsernameId());
        data.addProperty("_csrftoken", session.getToken());

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
//            getUserFollowings(usernameId, nextMaxId);
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
//            getUserFeed(usernameId, nextMaxId, minTimestamp);
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
        return getTotalUserFeed(session.getUsernameId(), minTimestamp);
    }

    public List<Object> getTotalSelfFollowers() {
        return getTotalFollowers(session.getUsernameId());
    }

    public List<Object> getTotalSelfFollowings() {
        return getTotalFollowings(session.getUsernameId());
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

    public Session getSession() {
        return session;
    }

}
