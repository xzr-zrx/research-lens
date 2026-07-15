package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "academic.fulltext")
public class FullTextDownloadProperties {
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 60;
    private int maxSizeMb = 50;
    private int maxRedirects = 5;

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int value) { this.connectTimeoutSeconds = value; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int value) { this.readTimeoutSeconds = value; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int value) { this.maxSizeMb = value; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int value) { this.maxRedirects = value; }
}
