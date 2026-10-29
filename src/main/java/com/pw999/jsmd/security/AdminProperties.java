package com.pw999.jsmd.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The single administrator account. Both values are mandatory and have no defaults.
 */
@Validated
@ConfigurationProperties("jsmd.admin")
public record AdminProperties(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "AdminProperties[username=" + username + ", password=****]";
    }
}
