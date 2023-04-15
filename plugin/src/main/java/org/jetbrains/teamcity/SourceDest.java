package org.jetbrains.teamcity;

import lombok.Data;

@Data
public class SourceDest {
    private String source;
    private String destDir;
    private String destName;

    public boolean hasCustomDest() {
        return hasDestDir() || hasDestName();
    }

    public boolean hasDestDir() {
        return destDir != null && !"".equalsIgnoreCase(destDir);
    }

    public boolean hasDestName() {
        return destName != null && !"".equalsIgnoreCase(destName);
    }
}
