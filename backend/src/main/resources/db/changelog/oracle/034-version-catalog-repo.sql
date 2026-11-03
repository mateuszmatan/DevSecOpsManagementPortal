--liquibase formatted sql

--changeset dso-portal:034-version-catalog-repo dbms:oracle,h2
ALTER TABLE DSO_SERVICE ADD (
    VC_REPO_URL                 VARCHAR2(1000),
    VC_CREDENTIALS_ID           VARCHAR2(200),
    VC_BRANCH                   VARCHAR2(200),
    VC_PATH                     VARCHAR2(500)
);
--rollback ALTER TABLE DSO_SERVICE DROP (VC_REPO_URL, VC_CREDENTIALS_ID, VC_BRANCH, VC_PATH);
