package com.autotriage.worker.filter.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import org.jboss.logging.Logger;

public class FilterScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(FilterScanActivities.class);

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        throw new UnsupportedOperationException("resolveRepoSource is handled by light worker");
    }

    @Override
    public ArtifactRef fetchSuppressionBundle(String repository, String ref) {
        throw new UnsupportedOperationException("fetchSuppressionBundle is handled by light worker");
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        throw new UnsupportedOperationException("verifySuppressionSignature is handled by light worker");
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public ArtifactRef applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        log.infov("applySuppressions rawUri={0} suppressionUri={1}", rawSarif.getUri(), suppressionBundle.getUri());
        return new ArtifactRef("stub://sarif/final/" + rawSarif.getUri(), "sarif-final");
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif) {
        throw new UnsupportedOperationException("uploadResults is handled by light worker");
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        throw new UnsupportedOperationException("computeVerdict is handled by light worker");
    }
}
