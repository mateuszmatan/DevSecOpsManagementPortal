package com.bbh.itss.dso.portal.application.actionlog.port.in;

import com.bbh.itss.dso.portal.domain.user.UserProfile;
import lombok.With;

/**
 * A change a user made, as the Action History shows it. The department is the one the changed resource belongs
 * to, when it belongs to one.
 */
@With
public record ActionRecord(UserProfile user, String actionType, String resourceType, String resourceName,
                           Long departmentId, String details, boolean success) {

    public static ActionRecord of(String actionType, String resourceType, String resourceName, Long departmentId) {
        return new ActionRecord(null, actionType, resourceType, resourceName, departmentId, null, true);
    }

    public ActionRecord done(String details) {
        return withDetails(details).withSuccess(true);
    }

    public ActionRecord failed(String reason) {
        return withDetails(reason).withSuccess(false);
    }
}
