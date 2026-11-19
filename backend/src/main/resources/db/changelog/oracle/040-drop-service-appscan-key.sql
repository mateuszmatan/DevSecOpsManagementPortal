--liquibase formatted sql

-- 040-drop-service-appscan-key removes the AppScan on Cloud API key of each service. The library signs in with the one
-- API key of the functional user, which its own defaults hold, so a service keeps only its AppScan application ID.
-- A rollback brings the columns back empty.

--changeset dso-portal:040-drop-service-appscan-key dbms:oracle,h2
ALTER TABLE DSO_SERVICE DROP (APPSCAN_KEY_ID, APPSCAN_SECRET_CREDENTIALS_ID);
--rollback ALTER TABLE DSO_SERVICE ADD (APPSCAN_KEY_ID VARCHAR2(200), APPSCAN_SECRET_CREDENTIALS_ID VARCHAR2(200));
