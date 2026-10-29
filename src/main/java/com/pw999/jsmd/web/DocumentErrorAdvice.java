package com.pw999.jsmd.web;

import com.pw999.jsmd.document.DocumentNotFoundException;
import com.pw999.jsmd.document.FileChangedException;
import com.pw999.jsmd.document.FileUnavailableException;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders document failures in the standard layout. Exception handlers get a fresh model, so the
 * banner's username and the active menu entry are set here.
 */
@ControllerAdvice(assignableTypes = DocumentsController.class)
class DocumentErrorAdvice {

    @ExceptionHandler(DocumentNotFoundException.class)
    ModelAndView notFound(Principal principal) {
        return view("error/404", HttpStatus.NOT_FOUND, null, principal);
    }

    @ExceptionHandler(FileUnavailableException.class)
    ModelAndView unavailable(FileUnavailableException e, Principal principal) {
        return view("error/document-unavailable", HttpStatus.NOT_FOUND, "File not available", principal)
                .addObject("message", e.getMessage());
    }

    @ExceptionHandler(FileChangedException.class)
    ModelAndView changed(FileChangedException e, Principal principal) {
        return view("error/document-unavailable", HttpStatus.CONFLICT, "File changed", principal)
                .addObject("message", e.getMessage());
    }

    private static ModelAndView view(String name, HttpStatus status, String heading, Principal principal) {
        ModelAndView mav = new ModelAndView(name, status);
        mav.addObject("username", principal != null ? principal.getName() : null);
        mav.addObject("active", "documents");
        mav.addObject("heading", heading);
        return mav;
    }
}
