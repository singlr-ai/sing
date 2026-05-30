/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.api.ServerConnectionConfig;
import ai.singlr.sail.config.HostYaml;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.engine.SailPaths;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Signs in with a passkey and stores the resulting session token. Opens the control-plane {@code
 * /login} page in a browser (the passkey ceremony must run at the Relying Party origin), passing a
 * loopback {@code /callback} as the redirect target and a one-time {@code state} nonce; the page
 * redirects the minted session token back to that loopback, which this command captures and writes
 * to the client config so subsequent {@code sail} calls authenticate as the FDE.
 */
@Command(
    name = "login",
    description = "Sign in with a passkey and store a session token.",
    mixinStandardHelpOptions = true)
public final class LoginCommand implements Runnable {

  @Option(
      names = "--origin",
      description =
          "Control-plane origin, e.g. https://sail.example.dev. Defaults to the configured"
              + " webauthn origin from host.yaml.")
  private String origin;

  @Option(
      names = "--timeout",
      description = "Seconds to wait for the browser sign-in to complete.",
      defaultValue = "180")
  private int timeoutSeconds;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(
        spec,
        () -> {
          var resolvedOrigin = origin != null ? origin : configuredOrigin();
          if (resolvedOrigin == null) {
            throw new IllegalArgumentException(
                "No control-plane origin. Pass --origin or configure the webauthn block in"
                    + " host.yaml.");
          }
          var state = randomState();
          try (var callback = new LoopbackCallbackServer(state)) {
            callback.start();
            var url =
                resolvedOrigin
                    + "/login?redirect_uri="
                    + URLEncoder.encode(callback.redirectUri(), StandardCharsets.UTF_8)
                    + "&state="
                    + state;
            System.out.println("  Opening your browser to sign in:");
            System.out.println(Ansi.AUTO.string("    @|cyan " + url + "|@"));
            openBrowser(url);
            System.out.println("  Waiting for sign-in to complete…");
            var token = callback.awaitToken(Duration.ofSeconds(timeoutSeconds));
            var configPath = SailPaths.clientConfigPath();
            ServerConnectionConfig.saveSessionToken(token, configPath);
            System.out.println(
                Ansi.AUTO.string("  @|green ✓|@ Signed in. Session saved to " + configPath));
          }
        });
  }

  private static String configuredOrigin() throws IOException {
    var path = SailPaths.hostConfigPath();
    if (!Files.exists(path)) {
      return null;
    }
    var webauthn = HostYaml.fromMap(YamlUtil.parseFile(path)).webauthn();
    return webauthn.isConfigured() ? webauthn.origins().getFirst() : null;
  }

  private static void openBrowser(String url) {
    var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    var command = os.contains("mac") ? List.of("open", url) : List.of("xdg-open", url);
    try {
      new ProcessBuilder(command).start();
    } catch (IOException ignored) {
      System.out.println("  (Could not open a browser automatically — open the URL above.)");
    }
  }

  private static String randomState() {
    var bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
