package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetManager backed by a filesystem root, for loading external asset packs.
 */
public class FilesystemAssetManager implements AssetManager {
  private final Path root;

  public FilesystemAssetManager(Path root) {
    this.root = root == null ? Paths.get(".") : root;
  }

  @Override
  public boolean exists(AssetType type, String name) {
    return Files.exists(resolve(type, name));
  }

  @Override
  public URL url(AssetType type, String name) {
    try {
      Path p = resolve(type, name);
      if (!Files.exists(p)) return null;
      return p.toUri().toURL();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    Path p = resolve(type, name);
    return Files.newInputStream(p);
  }

  @Override
  public List<String> list(String directory) {
    Path dir = root.resolve(directory);
    if (!Files.isDirectory(dir)) return List.of();
    try (var stream = Files.list(dir)) {
      List<String> names = new ArrayList<>();
      stream.forEach(p -> names.add(p.getFileName().toString()));
      return names;
    } catch (IOException e) {
      return List.of();
    }
  }

  private Path resolve(AssetType type, String name) {
    String rel = AssetPaths.build(type, name);
    return root.resolve(rel);
  }
}
