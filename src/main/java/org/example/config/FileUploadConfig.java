package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    private String path;
    private String allowedExtensions;

    public void setPath(String path) {
        this.path = path;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public String getPath() { return path; }
    public String getAllowedExtensions() { return allowedExtensions; }
}
