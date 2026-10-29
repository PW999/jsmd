package com.pw999.jsmd.web;

import java.security.Principal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the signed-in username available to the layout banner on every page.
 */
@ControllerAdvice
class CurrentUserAdvice {

    @ModelAttribute("username")
    String username(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
