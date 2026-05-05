/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReleaseFetcherTest {

  @Test
  void buildDownloadUrlForBinary() {
    assertEquals(
        "https://github.com/singlr-ai/sing/releases/download/v0.9.2/sail",
        ReleaseFetcher.buildDownloadUrl("v0.9.2", "sail"));
  }

  @Test
  void buildDownloadUrlForChecksum() {
    assertEquals(
        "https://github.com/singlr-ai/sing/releases/download/v0.9.2/sing.sha256",
        ReleaseFetcher.buildDownloadUrl("v0.9.2", "sing.sha256"));
  }

  @Test
  void releaseAssetCandidatesIncludeLegacySingAssetForSailAsset() {
    assertEquals(
        java.util.List.of("sail-linux-amd64", "sing-linux-amd64"),
        ReleaseFetcher.releaseAssetCandidates("sail-linux-amd64"));
  }

  @Test
  void releaseAssetCandidatesDoNotRewriteLegacyAsset() {
    assertEquals(
        java.util.List.of("sing-linux-amd64"),
        ReleaseFetcher.releaseAssetCandidates("sing-linux-amd64"));
  }

  @Test
  void apiBasePointsToGitHub() {
    assertTrue(ReleaseFetcher.API_BASE.contains("api.github.com"));
    assertTrue(ReleaseFetcher.API_BASE.contains("singlr-ai/sing"));
  }

  @Test
  void downloadBasePointsToGitHubReleases() {
    assertTrue(ReleaseFetcher.DOWNLOAD_BASE.contains("github.com"));
    assertTrue(ReleaseFetcher.DOWNLOAD_BASE.contains("releases/download"));
  }

  @Test
  void githubRepoIsCorrect() {
    assertEquals("singlr-ai/sing", ReleaseFetcher.GITHUB_REPO);
  }
}
