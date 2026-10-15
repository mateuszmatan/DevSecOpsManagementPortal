package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.Map;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record OpenShiftTarget(String projectBuild, String buildConfigPath, String dockerFilePath, String buildContext,
                              String addFile, String dockerRepoPush, String dockerRepoPull, String certDir,
                              String nexusAuthFile, String projectDeployment, String deployConfigPath,
                              String configPath, Boolean skipConfigDeploy, String healthCheckUrl, String routeHostname,
                              String deploymentPath, String deploymentRepoUrl, String deploymentRepoBranch,
                              String deploymentRepoCredentialsId, String buildTag, String internalDockerUrl) {

    public OpenShiftTarget {
        projectBuild = trimToNull(projectBuild);
        buildConfigPath = trimToNull(buildConfigPath);
        dockerFilePath = trimToNull(dockerFilePath);
        buildContext = trimToNull(buildContext);
        addFile = trimToNull(addFile);
        dockerRepoPush = trimToNull(dockerRepoPush);
        dockerRepoPull = trimToNull(dockerRepoPull);
        certDir = trimToNull(certDir);
        nexusAuthFile = trimToNull(nexusAuthFile);
        projectDeployment = trimToNull(projectDeployment);
        deployConfigPath = trimToNull(deployConfigPath);
        configPath = trimToNull(configPath);
        skipConfigDeploy = isTrue(skipConfigDeploy);
        healthCheckUrl = trimToNull(healthCheckUrl);
        routeHostname = trimToNull(routeHostname);
        deploymentPath = trimToNull(deploymentPath);
        deploymentRepoUrl = trimToNull(deploymentRepoUrl);
        deploymentRepoBranch = trimToNull(deploymentRepoBranch);
        deploymentRepoCredentialsId = trimToNull(deploymentRepoCredentialsId);
        buildTag = trimToNull(buildTag);
        internalDockerUrl = trimToNull(internalDockerUrl);
    }

    public static final OpenShiftTarget NONE = builder().build();

    public void validateImageBuild(ValidationProblems problems) {
        String message = "is required for OpenShift: the Nexus snapshot delivery builds the image in the RD project";
        problems.require("projectBuild", projectBuild, message).require("buildConfigPath", buildConfigPath, message)
                .require("dockerFilePath", dockerFilePath, message).require("buildContext", buildContext, message)
                .require("dockerRepoPush", dockerRepoPush, message).require("nexusAuthFile", nexusAuthFile, message);
    }

    public boolean isEmpty() {
        return toConfig().isEmpty();
    }

    public Map<String, Object> toConfig() {
        ConfigTree entry = new ConfigTree()
                .set("projectBuildR", projectBuild)
                .set("buildConfigPath", buildConfigPath)
                .set("dockerFilePath", dockerFilePath)
                .set("buildContext", buildContext)
                .set("addFile", addFile)
                .set("qcDockerRepoPush", dockerRepoPush)
                .set("qcDockerRepoPull", dockerRepoPull)
                .set("openshiftCertDir", certDir)
                .set("nexus.authfile", nexusAuthFile)
                .set("projectDeploymentR", projectDeployment)
                .set("deployConfigPath", deployConfigPath)
                .set("configPathR", configPath)
                .set("healthCheckUrl", healthCheckUrl)
                .set("routeHostnameR", routeHostname)
                .set("deploymentPath", deploymentPath)
                .set("deploymentRepo.url", deploymentRepoUrl)
                .set("deploymentRepo.branch", deploymentRepoBranch)
                .set("deploymentRepo.credentials", deploymentRepoCredentialsId)
                .set("buildTag", buildTag)
                .set("internalDockerUrl", internalDockerUrl)
                .flag("skipConfigDeploy", skipConfigDeploy);
        return entry.toMap();
    }
}
