/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.webauthn;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies WebAuthn ceremonies for one Relying Party (one {@code rpId} and its allowed origins),
 * implementing the W3C WebAuthn §7.1 registration steps for passwordless passkeys. Attestation is
 * not verified (we request {@code none}); trust comes from the registration being an authenticated,
 * user-verifying ceremony, not from an attestation chain.
 */
public final class RelyingParty {

  private static final int CHALLENGE_BYTES = 32;
  private static final int TIMEOUT_MS = 300_000;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

  private final String rpId;
  private final String rpName;
  private final Set<String> origins;
  private final byte[] rpIdHash;

  public RelyingParty(String rpId, String rpName, Set<String> origins) {
    this.rpId = Objects.requireNonNull(rpId, "rpId");
    this.rpName = Objects.requireNonNull(rpName, "rpName");
    this.origins = Set.copyOf(origins);
    this.rpIdHash = Hashes.sha256(rpId.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Builds the {@code PublicKeyCredentialCreationOptions} for a registration: a fresh challenge,
   * the RP and user identities, the supported algorithms (ES256, RS256, EdDSA), and passwordless-
   * passkey selection criteria (resident key + user verification required, no attestation). {@code
   * excludeCredentialIds} prevents re-registering an already-enrolled authenticator.
   */
  public CeremonyOptions startRegistration(
      byte[] userId, String userName, String displayName, List<byte[]> excludeCredentialIds) {
    var challenge = randomChallenge();
    var user = new LinkedHashMap<String, Object>();
    user.put("id", B64URL.encodeToString(userId));
    user.put("name", userName);
    user.put("displayName", displayName);

    var options = new LinkedHashMap<String, Object>();
    options.put("rp", orderedMap("id", rpId, "name", rpName));
    options.put("user", user);
    options.put("challenge", B64URL.encodeToString(challenge));
    options.put(
        "pubKeyCredParams",
        List.of(credParam(CoseKey.ES256), credParam(CoseKey.RS256), credParam(CoseKey.EDDSA)));
    options.put("timeout", TIMEOUT_MS);
    options.put(
        "authenticatorSelection",
        orderedMap("residentKey", "required", "userVerification", "required"));
    options.put("attestation", "none");
    if (!excludeCredentialIds.isEmpty()) {
      options.put("excludeCredentials", descriptors(excludeCredentialIds));
    }
    return new CeremonyOptions(challenge, options);
  }

  /**
   * Builds the {@code PublicKeyCredentialRequestOptions} for an assertion: a fresh challenge, the
   * RP id, and user verification required. {@code allowCredentialIds} may be empty for discoverable
   * passkeys (the user picks any credential).
   */
  public CeremonyOptions startAssertion(List<byte[]> allowCredentialIds) {
    var challenge = randomChallenge();
    var options = new LinkedHashMap<String, Object>();
    options.put("challenge", B64URL.encodeToString(challenge));
    options.put("timeout", TIMEOUT_MS);
    options.put("rpId", rpId);
    options.put("userVerification", "required");
    if (!allowCredentialIds.isEmpty()) {
      options.put("allowCredentials", descriptors(allowCredentialIds));
    }
    return new CeremonyOptions(challenge, options);
  }

  private static byte[] randomChallenge() {
    var challenge = new byte[CHALLENGE_BYTES];
    RANDOM.nextBytes(challenge);
    return challenge;
  }

  private static Map<String, Object> credParam(long alg) {
    return orderedMap("type", "public-key", "alg", alg);
  }

  private static List<Map<String, Object>> descriptors(List<byte[]> credentialIds) {
    var list = new ArrayList<Map<String, Object>>();
    for (var id : credentialIds) {
      list.add(orderedMap("type", "public-key", "id", B64URL.encodeToString(id)));
    }
    return list;
  }

  private static Map<String, Object> orderedMap(String k1, Object v1, String k2, Object v2) {
    var map = new LinkedHashMap<String, Object>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  /**
   * Verifies a registration response and returns the credential to persist. Throws {@link
   * IllegalArgumentException} if any check fails.
   *
   * @param clientDataJson the raw {@code clientDataJSON} bytes
   * @param attestationObject the raw CBOR attestation object
   * @param expectedChallenge the challenge issued for this ceremony
   */
  public RegisteredCredential finishRegistration(
      byte[] clientDataJson, byte[] attestationObject, byte[] expectedChallenge) {
    var clientData = ClientData.parse(clientDataJson);
    if (!ClientData.TYPE_CREATE.equals(clientData.type())) {
      throw new IllegalArgumentException(
          "clientDataJSON type must be " + ClientData.TYPE_CREATE + ", got " + clientData.type());
    }
    if (!MessageDigest.isEqual(clientData.challenge(), expectedChallenge)) {
      throw new IllegalArgumentException("Registration challenge mismatch");
    }
    if (!origins.contains(clientData.origin())) {
      throw new IllegalArgumentException("Registration origin not allowed: " + clientData.origin());
    }

    if (!(Cbor.decode(attestationObject) instanceof Map<?, ?> attestation)) {
      throw new IllegalArgumentException("Attestation object is not a CBOR map");
    }
    if (!(attestation.get("authData") instanceof byte[] authDataBytes)) {
      throw new IllegalArgumentException("Attestation object missing authData");
    }
    var authData = AuthenticatorData.parse(authDataBytes);
    if (!MessageDigest.isEqual(authData.rpIdHash(), rpIdHash)) {
      throw new IllegalArgumentException("Registration rpIdHash mismatch");
    }
    if (!authData.userPresent()) {
      throw new IllegalArgumentException("User presence (UP) flag not set");
    }
    if (!authData.userVerified()) {
      throw new IllegalArgumentException("User verification (UV) flag not set");
    }
    var attested = authData.attestedCredential();
    if (attested == null) {
      throw new IllegalArgumentException("Registration has no attested credential data");
    }
    return new RegisteredCredential(
        attested.credentialId(),
        attested.publicKeyCose(),
        attested.publicKey().algorithm(),
        authData.signCount(),
        attested.aaguid(),
        authData.backupEligible(),
        authData.backupState());
  }

  /**
   * Verifies an assertion (W3C WebAuthn §7.2) against the credential the caller looked up by
   * credential id. Returns the new signature counter (to persist) or throws if any check fails.
   *
   * @param credentialPublicKeyCose the stored COSE public key (as returned at registration)
   * @param storedSignCount the signature counter persisted from the last ceremony
   * @param clientDataJson the raw {@code clientDataJSON} bytes
   * @param authenticatorData the raw authenticator data
   * @param signature the authenticator's signature over {@code authenticatorData ‖ clientDataHash}
   * @param expectedChallenge the challenge issued for this ceremony
   */
  public AssertionResult finishAssertion(
      byte[] credentialPublicKeyCose,
      long storedSignCount,
      byte[] clientDataJson,
      byte[] authenticatorData,
      byte[] signature,
      byte[] expectedChallenge) {
    var clientData = ClientData.parse(clientDataJson);
    if (!ClientData.TYPE_GET.equals(clientData.type())) {
      throw new IllegalArgumentException(
          "clientDataJSON type must be " + ClientData.TYPE_GET + ", got " + clientData.type());
    }
    if (!MessageDigest.isEqual(clientData.challenge(), expectedChallenge)) {
      throw new IllegalArgumentException("Assertion challenge mismatch");
    }
    if (!origins.contains(clientData.origin())) {
      throw new IllegalArgumentException("Assertion origin not allowed: " + clientData.origin());
    }

    var authData = AuthenticatorData.parse(authenticatorData);
    if (!MessageDigest.isEqual(authData.rpIdHash(), rpIdHash)) {
      throw new IllegalArgumentException("Assertion rpIdHash mismatch");
    }
    if (!authData.userPresent()) {
      throw new IllegalArgumentException("User presence (UP) flag not set");
    }
    if (!authData.userVerified()) {
      throw new IllegalArgumentException("User verification (UV) flag not set");
    }

    if (!(Cbor.decode(credentialPublicKeyCose) instanceof Map<?, ?> coseMap)) {
      throw new IllegalArgumentException("Stored credential public key is not a CBOR map");
    }
    var key = CoseKey.parse(coseMap);
    if (!verifySignature(key, concat(authenticatorData, clientData.hash()), signature)) {
      throw new IllegalArgumentException("Assertion signature is invalid");
    }

    var newSignCount = authData.signCount();
    if ((newSignCount != 0 || storedSignCount != 0) && newSignCount <= storedSignCount) {
      throw new IllegalArgumentException(
          "Signature counter did not increase (possible cloned authenticator)");
    }
    return new AssertionResult(newSignCount, authData.userVerified(), authData.backupState());
  }

  private static boolean verifySignature(CoseKey key, byte[] signedData, byte[] signature) {
    try {
      var verifier = Signature.getInstance(key.jdkSignatureAlgorithm());
      verifier.initVerify(key.publicKey());
      verifier.update(signedData);
      return verifier.verify(signature);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Could not verify assertion signature", e);
    }
  }

  private static byte[] concat(byte[] a, byte[] b) {
    var out = new ByteArrayOutputStream(a.length + b.length);
    out.writeBytes(a);
    out.writeBytes(b);
    return out.toByteArray();
  }
}
