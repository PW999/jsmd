package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ExtractPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ExtractProperties.class)
    static class Config {
    }

    /** Minimal valid properties (tessdata-path is optional and omitted). */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "jsmd.extract.enabled=true",
                    "jsmd.extract.poll-interval=PT10S",
                    "jsmd.extract.ocr-languages=en",
                    "jsmd.extract.tesseract-library=libtesseract.so.5.5",
                    "jsmd.extract.render-dpi=300");

    @Test
    void defaultsAreApplied() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ExtractProperties p = context.getBean(ExtractProperties.class);
            assertThat(p.enabled()).isTrue();
            assertThat(p.pollInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(p.ocrLanguages()).containsExactly("en");
            assertThat(p.tessdataPath()).isNull();
            assertThat(p.tesseractLibrary()).isEqualTo("libtesseract.so.5.5");
            assertThat(p.renderDpi()).isEqualTo(300);
        });
    }

    @Test
    void multiLanguageListIsBound() {
        runner.withPropertyValues("jsmd.extract.ocr-languages=en,nl,deu").run(context -> {
            assertThat(context).hasNotFailed();
            ExtractProperties p = context.getBean(ExtractProperties.class);
            assertThat(p.ocrLanguages()).containsExactly("en", "nl", "deu");
        });
    }

    @Test
    void invalidDpiFailsStartup() {
        runner.withPropertyValues("jsmd.extract.render-dpi=30").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void disabledIsBindable() {
        runner.withPropertyValues("jsmd.extract.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExtractProperties.class).enabled()).isFalse();
        });
    }
}
