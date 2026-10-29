package com.pw999.jsmd.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("active", "home");
        return "home";
    }
}
