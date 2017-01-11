package com.github.volfor.responses;

import com.github.volfor.models.Experiment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class SyncResponse extends Response {

    private List<Experiment> experiments;

    public SyncResponse(ResponseBody body) {
        parseExperiments(body);
    }

    private void parseExperiments(ResponseBody body) {
        try {
            JsonObject json = (JsonObject) new JsonParser().parse(body.string());

            List<Experiment> experiments = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String name = entry.getKey();

                if (name.equals("status")) {
                    this.setStatus(entry.getValue().getAsString());
                    continue;
                }

                Experiment experiment = new Experiment();
                experiment.setName(name);

                Map<String, String> params = new HashMap<>();
                JsonObject paramsJson = entry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> item : paramsJson.entrySet()) {
                    params.put(item.getKey(), item.getValue().getAsString());
                }

                experiment.setParams(params);
                experiments.add(experiment);
            }

            this.experiments = experiments;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
