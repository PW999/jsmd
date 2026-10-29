package com.pw999.jsmd.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class ExternalizedConfigurationTest {

    @Configuration
    static class ConfigOnly {
    }

    @Nested
    @SpringBootTest(classes = ConfigOnly.class)
    class Defaults {

        @Autowired
        Environment env;

        @Test
        void usesBundledDefaults() {
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/jsmd");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("jsmd");
            assertThat(env.getProperty("server.port")).isEqualTo("8080");
        }
    }

    @Nested
    @SpringBootTest(classes = ConfigOnly.class, properties = {
            "JSMD_DB_URL=jdbc:postgresql://db.example:5433/other",
            "JSMD_DB_USER=alice",
            "JSMD_DB_PASSWORD=secret",
            "PORT=9191"
    })
    class Overrides {

        @Autowired
        Environment env;

        @Test
        void environmentOverridesBundledFile() {
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example:5433/other");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("alice");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret");
            assertThat(env.getProperty("server.port")).isEqualTo("9191");
        }
    }
}
