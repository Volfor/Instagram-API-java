package com.github.volfor;

import com.github.volfor.helpers.Json;
import okhttp3.Cookie;
import okhttp3.ResponseBody;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.github.volfor.Constants.IG_SIG_KEY;

class Utils {

    static String generateUUID(boolean type) {
        String generatedUuid = UUID.randomUUID().toString();
        return type ? generatedUuid : generatedUuid.replace("-", "");
    }

    static String generateDeviceId(String seed) {
        String volatileSeed = "12345";
        return "android-" + getHexdigest(seed, volatileSeed).substring(0, 16);
    }

    static String generateSignature(Json data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(IG_SIG_KEY.getBytes("UTF-8"), "HmacSHA256"));
            String encodedHex = bytesToHex(hmac.doFinal(data.toJSONString().getBytes("UTF-8")));

            return encodedHex + "." + data.toJSONString();
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }

        return "";
    }

    static String bytesToHex(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    static String getHexdigest(String s1, String s2) {
        StringBuilder sb = new StringBuilder();

        try {
            byte[] a = s1.getBytes("UTF-8");
            byte[] b = s2.getBytes("UTF-8");
            byte[] c = new byte[a.length + b.length];

            System.arraycopy(a, 0, c, 0, a.length);
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

    static Cookie getCookie(Set<Cookie> cookies, String name) {
        for (Cookie c : cookies) {
            if (c != null && c.name().equals(name)) {
                return c;
            }
        }

        return null;
    }

    static String getCookieString(List<Cookie> cookies) {
        String s = "";
        if (!cookies.isEmpty()) {
            for (Cookie c : cookies) {
                s += c.name() + "=" + c.value() + "; ";
            }
            s = s.substring(0, s.length() - 2);
        }

        return s;
    }

    static String parseErrorMessage(ResponseBody errorBody) {
        try {
            JSONObject error = (JSONObject) new JSONParser().parse(errorBody.string());
            return (String) error.get("message");
        } catch (ParseException | IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

}
