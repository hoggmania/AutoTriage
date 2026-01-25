package com.autotriage.common.activity;

import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.ScanStatus;

public interface ScanActivities {

    ArtifactRef resolveRepoSource(ScanRequest request);

    ArtifactRef fetchSuppressionBundle(String repository, String ref);

    boolean verifySuppressionSignature(ArtifactRef bundle);

    ArtifactRef runOpenGrep(ArtifactRef source, String runId);

    SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle);

    void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif, ArtifactRef suppressionReport);

    ScanStatus computeVerdict(String runId, ArtifactRef finalSarif);
}
