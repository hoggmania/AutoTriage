package com.autotriage.worker.opengrep.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import org.jboss.logging.Logger;

public class OpenGrepScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(OpenGrepScanActivities.class);

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
        log.infov("runOpenGrep runId={0} sourceUri={1}", runId, source.getUri());
        return new ArtifactRef("stub://sarif/raw/" + runId, "sarif-raw");
    }

    @Override
    public ArtifactRef applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        throw new UnsupportedOperationException("applySuppressions is handled by filter worker");
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
