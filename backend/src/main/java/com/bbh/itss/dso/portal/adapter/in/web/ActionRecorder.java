package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionLogUseCase;
import com.bbh.itss.dso.portal.application.actionlog.port.in.ActionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Runs a change the user asked for and records it, failed or done, in the Action History. */
@Slf4j
@Component
@RequiredArgsConstructor
class ActionRecorder {

    private final ActionLogUseCase actionLog;
    private final CurrentUserProvider users;

    <T> T record(ActionRecord action, Supplier<T> change, BiFunction<ActionRecord, ? super T, ActionRecord> done) {
        ActionRecord byUser = action.withUser(users.getCurrentUser());
        T result;
        try {
            result = change.get();
        } catch (RuntimeException failure) {
            save(byUser.failed(failure.getMessage()));
            throw failure;
        }
        save(done.apply(byUser, result));
        return result;
    }

    <T> T record(ActionRecord action, Supplier<T> change, String details) {
        return record(action, change, (recorded, result) -> recorded.done(details));
    }

    void record(ActionRecord action, Runnable change, String details) {
        record(action, () -> {
            change.run();
            return action;
        }, details);
    }

    private void save(ActionRecord action) {
        log.info("{} {} {} '{}': {}", action.user().username(), action.success() ? "did" : "failed to do",
                action.actionType(), action.resourceName(), action.details());
        try {
            actionLog.record(action);
        } catch (RuntimeException e) {
            log.warn("Could not record {} of '{}' in the action history", action.actionType(), action.resourceName(), e);
        }
    }
}
