package com.pw999.jsmd.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warns at startup when the well-known development admin account is in use.
 */
@Component
@Profile("dev")
class DevAdminAccountWarning {

    private static final Logger log = LoggerFactory.getLogger(DevAdminAccountWarning.class);

    private final AdminProperties admin;

    DevAdminAccountWarning(AdminProperties admin) {
        this.admin = admin;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warn() {
        if (usesDevAccount(admin)) {
            log.warn("The 'dev' profile is active and the development admin account '{}' is in use. "
                    + "Never run this configuration outside local development.", admin.username());
        }
    }

    static boolean usesDevAccount(AdminProperties admin) {
        return "admin".equals(admin.username()) && "admin".equals(admin.password());
    }
}
