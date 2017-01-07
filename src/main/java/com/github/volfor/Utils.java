package com.github.volfor;

import com.github.volfor.helpers.Json;
import okhttp3.Cookie;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

import static com.github.volfor.Constants.IG_SIG_KEY;
import static com.github.volfor.Constants.SIG_KEY_VERSION;

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
        String dataString = data.toJSONString();

        try {
            String encodedData = URLEncoder.encode(dataString, "UTF-8");

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(IG_SIG_KEY.getBytes("UTF-8"), "HmacSHA256"));
            String encodedHex = DatatypeConverter.printHexBinary(hmac.doFinal(dataString.getBytes("UTF-8"))).toLowerCase();

            return "ig_sig_key_version=" + SIG_KEY_VERSION + "&signed_body=" + encodedHex + "." + encodedData;
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }

        return "";
    }

    static String getHexdigest(String s1, String s2) {
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

    static Cookie getCookie(List<Cookie> cookies, String name) {
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

}
