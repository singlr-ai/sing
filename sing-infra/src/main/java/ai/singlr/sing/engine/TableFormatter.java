/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Help.Ansi;

/**
 * Renders bordered CLI tables with dynamically computed column widths. Columns auto-size to fit the
 * widest cell in each column (including headers), ensuring aligned borders regardless of content
 * length.
 */
final class TableFormatter {

  private static final int MIN_WIDTH = 56;
  private static final int COL_GAP = 2;

  private final String title;
  private final List<String> headers;
  private final List<List<String>> rows = new ArrayList<>();

  TableFormatter(String title, List<String> headers) {
    this.title = title;
    this.headers = headers;
  }

  void addRow(String... cells) {
    rows.add(List.of(cells));
  }

  void render(PrintStream out, Ansi ansi) {
    var ncols = headers.size();
    var widths = new int[ncols];
    for (var i = 0; i < ncols; i++) {
      widths[i] = headers.get(i).length();
    }
    for (var row : rows) {
      for (var i = 0; i < ncols && i < row.size(); i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }

    var contentWidth = 2;
    for (var i = 0; i < ncols; i++) {
      contentWidth += widths[i];
      if (i < ncols - 1) {
        contentWidth += COL_GAP;
      }
    }
    var w = Math.max(MIN_WIDTH, contentWidth + 1);

    out.println();
    out.println(
        ansi.string(
            "  @|bold,cyan \u256d\u2500"
                + title
                + "\u2500".repeat(Math.max(0, w - title.length() - 1))
                + "\u256e|@"));

    out.println(ansi.string("  @|bold,cyan \u2502|@" + " ".repeat(w) + "@|bold,cyan \u2502|@"));

    var headerRow = formatRow(headers, widths);
    var headerUsed = 2 + headerRow.length();
    var headerPad = w - headerUsed;
    out.println(
        ansi.string(
            "  @|bold,cyan \u2502|@  @|bold "
                + headerRow
                + "|@"
                + (headerPad > 0 ? " ".repeat(headerPad) : "")
                + "@|bold,cyan \u2502|@"));

    var sep = "\u2500".repeat(w - 4);
    out.println(ansi.string("  @|bold,cyan \u2502|@  @|faint " + sep + "|@  @|bold,cyan \u2502|@"));

    for (var row : rows) {
      var rowStr = formatRow(row, widths);
      var rowUsed = 2 + rowStr.length();
      var rowPad = w - rowUsed;
      out.println(
          ansi.string(
              "  @|bold,cyan \u2502|@  "
                  + rowStr
                  + (rowPad > 0 ? " ".repeat(rowPad) : "")
                  + "@|bold,cyan \u2502|@"));
    }

    out.println(ansi.string("  @|bold,cyan \u2502|@" + " ".repeat(w) + "@|bold,cyan \u2502|@"));
    out.println(ansi.string("  @|bold,cyan \u2570" + "\u2500".repeat(w) + "\u256f|@"));
    out.println();
  }

  private String formatRow(List<String> cells, int[] widths) {
    var sb = new StringBuilder();
    for (var i = 0; i < widths.length; i++) {
      var cell = i < cells.size() ? cells.get(i) : "";
      if (i < widths.length - 1) {
        sb.append(String.format("%-" + widths[i] + "s", cell));
        sb.append(" ".repeat(COL_GAP));
      } else {
        sb.append(cell);
      }
    }
    return sb.toString();
  }
}
