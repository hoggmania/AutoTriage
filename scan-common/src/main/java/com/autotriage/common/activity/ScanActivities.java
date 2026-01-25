package com.autotriage.common.activity;

import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionBundle;

public interface ScanActivities {

    ArtifactRef resolveRepoSource(ScanRequest request);

    SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef);

    boolean verifySuppressionSignature(ArtifactRef bundle);

    ArtifactRef runOpenGrep(ArtifactRef source, String runId);

    SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif,
                                                   ArtifactRef suppressionBundle,
                                                   ArtifactRef sourceArchive,
                                                   ScanRequest request);

    void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif, ArtifactRef suppressionReport);

    ScanStatus computeVerdict(String runId, ArtifactRef finalSarif);
}
