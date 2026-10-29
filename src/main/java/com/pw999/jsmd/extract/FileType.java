package com.pw999.jsmd.extract;

import java.nio.file.Path;
import java.util.Set;

/**
 * Classifies a file by extension (case-insensitive). No byte-sniffing — the extension is the contract.
 */
public enum FileType {

    PDF, IMAGE, UNSUPPORTED;

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff");

    /**
     * Returns the {@link FileType} for the given path, based solely on its file-name extension.
     * A path with no extension or an unrecognised extension returns {@link #UNSUPPORTED}.
     */
    public static FileType of(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return UNSUPPORTED;
        }
        String ext = name.substring(dot + 1).toLowerCase();
        if ("pdf".equals(ext)) {
            return PDF;
        }
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return IMAGE;
        }
        return UNSUPPORTED;
    }
}
