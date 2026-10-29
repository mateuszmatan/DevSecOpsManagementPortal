package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/** The services a config file describes, its pipeline section, and notes on what the file holds beyond them. */
public record ConfigImportResponse(List<ImportedService> services, ImportedPipeline pipeline, List<FieldProblem> notes) {

    /** One project of the file as a new service, with notes on the values the service does not take over. */
    public record ImportedService(String name, ServiceDto service, List<FieldProblem> notes) {
    }

    /** The pipeline type and Jenkins agents of a rendered pipeline config. */
    public record ImportedPipeline(PipelineType type, List<String> agentLabels) {

        public ImportedPipeline {
            agentLabels = List.copyOf(emptyIfNull(agentLabels));
        }
    }
}
