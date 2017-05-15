package de.jeha.s3pt;

import java.util.Map;

class KairosdbPoint {
    String name;
    long timestamp;
    double value;
    Map<String, String> tags;

    public KairosdbPoint(String name, long timestamp, double value, Map<String, String> tags) {
        this.name = name;
        this.timestamp = timestamp;
        this.value = value;
        this.tags = tags;
    }
}
