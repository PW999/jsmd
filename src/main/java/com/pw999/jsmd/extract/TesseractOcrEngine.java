package com.pw999.jsmd.extract;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * {@link OcrEngine} implementation that binds to the Tesseract C API via the Java Foreign Function & Memory API.
 *
 * <p>One engine session corresponds to one {@code TessBaseAPI} handle; it is initialised on construction
 * and always terminated and deleted on {@link #close()}.
 *
 * <p>The JVM must be started with {@code --enable-native-access=ALL-UNNAMED}.
 */
public class TesseractOcrEngine implements OcrEngine {

    // --- MethodHandles for the eight Tesseract C API functions ---

    private final MethodHandle tessBaseAPICreate;
    private final MethodHandle tessBaseAPIInit3;
    private final MethodHandle tessBaseAPISetImage;
    private final MethodHandle tessBaseAPISetSourceResolution;
    private final MethodHandle tessBaseAPIGetUTF8Text;
    private final MethodHandle tessDeleteText;
    private final MethodHandle tessBaseAPIEnd;
    private final MethodHandle tessBaseAPIDelete;

    /** The active API handle ({@code TessBaseAPI*}); null-segment means not yet created or already deleted. */
    private final MemorySegment apiHandle;

    /** Arena used for native string arguments; confined to this object's construction thread. */
    private final Arena arena;

    /**
     * Loads the Tesseract library and creates a new OCR session initialised with the given tessdata and languages.
     *
     * @param libraryPath   library name (e.g. {@code libtesseract.so.5.5}) or absolute path
     * @param tessdataPath  directory containing {@code .traineddata} files, or null for Tesseract's default
     * @param joinedLanguages {@code +}-joined language codes, e.g. {@code eng+nld}
     * @throws OcrException if the library cannot be loaded or {@code Init3} returns non-zero
     */
    public TesseractOcrEngine(String libraryPath, Path tessdataPath, String joinedLanguages) {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib;
        try {
            lib = SymbolLookup.libraryLookup(libraryPath, Arena.global());
        } catch (Exception e) {
            throw new OcrException("Cannot load Tesseract library '" + libraryPath + "': " + e.getMessage(), e);
        }

        tessBaseAPICreate = linker.downcallHandle(
                lib.find("TessBaseAPICreate").orElseThrow(() -> new OcrException("Symbol TessBaseAPICreate not found in " + libraryPath)),
                FunctionDescriptor.of(ValueLayout.ADDRESS));

        tessBaseAPIInit3 = linker.downcallHandle(
                lib.find("TessBaseAPIInit3").orElseThrow(() -> new OcrException("Symbol TessBaseAPIInit3 not found")),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,   // TessBaseAPI*
                        ValueLayout.ADDRESS,   // datapath (const char*)
                        ValueLayout.ADDRESS)); // language (const char*)

        tessBaseAPISetImage = linker.downcallHandle(
                lib.find("TessBaseAPISetImage").orElseThrow(() -> new OcrException("Symbol TessBaseAPISetImage not found")),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,   // TessBaseAPI*
                        ValueLayout.ADDRESS,   // imagedata (const unsigned char*)
                        ValueLayout.JAVA_INT,  // width
                        ValueLayout.JAVA_INT,  // height
                        ValueLayout.JAVA_INT,  // bytes_per_pixel
                        ValueLayout.JAVA_INT)); // bytes_per_line

        tessBaseAPISetSourceResolution = linker.downcallHandle(
                lib.find("TessBaseAPISetSourceResolution").orElseThrow(() -> new OcrException("Symbol TessBaseAPISetSourceResolution not found")),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,   // TessBaseAPI*
                        ValueLayout.JAVA_INT)); // ppi

        tessBaseAPIGetUTF8Text = linker.downcallHandle(
                lib.find("TessBaseAPIGetUTF8Text").orElseThrow(() -> new OcrException("Symbol TessBaseAPIGetUTF8Text not found")),
                FunctionDescriptor.of(ValueLayout.ADDRESS,  // char* (must be freed)
                        ValueLayout.ADDRESS));               // TessBaseAPI*

        tessDeleteText = linker.downcallHandle(
                lib.find("TessDeleteText").orElseThrow(() -> new OcrException("Symbol TessDeleteText not found")),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)); // char*

        tessBaseAPIEnd = linker.downcallHandle(
                lib.find("TessBaseAPIEnd").orElseThrow(() -> new OcrException("Symbol TessBaseAPIEnd not found")),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)); // TessBaseAPI*

        tessBaseAPIDelete = linker.downcallHandle(
                lib.find("TessBaseAPIDelete").orElseThrow(() -> new OcrException("Symbol TessBaseAPIDelete not found")),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)); // TessBaseAPI*

        // Create the API handle and initialise
        arena = Arena.ofConfined();
        MemorySegment handle;
        try {
            handle = (MemorySegment) tessBaseAPICreate.invokeExact();
        } catch (Throwable t) {
            arena.close();
            throw new OcrException("TessBaseAPICreate failed", t);
        }

        MemorySegment datapathSeg = tessdataPath != null
                ? arena.allocateFrom(tessdataPath.toAbsolutePath().toString())
                : MemorySegment.NULL;
        MemorySegment langSeg = arena.allocateFrom(joinedLanguages);

        int initResult;
        try {
            initResult = (int) tessBaseAPIInit3.invokeExact(handle, datapathSeg, langSeg);
        } catch (Throwable t) {
            safeDelete(handle);
            arena.close();
            throw new OcrException("TessBaseAPIInit3 threw an exception", t);
        }
        if (initResult != 0) {
            safeDelete(handle);
            arena.close();
            throw new OcrException("TessBaseAPIInit3 returned " + initResult
                    + " for languages='" + joinedLanguages + "'"
                    + (tessdataPath != null ? ", tessdata='" + tessdataPath + "'" : ""));
        }
        apiHandle = handle;
    }

    @Override
    public String recognise(BufferedImage image, int dpi) {
        // Convert to 8-bit grayscale
        BufferedImage gray = toGrayscale(image);
        byte[] pixels = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();

        int width = gray.getWidth();
        int height = gray.getHeight();
        int bytesPerLine = width; // 1 byte per pixel for grayscale

        // Copy image bytes into a confined native memory segment
        try (Arena imgArena = Arena.ofConfined()) {
            MemorySegment imgSeg = imgArena.allocate(pixels.length);
            MemorySegment.copy(pixels, 0, imgSeg, ValueLayout.JAVA_BYTE, 0, pixels.length);

            try {
                tessBaseAPISetImage.invokeExact(apiHandle, imgSeg, width, height, 1, bytesPerLine);
                tessBaseAPISetSourceResolution.invokeExact(apiHandle, dpi);
            } catch (Throwable t) {
                throw new OcrException("Failed to set image on Tesseract API", t);
            }

            MemorySegment textPtr;
            try {
                textPtr = (MemorySegment) tessBaseAPIGetUTF8Text.invokeExact(apiHandle);
            } catch (Throwable t) {
                throw new OcrException("TessBaseAPIGetUTF8Text failed", t);
            }

            try {
                if (textPtr.equals(MemorySegment.NULL)) {
                    return "";
                }
                // Reinterpret as a null-terminated C string of unknown length, then copy to Java String
                MemorySegment reinterpreted = textPtr.reinterpret(Long.MAX_VALUE);
                return reinterpreted.getString(0);
            } finally {
                // Always release the Tesseract-allocated string
                try {
                    tessDeleteText.invokeExact(textPtr);
                } catch (Throwable ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            tessBaseAPIEnd.invokeExact(apiHandle);
        } catch (Throwable ignored) {
            // best-effort
        }
        safeDelete(apiHandle);
        try {
            arena.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    // --- helpers ---

    private void safeDelete(MemorySegment handle) {
        try {
            tessBaseAPIDelete.invokeExact(handle);
        } catch (Throwable ignored) {
            // best-effort cleanup
        }
    }

    private static BufferedImage toGrayscale(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return src;
        }
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            // Transparent pixels would otherwise default to black
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, gray.getWidth(), gray.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return gray;
    }
}
