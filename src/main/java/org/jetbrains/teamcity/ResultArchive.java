package org.jetbrains.teamcity;

import lombok.Data;
import org.codehaus.plexus.archiver.FileSet;

import java.io.File;
import java.util.List;

@Data
public class ResultArchive {
    private final String type;
    private final List<FileSet> fileSets;
    private final File file;
}
