package com.github.volfor.helpers;

import com.sun.istack.internal.NotNull;
import org.json.simple.JSONObject;

import java.util.Map;

@SuppressWarnings("unchecked")
public class Json extends JSONObject {

    public static class Builder {

        private Json json;

        public Builder() {
            this.json = new Json();
        }

        public Builder put(Object key, Object value) {
            json.put(key, value);
            return this;
        }

        public Builder putAll(@NotNull Map m) {
            json.putAll(m);
            return this;
        }

        public Json build() {
            return json;
        }
    }

}
