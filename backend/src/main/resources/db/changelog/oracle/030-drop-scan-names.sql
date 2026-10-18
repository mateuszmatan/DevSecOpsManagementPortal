--liquibase formatted sql

--changeset dso-portal:030-drop-scan-names dbms:oracle,h2
ALTER TABLE DSO_SERVICE DROP (SAST_SCAN_NAME, DAST_SCAN_NAME, SCA_SCAN_NAME);
--rollback ALTER TABLE DSO_SERVICE ADD (SAST_SCAN_NAME VARCHAR2(200), DAST_SCAN_NAME VARCHAR2(200),
--rollback     SCA_SCAN_NAME VARCHAR2(200));
