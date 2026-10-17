package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.user.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"/user/me", "/auth/me"})
    public UserProfile getCurrentUser() {
        log.debug("Fetching current user profile");
        return currentUserProvider.getCurrentUser();
    }
}
