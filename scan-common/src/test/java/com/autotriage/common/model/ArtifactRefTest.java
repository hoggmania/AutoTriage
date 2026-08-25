package com.autotriage.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtifactRefTest {
    @Test
    void roundTripsDurableReferenceThroughJackson() throws Exception {
        String digest = "d".repeat(64);
        ArtifactRef expected = new ArtifactRef("artifact://test/" + ArtifactRef.contentAddressedKey(digest),
                "source", digest, 6, "application/octet-stream", ArtifactRef.contentAddressedKey(digest));

        ArtifactRef actual = new ObjectMapper().readValue(new ObjectMapper().writeValueAsBytes(expected), ArtifactRef.class);

        assertEquals(expected.getUri(), actual.getUri());
        assertEquals(expected.getSha256(), actual.getSha256());
    }

    @Test
    void roundTripsLegacyReferenceForTemporalHistoryReplay() throws Exception {
        ArtifactRef expected = new ArtifactRef("none://suppressions", "suppression-bundle");

        ArtifactRef actual = new ObjectMapper().readValue(new ObjectMapper().writeValueAsBytes(expected), ArtifactRef.class);

        assertEquals(expected.getUri(), actual.getUri());
        assertEquals(false, actual.isDurable());
    }

    @Test
    void durableReferenceCarriesImmutableContentMetadata() {
        String digest = "a".repeat(64);
        ArtifactRef ref = new ArtifactRef("s3://artifacts/sha256/aa/" + digest, "source-archive",
                digest, 42, "application/gzip", "sha256/aa/" + digest);

        assertEquals(digest, ref.getSha256());
        assertEquals(42, ref.getSizeBytes());
        assertEquals("application/gzip", ref.getMediaType());
        assertEquals("sha256/aa/" + digest, ref.getObjectKey());
    }

    @Test
    void rejectsLocalFileReferences() {
        String digest = "c".repeat(64);
        assertThrows(IllegalArgumentException.class,
                () -> new ArtifactRef("file:///tmp/input", "source-archive", digest, 1,
                        "application/octet-stream", ArtifactRef.contentAddressedKey(digest)));
    }

    @Test
    void rejectsObjectKeyThatDoesNotMatchDigest() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArtifactRef("s3://artifacts/wrong", "source-archive", "b".repeat(64), 1,
                        "application/octet-stream", "wrong"));
    }
}
