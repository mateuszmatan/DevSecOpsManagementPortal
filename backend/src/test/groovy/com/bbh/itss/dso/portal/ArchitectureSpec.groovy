package com.bbh.itss.dso.portal

import com.bbh.itss.dso.portal.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        DsoPortalApplication
    }

    @Override
    String httpAdapter() {
        'com.bbh.itss.dso.portal.adapter.out.influx..'
    }
}
