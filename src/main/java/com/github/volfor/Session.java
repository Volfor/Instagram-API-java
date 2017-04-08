package com.github.volfor;

import com.github.volfor.models.User;
import lombok.Data;
import okhttp3.Cookie;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.github.volfor.Utils.*;

@Data
public class Session {

    private Set<Cookie> cookies = new HashSet<>();
    private User loggedInUser;
    private String uuid = generateUUID(true);
    private String adid = generateUUID(true);
    private String deviceId;

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
        return cookie != null ? cookie.value() : "";
    }

    public String getMid() {
        Cookie cookie = getCookie(cookies, "mid");
        return cookie != null ? cookie.value() : "";
    }

    public String getToken() {
        Cookie cookie = getCookie(cookies, "csrftoken");
        return cookie != null ? cookie.value() : "";
    }

    public String getRankToken() {
        return loggedInUser != null ? String.format("%s_%s", loggedInUser.getPk(), generateUUID(true)) : "";
    }

    public long getUsernameId() {
        return loggedInUser != null ? loggedInUser.getPk() : 0;
    }

    public void setCookies(Set<Cookie> cookies) {
        this.cookies.clear();
        this.cookies.addAll(cookies);
    }

    public void setCookies(Collection<Cookie> c) {
        this.cookies.clear();
        this.cookies.addAll(c);
    }

    public void close() {
        cookies.clear();
        loggedInUser = null;
    }

}
