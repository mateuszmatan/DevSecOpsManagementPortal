package com.bbh.itss.dso.portal.domain.user;

public record UserProfile(String username, String displayName, String email, boolean admin) {

    public static UserProfile defaultAdmin() {
        return new UserProfile("mateusz.matan", "Mateusz Matan", "mateusz.matan@bbh.com", true);
    }
}
