/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.ssh;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the single command string SSH delivers in {@code SSH_ORIGINAL_COMMAND} into an argument
 * vector, honoring single quotes, double quotes, and backslash escapes the way a POSIX shell would.
 * The gateway parses the command itself — rather than handing it to a shell — so it can authorize
 * the request against an allow-list before executing, with no shell ever interpreting the string.
 */
public final class CommandTokenizer {

  private CommandTokenizer() {}

  /** Tokenizes {@code input}. Throws {@link IllegalArgumentException} on an unterminated quote. */
  public static List<String> split(String input) {
    var tokens = new ArrayList<String>();
    var current = new StringBuilder();
    var inToken = false;
    var inSingle = false;
    var inDouble = false;

    for (var i = 0; i < input.length(); i++) {
      var c = input.charAt(i);
      if (inSingle) {
        if (c == '\'') {
          inSingle = false;
        } else {
          current.append(c);
        }
        inToken = true;
      } else if (inDouble) {
        if (c == '"') {
          inDouble = false;
        } else if (c == '\\'
            && i + 1 < input.length()
            && isDoubleQuoteEscape(input.charAt(i + 1))) {
          current.append(input.charAt(++i));
        } else {
          current.append(c);
        }
        inToken = true;
      } else if (c == '\'') {
        inSingle = true;
        inToken = true;
      } else if (c == '"') {
        inDouble = true;
        inToken = true;
      } else if (c == '\\' && i + 1 < input.length()) {
        current.append(input.charAt(++i));
        inToken = true;
      } else if (Character.isWhitespace(c)) {
        if (inToken) {
          tokens.add(current.toString());
          current.setLength(0);
          inToken = false;
        }
      } else {
        current.append(c);
        inToken = true;
      }
    }
    if (inSingle || inDouble) {
      throw new IllegalArgumentException("Unterminated quote in command.");
    }
    if (inToken) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  private static boolean isDoubleQuoteEscape(char next) {
    return next == '"' || next == '\\' || next == '$' || next == '`';
  }
}
