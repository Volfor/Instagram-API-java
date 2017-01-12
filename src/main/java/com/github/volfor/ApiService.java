package com.github.volfor;

import com.github.volfor.responses.*;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

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

}
