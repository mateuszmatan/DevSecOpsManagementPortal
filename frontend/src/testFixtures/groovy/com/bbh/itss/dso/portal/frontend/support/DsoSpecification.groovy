package com.bbh.itss.dso.portal.frontend.support

abstract class DsoSpecification extends GuiSpecification {

    static final List<String> MENU = ['Pipelines', 'Self-service', 'Pipeline Monitoring', 'Admin']

    @Override
    StubApi newApi() {
        new DsoStubApi()
    }
}
