package com.pw999.jsmd.scan;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which directories are scanned and how often.
 *
 * @param enabled     master switch; nothing is scanned when false
 * @param directories scan roots, made absolute and normalised; none may be inside another
 * @param interval    delay between the end of one scan and the start of the next
 * @param settleTime  files modified more recently than this before a scan starts are left for the next scan
 */
@Validated
@ConfigurationProperties("jsmd.scan")
public record ScanProperties(boolean enabled, List<Path> directories, @NotNull Duration interval,
                             @NotNull Duration settleTime) {

    public ScanProperties {
        directories = directories == null ? List.of()
                : directories.stream().map(d -> d.toAbsolutePath().normalize()).toList();
        for (int i = 0; i < directories.size(); i++) {
            for (int j = 0; j < directories.size(); j++) {
                if (i != j && directories.get(i).startsWith(directories.get(j))) {
                    throw new IllegalArgumentException("jsmd.scan.directories must not overlap: "
                            + directories.get(i) + " is inside " + directories.get(j));
                }
            }
        }
    }

    /** True when a scan has something to do. */
    public boolean active() {
        return enabled && !directories.isEmpty();
    }
}
