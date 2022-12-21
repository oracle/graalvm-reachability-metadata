/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_spring_boot_starter.controller;

import com_baomidou.dynamic_datasource_spring_boot_starter.service.spel.User;
import com_baomidou.dynamic_datasource_spring_boot_starter.service.spel.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/users")
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/session")
    public List<User> session(HttpServletRequest request) {
        request.getSession().setAttribute("tenantName", "tenant1");
        return userService.selectSpelBySession();
    }

    @GetMapping("/header")
    public String header() {
        userService.selectSpelByHeader();
        return "success";
    }
}
