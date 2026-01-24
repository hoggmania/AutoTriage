package com.autotriage.worker.light.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import org.jboss.logging.Logger;

public class LightScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(LightScanActivities.class);

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        log.infov("resolveRepoSource runId={0} repo={1} sha={2}", request.getRunId(), request.getRepository(), request.getCommitSha());
        return new ArtifactRef("stub://source/" + request.getRunId(), "source-archive");
    }

    @Override
    public ArtifactRef fetchSuppressionBundle(String repository, String ref) {
        log.infov("fetchSuppressionBundle repo={0} ref={1}", repository, ref);
        return new ArtifactRef("stub://suppressions/" + ref, "suppression-bundle");
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        log.infov("verifySuppressionSignature uri={0}", bundle.getUri());
        return true;
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public ArtifactRef applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        throw new UnsupportedOperationException("applySuppressions is handled by filter worker");
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif) {
        log.infov("uploadResults runId={0} finalUri={1} rawUri={2}", runId, finalSarif.getUri(), rawSarif.getUri());
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        log.infov("computeVerdict runId={0} finalUri={1}", runId, finalSarif.getUri());
        return new ScanStatus(runId, ScanState.COMPLETED, "Stub verdict: PASS");
    }
}
