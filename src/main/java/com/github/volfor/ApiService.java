package com.github.volfor;

import com.github.volfor.responses.*;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;

public interface ApiService {

    @GET("si/fetch_headers/?challenge_type=signup")
    Call<ResponseBody> fetchHeaders(@Query("guid") String guid);

    @FormUrlEncoded
    @POST("accounts/login/")
    Call<LoginResponse> login(@Field("ig_sig_key_version") String sigKeyVersion, @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("qe/sync/")
    Call<ResponseBody> sync(@Field("ig_sig_key_version") String sigKeyVersion, @Field("signed_body") String signedBody);

    @GET("friendships/autocomplete_user_list/")
    Call<AutocompleteUserListResponse> autocompleteUserList();

    @GET("feed/timeline/")
    Call<TimelineFeedResponse> timeline();

    @GET("direct_v2/inbox/?")
    Call<V2InboxResponse> directv2Inbox();

    @GET("news/inbox/?")
    Call<RecentActivityResponse> newsInbox();

    @GET("feed/tag/{tag}/?ranked_content=true")
    Call<TagFeedResponse> tagFeed(@Path("tag") String tag, @Query("rank_token") String rankToken);

    @FormUrlEncoded
    @POST("media/{mediaId}/like/")
    Call<Response> like(@Path("mediaId") long mediaId,
                        @Field("ig_sig_key_version") String sigKeyVersion,
                        @Field("signed_body") String signedBody);

    @GET("friendships/{userId}/followers/")
    Call<FollowersResponse> followers(@Path("userId") long userId,
                                      @Query("rank_token") String rankToken,
                                      @Query("max_id") String maxId);

    @FormUrlEncoded
    @POST("megaphone/log/")
    Call<MegaphoneLogResponse> megaphone(@FieldMap Map<String, String> params);

    @GET("accounts/logout/")
    Call<Response> logout();

    @FormUrlEncoded
    @POST("qe/expose/")
    Call<Response> expose(@Field("ig_sig_key_version") String sigKeyVersion, @Field("signed_body") String signedBody);

    @Headers({"X-IG-Capabilities: 3Q4=", "X-IG-Connection-Type: WIFI", "Accept-Encoding: gzip, deflate"})
    @POST("upload/photo/")
    Call<ResponseBody> uploadPhoto(@Body RequestBody body);

    @FormUrlEncoded
    @POST("media/configure/?")
    Call<UploadPhotoResponse> configure(@Field("ig_sig_key_version") String sigKeyVersion,
                                        @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("media/{mediaId}/edit_media/")
    Call<MediaResponse> editMedia(@Path("mediaId") long mediaId,
                                  @Field("ig_sig_key_version") String sigKeyVersion,
                                  @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("usertags/{mediaId}/remove/")
    Call<MediaResponse> removeUsertag(@Path("mediaId") long mediaId,
                                      @Field("ig_sig_key_version") String sigKeyVersion,
                                      @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("media/{mediaId}/info/")
    Call<MediaInfoResponse> mediaInfo(@Path("mediaId") long mediaId,
                                      @Field("ig_sig_key_version") String sigKeyVersion,
                                      @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("media/{mediaId}/comment/")
    Call<CommentResponse> comment(@Path("mediaId") long mediaId,
                                  @Field("ig_sig_key_version") String sigKeyVersion,
                                  @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("media/{mediaId}/comment/{commentId}/delete/")
    Call<Response> deleteComment(@Path("mediaId") long mediaId,
                                 @Path("commentId") long commentId,
                                 @Field("ig_sig_key_version") String sigKeyVersion,
                                 @Field("signed_body") String signedBody);

    @GET("discover/explore/")
    Call<ExploreResponse> explore();

}
