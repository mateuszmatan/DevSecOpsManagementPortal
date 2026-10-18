package com.bbh.itss.dso.portal.domain.user;

import java.util.List;

public record UserProfile(
        Long id,
        String username,
        String displayName,
        String email,
        List<String> roles,
        List<String> departmentNames,
        boolean admin
) {
    public static UserProfile defaultAdmin() {
        return new UserProfile(
                1L,
                "mateusz.matan",
                "Mateusz Matan",
                "mateusz.matan@bbh.com",
                List.of(
                        "ROLE_ADMIN",
                        "ROLE_DEPT_AI_LAB",
                        "ROLE_DEPT_CAPITAL_PARTNERS",
                        "ROLE_DEPT_CORPORATE_TECHNOLOGY",
                        "ROLE_DEPT_CUSTODY",
                        "ROLE_DEPT_FUND_SERVICES"
                ),
                List.of(
                        "AI Lab",
                        "Capital Partners",
                        "Corporate Technology",
                        "Custody",
                        "Fund Services"
                ),
                true
        );
    }
}
