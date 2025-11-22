package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tries a primary AssetManager, then falls back to a secondary one.
 */
public class OverlayAssetManager implements AssetManager {
  private final AssetManager primary;
  private final AssetManager fallback;

  public OverlayAssetManager(AssetManager primary, AssetManager fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public boolean exists(AssetType type, String name) {
    if (primary != null && primary.exists(type, name)) return true;
    return fallback != null && fallback.exists(type, name);
  }

  @Override
  public URL url(AssetType type, String name) {
    URL u = primary != null ? primary.url(type, name) : null;
    if (u != null) return u;
    return fallback != null ? fallback.url(type, name) : null;
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    if (primary != null && primary.exists(type, name)) return primary.open(type, name);
    if (fallback != null) return fallback.open(type, name);
    throw new IOException("Asset not found: " + name);
  }

  @Override
  public List<String> list(String directory) {
    Set<String> names = new LinkedHashSet<>();
    if (primary != null) names.addAll(primary.list(directory));
    if (fallback != null) names.addAll(fallback.list(directory));
    return new ArrayList<>(names);
  }
}
