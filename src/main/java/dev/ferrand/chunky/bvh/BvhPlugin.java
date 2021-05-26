package dev.ferrand.chunky.bvh;

import dev.ferrand.chunky.bvh.implementation.PackedSahMaBVH;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.ui.ChunkyFx;

public class BvhPlugin implements Plugin {
  @Override
  public void attach(Chunky chunky) {
    PackedSahMaBVH.addImplementation();
  }

  public static void main(String[] args) {
    // Start Chunky normally with this plugin attached.
    Chunky.loadDefaultTextures();
    Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
    new BvhPlugin().attach(chunky);
    ChunkyFx.startChunkyUI(chunky);
  }
}
