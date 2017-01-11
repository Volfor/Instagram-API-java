package com.github.volfor;

import com.github.volfor.responses.LoginResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @GET("si/fetch_headers/?challenge_type=signup")
    Call<ResponseBody> fetchHeaders(@Query("guid") String guid);

    @FormUrlEncoded
    @POST("accounts/login/")
    Call<LoginResponse> login(@Field("ig_sig_key_version") String sigKeyVersion, @Field("signed_body") String signedBody);

}
