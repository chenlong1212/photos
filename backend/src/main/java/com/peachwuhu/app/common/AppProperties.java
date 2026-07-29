package com.peachwuhu.app.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "peachwuhu")
public class AppProperties {
    private String storageRoot;
    private int previewSize = 400;
    private String allowedExtensions;
    private final Auth auth = new Auth();

    public String getStorageRoot() { return storageRoot; }
    public void setStorageRoot(String storageRoot) { this.storageRoot = storageRoot; }
    public int getPreviewSize() { return previewSize; }
    public void setPreviewSize(int previewSize) { this.previewSize = previewSize; }
    public String getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(String allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    public Auth getAuth() { return auth; }

    public static class Auth {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
