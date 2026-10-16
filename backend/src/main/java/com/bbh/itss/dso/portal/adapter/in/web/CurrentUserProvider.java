package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.user.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public UserProfile getCurrentUser() {
        return UserProfile.defaultAdmin();
    }
}
