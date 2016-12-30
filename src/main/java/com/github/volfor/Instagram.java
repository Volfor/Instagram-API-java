package com.github.volfor;

import okhttp3.*;
import org.apache.commons.codec.binary.Hex;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.github.volfor.Constants.*;

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
    private JSONObject lastJson;

    private static OkHttpClient httpClient;

    static {
        httpClient = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url);
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .build();
    }

    public Instagram(String username, String password) {
        deviceId = generateDeviceId(getHexdigest(username, password));
        setUser(username, password);
    }

    @SuppressWarnings("unchecked")
    public void login(boolean force) {
        if (!isLoggedIn || force) {
            // if you need proxy make something like this:
            // Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("host", "post"));
            // httpClient = httpClient.newBuilder().proxy(proxy).build();

            if (sendRequest("si/fetch_headers/?challenge_type=signup&guid=" + generateUUID(false), null)) {
                JSONObject data = new JSONObject();
                data.put("phone_id", generateUUID(true));
                data.put("_csrftoken", getCookie(cookies, "csrftoken").value());
                data.put("username", username);
                data.put("guid", uuid);
                data.put("device_id", deviceId);
                data.put("password", password);
                data.put("login_attempt_count", "0");

                if (sendRequest("accounts/login/", generateSignature(data.toJSONString()))) {
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

    @SuppressWarnings("unchecked")
    private void syncFeatures() {
        JSONObject data = new JSONObject();
        data.put("_uuid", uuid);
        data.put("_uid", usernameId);
        data.put("id", usernameId);
        data.put("_csrftoken", token);
        data.put("experiments", EXPERIMENTS);

        sendRequest("qe/sync/", generateSignature(data.toJSONString()));
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

    private void setUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.uuid = generateUUID(true);
    }

    private String generateUUID(boolean type) {
        String generatedUuid = UUID.randomUUID().toString();
        return type ? generatedUuid : generatedUuid.replace("-", "");
    }

    private String generateDeviceId(String seed) {
        String volatileSeed = "12345";
        return "android-" + getHexdigest(seed, volatileSeed).substring(0, 16);
    }

    private String generateSignature(String data) {
        try {
            String encodedData = URLEncoder.encode(data, "UTF-8");

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(IG_SIG_KEY.getBytes("UTF-8"), "HmacSHA256"));
            String encodedHex = Hex.encodeHexString(hmac.doFinal(data.getBytes("UTF-8")));

            return "ig_sig_key_version=" + SIG_KEY_VERSION + "&signed_body=" + encodedHex + "." + encodedData;
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getHexdigest(String s1, String s2) {
        StringBuilder sb = new StringBuilder();

        try {
            byte[] a = s1.getBytes("UTF-8");
            byte[] b = s2.getBytes("UTF-8");
            byte[] c = new byte[a.length + b.length];

            System.arraycopy(a, 0, c, 0, b.length);
            System.arraycopy(b, 0, c, a.length, b.length);

            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(c);

            for (byte bt : md.digest()) {
                sb.append(String.format("%02x", bt & 0xff));
            }
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return sb.toString();
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

        if (loginSessionCookies != null && !loginSessionCookies.isEmpty()) {
            request = request.newBuilder()
                    .addHeader("Cookie", loginSessionCookies)
                    .build();
        }

        if (post != null) { //POST
            request = request.newBuilder()
                    .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"), post))
                    .build();
        }

        try {
            Response response = httpClient.newCall(request).execute();

            if (response.code() == 200) {
                cookies = httpClient.cookieJar().loadForRequest(HttpUrl.parse(endpoint));
                lastJson = (JSONObject) new JSONParser().parse(response.body().string());
                return true;
            } else {
                System.err.println("Request return " + response.code() + " error!");

                if (!isLoggedIn) {
                    throw new NotLoggedInException();
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return false;
    }

    private Cookie getCookie(List<Cookie> cookies, String name) {
        for (Cookie c : cookies) {
            if (c != null && c.name().equals(name)) {
                return c;
            }
        }

        return null;
    }

    private String getCookieString(List<Cookie> cookies) {
        String s = "";
        if (!cookies.isEmpty()) {
            for (Cookie c : cookies) {
                s += c.name() + "=" + c.value() + "; ";
            }
            s = s.substring(0, s.length() - 2);
        }

        return s;
    }

}
