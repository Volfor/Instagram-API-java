package com.github.volfor;

import com.github.volfor.models.User;
import lombok.Data;
import okhttp3.Cookie;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.github.volfor.Utils.getCookie;

@Data
public class Session {

    private Set<Cookie> cookies = new HashSet<>();
    private User loggedInUser;

    public Session() {
    }

    public Session(Set<Cookie> cookies) {
        this.cookies = cookies;
    }

    public Session(Collection<Cookie> c) {
        this.cookies.addAll(c);
    }

    public String getSessionId() {
        Cookie cookie = getCookie(cookies, "sessionid");
        return cookie != null ? cookie.value() : null;
    }

    public String getMid() {
        Cookie cookie = getCookie(cookies, "mid");
        return cookie != null ? cookie.value() : null;
    }

    public String getToken() {
        Cookie cookie = getCookie(cookies, "csrftoken");
        return cookie != null ? cookie.value() : null;
    }

    public void setCookies(Set<Cookie> cookies) {
        this.cookies.clear();
        this.cookies.addAll(cookies);
    }

    public void setCookies(Collection<Cookie> c) {
        this.cookies.clear();
        this.cookies.addAll(c);
    }

}
