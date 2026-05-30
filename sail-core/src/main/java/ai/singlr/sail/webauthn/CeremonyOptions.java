/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.webauthn;

import ai.singlr.sail.config.YamlUtil;
import java.util.Map;

/**
 * The options a ceremony hands to the browser's {@code navigator.credentials.create}/{@code get} (a
 * {@code PublicKeyCredentialCreationOptions} or {@code PublicKeyCredentialRequestOptions}),
 * together with the raw {@code challenge} the server must retain to match the matching {@code
 * finishRegistration}/{@code finishAssertion} call.
 */
public record CeremonyOptions(byte[] challenge, Map<String, Object> publicKey) {

  /** The options serialized as JSON for delivery to the client. */
  public String json() {
    return YamlUtil.dumpJson(publicKey);
  }
}
