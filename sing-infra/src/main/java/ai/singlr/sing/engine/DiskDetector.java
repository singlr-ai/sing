/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Detects available block devices by parsing {@code lsblk --json} output. Identifies candidate
 * disks for ZFS pool creation.
 */
public final class DiskDetector {

  private static final Set<String> SKIP_FSTYPES =
      Set.of("ext4", "xfs", "btrfs", "vfat", "swap", "ntfs");

  private final ShellExec shell;

  public DiskDetector(ShellExec shell) {
    this.shell = shell;
  }

  /** A candidate disk that could be used for the ZFS storage pool. */
  public record Candidate(String device, String size, String model, String reason) {}

  /** Detects candidate disks for ZFS pool creation. */
  public List<Candidate> detect() throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(
            List.of("lsblk", "--json", "--output", "NAME,SIZE,TYPE,MOUNTPOINT,FSTYPE,MODEL"));
    if (!result.ok()) {
      throw new IOException("lsblk failed: " + result.stderr());
    }
    if (result.stdout().isBlank()) {
      return List.of();
    }
    return parseAndFilter(result.stdout());
  }

  /** Parses lsblk JSON and filters to candidate disks. Visible for testing. */
  List<Candidate> parseAndFilter(String json) throws IOException {
    var lsblk = LsblkOutput.fromMap(YamlUtil.parseMap(json));
    var candidates = new ArrayList<Candidate>();

    for (var dev : lsblk.blockdevices()) {
      if (!"disk".equals(dev.type())) {
        continue;
      }

      if (isOsDisk(dev)) {
        continue;
      }

      if (hasZfsPartition(dev)) {
        candidates.add(
            new Candidate(
                "/dev/" + dev.name(),
                dev.size(),
                dev.model(),
                "Already has ZFS — can reuse or wipe"));
        continue;
      }

      if (isCompletelyUnused(dev)) {
        candidates.add(
            new Candidate(
                "/dev/" + dev.name(), dev.size(), dev.model(), "Unmounted, no filesystem"));
      }
    }

    return candidates;
  }

  /** A disk is the OS disk if it or any of its children are mounted at / or /boot. */
  private static boolean isOsDisk(BlockDevice dev) {
    if (isCriticalMount(dev.mountpoint())) {
      return true;
    }
    if (dev.children() != null) {
      for (var child : dev.children()) {
        if (isCriticalMount(child.mountpoint())) {
          return true;
        }
        if (child.children() != null) {
          for (var grandchild : child.children()) {
            if (isCriticalMount(grandchild.mountpoint())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isCriticalMount(String mountpoint) {
    return (mountpoint != null && (mountpoint.equals("/") || mountpoint.startsWith("/boot")));
  }

  private static boolean hasZfsPartition(BlockDevice dev) {
    if ("zfs_member".equals(dev.fstype())) {
      return true;
    }
    if (dev.children() != null) {
      for (var child : dev.children()) {
        if ("zfs_member".equals(child.fstype())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A disk is completely unused if it has no filesystem and none of its children have a recognized
   * filesystem or mountpoint.
   */
  private static boolean isCompletelyUnused(BlockDevice dev) {
    if (dev.fstype() != null && SKIP_FSTYPES.contains(dev.fstype())) {
      return false;
    }
    if (dev.mountpoint() != null) {
      return false;
    }
    if (dev.children() != null) {
      for (var child : dev.children()) {
        if (child.mountpoint() != null) {
          return false;
        }
        if (child.fstype() != null && SKIP_FSTYPES.contains(child.fstype())) {
          return false;
        }
        if (child.children() != null) {
          for (var grandchild : child.children()) {
            if (grandchild.mountpoint() != null) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  record LsblkOutput(List<BlockDevice> blockdevices) {
    @SuppressWarnings("unchecked")
    static LsblkOutput fromMap(Map<String, Object> map) {
      var devicesRaw = (List<Map<String, Object>>) map.get("blockdevices");
      return new LsblkOutput(
          devicesRaw != null ? devicesRaw.stream().map(BlockDevice::fromMap).toList() : List.of());
    }
  }

  record BlockDevice(
      String name,
      String size,
      String type,
      String mountpoint,
      String fstype,
      String model,
      List<BlockDevice> children) {
    @SuppressWarnings("unchecked")
    static BlockDevice fromMap(Map<String, Object> map) {
      var childrenRaw = (List<Map<String, Object>>) map.get("children");
      return new BlockDevice(
          (String) map.get("name"),
          (String) map.get("size"),
          (String) map.get("type"),
          (String) map.get("mountpoint"),
          (String) map.get("fstype"),
          (String) map.get("model"),
          childrenRaw != null ? childrenRaw.stream().map(BlockDevice::fromMap).toList() : null);
    }
  }
}
