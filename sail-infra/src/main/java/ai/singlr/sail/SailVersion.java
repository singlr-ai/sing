/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail;

import java.io.IOException;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/**
 * Provides the CLI version from Maven-filtered {@code version.properties} on the classpath.
 * Implements picocli's {@link IVersionProvider} so {@code sail -V} reads the version automatically.
 */
public final class SailVersion implements IVersionProvider {

  private static final String VERSION;

  static {
    var props = new Properties();
    try (var in = SailVersion.class.getResourceAsStream("/version.properties")) {
      if (in != null) {
        props.load(in);
      }
    } catch (IOException ignored) {
    }
    VERSION = props.getProperty("version", "dev");
  }

  /** Returns the version string (e.g. {@code "1.6.0"} or {@code "dev"} in IDE). */
  public static String version() {
    return VERSION;
  }

  @Override
  public String[] getVersion() {
    return new String[] {"sail " + VERSION};
  }
}
