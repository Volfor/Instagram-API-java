package com.github.volfor;

import com.github.volfor.models.FriendshipStatus;
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

    @GET("feed/timeline/?ranked_content=true")
    Call<TimelineFeedResponse> timeline(@Query("rank_token") String rankToken);

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

    @FormUrlEncoded
    @POST("accounts/current_user/?edit=true")
    Call<ProfileDataResponse> profile(@Field("ig_sig_key_version") String sigKeyVersion,
                                      @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("accounts/edit_profile/")
    Call<ProfileDataResponse> editProfile(@Field("ig_sig_key_version") String sigKeyVersion,
                                          @Field("signed_body") String signedBody);

    @GET("users/{usernameId}/info/")
    Call<UserInfoResponse> userInfo(@Path("usernameId") long usernameId);

    @GET("news/?")
    Call<FollowingRecentActivityResponse> news();

    @GET("usertags/{usernameId}/feed/?ranked_content=true")
    Call<UsertagsResponse> usertags(@Path("usernameId") long usernameId, @Query("rank_token") String rankToken);

    @GET("media/{mediaId}/likers/?")
    Call<MediaLikersResponse> likers(@Path("mediaId") long mediaId);

    @GET("maps/user/{usernameId}/")
    Call<GeoMediaResponse> geoMedia(@Path("usernameId") long usernameId);

    @GET("fbsearch/topsearch/?context=blended")
    Call<FbSearchResponse> fbSearch(@Query("query") String query, @Query("rank_token") String rankToken);

    @GET("users/search/?is_typeahead=true")
    Call<SearchUserResponse> searchUser(@Query("ig_sig_key_version") String sigKeyVersion,
                                        @Query("query") String query,
                                        @Query("rank_token") String rankToken);

    @GET("users/{username}/usernameinfo/")
    Call<UserInfoResponse> search(@Path("username") String username);

    @GET("tags/search/?is_typeahead=true")
    Call<SearchTagResponse> searchTags(@Query("q") String query, @Query("rank_token") String rankToken);

    @GET("feed/user/{usernameId}/?ranked_content=true")
    Call<UserFeedResponse> feed(@Path("usernameId") long usernameId,
                                @Query("max_id") String maxId,
                                @Query("min_timestamp") long minTimestamp,
                                @Query("rank_token") String rankToken);

    @GET("feed/tag/{hashtag}/?ranked_content=true")
    Call<TagFeedResponse> hashtagFeed(@Path("hashtag") String hashtag,
                                      @Query("max_id") String maxId,
                                      @Query("rank_token") String rankToken);

    @GET("fbsearch/places/")
    Call<SearchLocationResponse> fbSearchLocation(@Query("query") String query, @Query("rank_token") String rankToken);

    @GET("feed/location/{locationId}/?ranked_content=true")
    Call<LocationFeedResponse> locationFeed(@Path("locationId") long locationId,
                                            @Query("max_id") String maxId,
                                            @Query("rank_token") String rankToken);

    @GET("feed/popular/?people_teaser_supported=1&ranked_content=true")
    Call<PopularFeedResponse> popular(@Query("rank_token") String rankToken);

    @GET("friendships/{usernameId}/following/")
    Call<FollowersResponse> following(@Path("usernameId") long usernameId,
                                      @Query("max_id") String maxId,
                                      @Query("ig_sig_key_version") String sigKeyVersion,
                                      @Query("rank_token") String rankToken);

    @FormUrlEncoded
    @POST("media/{mediaId}/unlike/")
    Call<Response> unlike(@Path("mediaId") long mediaId,
                          @Field("ig_sig_key_version") String sigKeyVersion,
                          @Field("signed_body") String signedBody);

    @GET("media/{mediaId}/comments/?")
    Call<MediaCommentsResponse> comments(@Path("mediaId") long mediaId);

    @FormUrlEncoded
    @POST("accounts/set_phone_and_name/")
    Call<Response> phoneName(@Field("ig_sig_key_version") String sigKeyVersion,
                             @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("friendships/create/{userId}/")
    Call<FriendshipResponse> follow(@Path("userId") long userId,
                                    @Field("ig_sig_key_version") String sigKeyVersion,
                                    @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("friendships/destroy/{userId}/")
    Call<FriendshipResponse> unfollow(@Path("userId") long userId,
                                      @Field("ig_sig_key_version") String sigKeyVersion,
                                      @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("friendships/block/{userId}/")
    Call<FriendshipResponse> block(@Path("userId") long userId,
                                   @Field("ig_sig_key_version") String sigKeyVersion,
                                   @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("friendships/unblock/{userId}/")
    Call<FriendshipResponse> unblock(@Path("userId") long userId,
                                     @Field("ig_sig_key_version") String sigKeyVersion,
                                     @Field("signed_body") String signedBody);

    @FormUrlEncoded
    @POST("friendships/show/{userId}/")
    Call<FriendshipStatus> friendship(@Path("userId") long userId,
                                      @Field("ig_sig_key_version") String sigKeyVersion,
                                      @Field("signed_body") String signedBody);

}
