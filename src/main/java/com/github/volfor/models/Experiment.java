package com.github.volfor.models;

import lombok.Data;

import java.util.Map;

@Data
public class Experiment {

    private String name;
    private String group;
    private Map<String, String> params;

}
