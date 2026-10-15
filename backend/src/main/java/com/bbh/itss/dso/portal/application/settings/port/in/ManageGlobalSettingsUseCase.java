package com.bbh.itss.dso.portal.application.settings.port.in;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;

public interface ManageGlobalSettingsUseCase {

    GlobalSettings ensureExists();

    GlobalSettings current();

    GlobalSettings update(Long expectedVersion, GlobalSettingsValues values);
}
