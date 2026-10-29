package com.pw999.jsmd.scan;

import java.util.HexFormat;

/**
 * SHA-256 of a file's bytes and the number of bytes that were hashed.
 */
public record ContentHash(byte[] sha256, long size) {

    public String hex() {
        return HexFormat.of().formatHex(sha256);
    }

    @Override
    public String toString() {
        return "ContentHash[" + hex() + ", size=" + size + "]";
    }
}
