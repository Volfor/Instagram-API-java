package com.github.volfor;

import okhttp3.Cookie;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Set;

public class AddCookiesInterceptor implements Interceptor {

    private Set<Cookie> cookies;

    public AddCookiesInterceptor(Set<Cookie> sessionCookies) {
        this.cookies = sessionCookies;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();

        for (Cookie cookie : cookies) {
            builder.addHeader("Cookie", String.format("%s=%s", cookie.name(), cookie.value()));
        }

        return chain.proceed(builder.build());
    }

}
