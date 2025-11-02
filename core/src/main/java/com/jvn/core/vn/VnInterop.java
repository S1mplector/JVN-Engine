package com.jvn.core.vn;

public interface VnInterop {
  /**
   * Handle an external interop call coming from a VNS script EXTERNAL node.
   * Implementations may modify the scene/state, open overlays, or run code.
   */
  void handle(VnExternalCommand command, VnScene scene);
}
