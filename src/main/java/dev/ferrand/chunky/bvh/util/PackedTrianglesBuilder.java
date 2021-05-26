package dev.ferrand.chunky.bvh.util;

import it.unimi.dsi.fastutil.floats.FloatBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import se.llbit.chunky.world.Material;
import se.llbit.math.primitive.TexturedTriangle;

import java.util.BitSet;

public class PackedTrianglesBuilder {
  private final FloatBigArrayBigList points = new FloatBigArrayBigList(); // 9 floats per primitive
  private final FloatBigArrayBigList uv = new FloatBigArrayBigList(); // 6 floats per primitive
  private final IntBigArrayBigList materialIds = new IntBigArrayBigList(); // 1 int per primitive
  private final BitSet doubleSided = new BitSet(); // 1 bit per primitive
  private final ObjectArrayList<Material> materialPalette = new ObjectArrayList<>();
  private final Object2IntOpenHashMap<Material> materialToIdx = new Object2IntOpenHashMap<>();
  private int count = 0;

  public void addTriangle(TexturedTriangle triangle) {
    // TODO Is it better to copy to an array and call addElements
    points.add((float) triangle.o.x);
    points.add((float) triangle.o.y);
    points.add((float) triangle.o.z);
    points.add((float) triangle.e1.x);
    points.add((float) triangle.e1.y);
    points.add((float) triangle.e1.z);
    points.add((float) triangle.e2.x);
    points.add((float) triangle.e2.y);
    points.add((float) triangle.e2.z);

    uv.add((float) triangle.t1u);
    uv.add((float) triangle.t1v);
    uv.add((float) triangle.t2u);
    uv.add((float) triangle.t2v);
    uv.add((float) triangle.t3u);
    uv.add((float) triangle.t3v);

    if(materialToIdx.containsKey(triangle.material)) {
      materialIds.add(materialToIdx.getInt(triangle.material));
    } else {
      int materialIndex = materialPalette.size();
      materialPalette.add(triangle.material);
      materialToIdx.put(triangle.material, materialIndex);
      materialIds.add(materialIndex);
    }

    doubleSided.set(count, triangle.doubleSided);

    ++count;
  }

  public PackedTriangles build() {
    points.trim();
    uv.trim();
    materialIds.trim();
    materialPalette.trim();
    return new PackedTriangles(
            points.elements(),
            uv.elements(),
            materialIds.elements(),
            doubleSided,
            materialPalette.toArray(new Material[0]),
            count
    );
  }
}
