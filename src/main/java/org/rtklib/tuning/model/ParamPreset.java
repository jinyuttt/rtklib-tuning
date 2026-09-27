package org.rtklib.tuning.model;

import java.util.Map;

public class ParamPreset {

    private String name;
    private String description;
    private Map<String, Object> params;

    public ParamPreset() {}

    public ParamPreset(String name, String description, Map<String, Object> params) {
        this.name = name;
        this.description = description;
        this.params = params;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}