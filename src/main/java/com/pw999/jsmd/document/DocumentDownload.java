package com.pw999.jsmd.document;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/** A verified file ready to be streamed to the browser. */
public record DocumentDownload(Resource resource, String fileName, MediaType contentType) {
}
