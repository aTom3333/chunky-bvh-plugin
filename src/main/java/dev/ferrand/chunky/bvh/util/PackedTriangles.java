package dev.ferrand.chunky.bvh.util;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import se.llbit.chunky.block.Air;
import se.llbit.chunky.world.Material;
import se.llbit.math.AABB;
import se.llbit.math.Ray;
import se.llbit.math.Vector2;
import se.llbit.math.Vector3;
import se.llbit.math.primitive.TexturedTriangle;

import java.util.BitSet;
public class PackedTriangles {
  private final float[][] points; // 9 floats per primitive
  private final float[][] uv; // 6 floats per primitive
  private final int[][] materialIds; // 1 int per primitive
  private final BitSet doubleSided; // 1 bit per primitive
  private final Material[] materialPalette;
  public final int count;

  PackedTriangles(float[][] points, float[][] uv, int[][] materialIds, BitSet doubleSided, Material[] materialPalette, int count) {
    this.points = points;
    this.uv = uv;
    this.materialIds = materialIds;
    this.doubleSided = doubleSided;
    this.materialPalette = materialPalette;
    this.count = count;
  }

  private float[] computeCenters(int from, int to, int axis) {
    final float[] centers = new float[to-from];
    for(int index = from; index < to; ++index) {
      float origin = BigArrays.get(points, 9L * index + axis);
      float min = origin;
      float max = origin;
      for(int i = 1; i < 3; ++i) {
        float coordinate = BigArrays.get(points, 9L * index + i * 3 + axis) + origin;
        min = Math.min(min, coordinate);
        max = Math.max(max, coordinate);
      }
      centers[index-from] = min + max;
    }
    return centers;
  }
  
  private void swap(int indexA, int indexB) {
    // swap points
    for(int i = 0; i < 9; ++i) {
      BigArrays.swap(points, 9L * indexA + i, 9L * indexB + i);
    }

    // swap uv
    for(int i = 0; i < 6; ++i) {
      BigArrays.swap(uv, 6L * indexA + i, 6L * indexB + i);
    }

    // swap material
    BigArrays.swap(materialIds, indexA, indexB);

    // swap doubleSided
    boolean isADoubleSided = doubleSided.get(indexA);
    doubleSided.set(indexA, doubleSided.get(indexB));
    doubleSided.set(indexB, isADoubleSided);
  }

  private void move(int from, int to) {
    BigArrays.copy(points, 9L*from, points, 9L*to, 9);
    BigArrays.copy(uv, 6L*from, uv, 6L*to, 6);
    BigArrays.set(materialIds, to, BigArrays.get(materialIds, from));
    doubleSided.set(to, doubleSided.get(from));
  }

  public void quickSort(int from, int to, int axis) {
    final float[] centers = computeCenters(from, to, axis);

    BigArrays.quickSort(from, to,
      (a, b) -> {
        float acenter = centers[(int) (a-from)];
        float bcenter = centers[(int) (b-from)];

        return Float.compare(acenter, bcenter);
      },
      (a, b) -> {
        swap((int)a, (int)b);

        // swap centers
        float aCenter = centers[(int) (a-from)];
        centers[(int) (a - from)] = centers[(int) (b-from)];
        centers[(int) (b-from)] = aCenter;
      });
  }

  public void quickSortIndirect(int from, int to, int axis) {
    final float[] centers = computeCenters(from, to, axis);
    final int[] indexes = makeIndexes(to-from);

    BigArrays.quickSort(from, to,
      (a, b) -> {
        float acenter = centers[indexes[(int) (a-from)]];
        float bcenter = centers[indexes[(int) (b-from)]];

        return Float.compare(acenter, bcenter);
      },
      (a, b) -> {
        // swap indexes
        int aIndex = indexes[(int) (a-from)];
        indexes[(int) (a - from)] = indexes[(int) (b-from)];
        indexes[(int) (b-from)] = aIndex;
      });

    permute(indexes, from);
  }
  
  private class TempTriangle {
    final float[] pointsTemp = new float[9];
    final float[] uvTemp = new float[6];
    int materialIdTemp;
    boolean doubleSidedTemp;
    
    void readFromPacked(int index) {
      BigArrays.copyFromBig(points, 9L * index, pointsTemp, 0, 9);
      BigArrays.copyFromBig(uv, 6L*index, uvTemp, 0, 6);
      materialIdTemp = BigArrays.get(materialIds, index);
      doubleSidedTemp = doubleSided.get(index);
    }
    
    void writeToPacked(int index) {
      BigArrays.copyToBig(pointsTemp, 0, points, 9L*index, 9);
      BigArrays.copyToBig(uvTemp, 0, uv, 6L*index, 6);
      BigArrays.set(materialIds, index, materialIdTemp);
      doubleSided.set(index, doubleSidedTemp);
    }
  }

  private int[] makeIndexes(int n) {
    final int[] indexes = new int[n];
    for(int i = 0; i < n; ++i) {
      indexes[i] = i;
    }
    return indexes;
  }

  public void radixSort(int from, int to, int axis) {
    final float[] centers = computeCenters(from, to, axis);
    final int[] indexes = makeIndexes(to-from);

    FloatArrays.radixSortIndirect(indexes, centers, false);

    permute(indexes, from);
  }

  public void radixSortStable(int from, int to, int axis) {
    final float[] centers = computeCenters(from, to, axis);
    final int[] indexes = makeIndexes(to-from);

    FloatArrays.radixSortIndirect(indexes, centers, true);

    permute(indexes, from);
  }

  public void sort(int from, int to, int axis) {
    // It has been determined that quickSort is better for n < 2048 and radixSortStable is better for n > 2048
    if(to-from > 2048)
      radixSortStable(from, to, axis);
    else
      quickSort(from, to, axis);
  }

  private void permute(int[] indexes, int from) {
    TempTriangle temp = new TempTriangle();

    int indexIndex = 0;
    while(indexIndex < indexes.length) {
      while(indexes[indexIndex] == -1) {
        ++indexIndex;
        if(indexIndex >= indexes.length)
          return;
      }

      int index = indexes[indexIndex];
      int startIndex = index;
      temp.readFromPacked(index + from);
      int previousIndex;
      while(true) {
        previousIndex = index;
        index = indexes[index];
        indexes[previousIndex] = -1; // Mark as done

        if(index == startIndex)
          break;

        move(index + from, previousIndex + from);
      }
      temp.writeToPacked(previousIndex + from);

      ++indexIndex;
    }
  }

  public AABB computeAABB(int from, int to) {
    float xmin = Float.POSITIVE_INFINITY;
    float xmax = Float.NEGATIVE_INFINITY;
    float ymin = Float.POSITIVE_INFINITY;
    float ymax = Float.NEGATIVE_INFINITY;
    float zmin = Float.POSITIVE_INFINITY;
    float zmax = Float.NEGATIVE_INFINITY;

    for (int index = from; index < to; ++index) {
      long triangleBaseIndex = 9L * index;
      float originx = BigArrays.get(points, triangleBaseIndex);
      float originy = BigArrays.get(points, triangleBaseIndex+1);
      float originz = BigArrays.get(points, triangleBaseIndex+2);
      if (originx < xmin)
        xmin = originx;
      if (originx > xmax)
        xmax = originx;
      if (originy < ymin)
        ymin = originy;
      if (originy > ymax)
        ymax = originy;
      if (originz < zmin)
        zmin = originz;
      if (originz > zmax)
        zmax = originz;
      for(int i = 1; i < 3; ++i) {
        long pointBaseIndex = triangleBaseIndex + 3 * i;
        float x = BigArrays.get(points, pointBaseIndex) + originx;
        float y = BigArrays.get(points, pointBaseIndex+1) + originy;
        float z = BigArrays.get(points, pointBaseIndex+2) + originz;
        if (x < xmin)
          xmin = x;
        if (x > xmax)
          xmax = x;
        if (y < ymin)
          ymin = y;
        if (y > ymax)
          ymax = y;
        if (z < zmin)
          zmin = z;
        if (z > zmax)
          zmax = z;
      }
    }
    return new AABB(xmin, xmax, ymin, ymax, zmin, zmax);
  }

  public void expandAABB(AABB aabb, int index) {
    long triangleBaseIndex = 9L * index;
    float originx = BigArrays.get(points, triangleBaseIndex);
    float originy = BigArrays.get(points, triangleBaseIndex+1);
    float originz = BigArrays.get(points, triangleBaseIndex+2);
    if (originx < aabb.xmin)
      aabb.xmin = originx;
    if (originx > aabb.xmax)
      aabb.xmax = originx;
    if (originy < aabb.ymin)
      aabb.ymin = originy;
    if (originy > aabb.ymax)
      aabb.ymax = originy;
    if (originz < aabb.zmin)
      aabb.zmin = originz;
    if (originz > aabb.zmax)
      aabb.zmax = originz;
    for(int i = 1; i < 3; ++i) {
      long pointBaseIndex = triangleBaseIndex + 3*i;
      float x = BigArrays.get(points, pointBaseIndex) + originx;
      float y = BigArrays.get(points, pointBaseIndex+1) + originy;
      float z = BigArrays.get(points, pointBaseIndex+2) + originz;
      if (x < aabb.xmin)
        aabb.xmin = x;
      if (x > aabb.xmax)
        aabb.xmax = x;
      if (y < aabb.ymin)
        aabb.ymin = y;
      if (y > aabb.ymax)
        aabb.ymax = y;
      if (z < aabb.zmin)
        aabb.zmin = z;
      if (z > aabb.zmax)
        aabb.zmax = z;
    }
  }

  public boolean intersect(int index, Ray ray) {
    // Möller-Trumbore triangle intersection algorithm!
    Vector3 pvec = new Vector3();
    Vector3 qvec = new Vector3();
    Vector3 tvec = new Vector3();

    long pointsBaseIndex = 9L * index;

    float e1x = BigArrays.get(points, pointsBaseIndex + 3);
    float e1y = BigArrays.get(points, pointsBaseIndex + 4);
    float e1z = BigArrays.get(points, pointsBaseIndex + 5);
    float e2x = BigArrays.get(points, pointsBaseIndex + 6);
    float e2y = BigArrays.get(points, pointsBaseIndex + 7);
    float e2z = BigArrays.get(points, pointsBaseIndex + 8);

    // pvec.cross(ray.d, e2);
    pvec.set(
      ray.d.y * e2z - ray.d.z * e2y,
      ray.d.z * e2x - ray.d.x * e2z,
      ray.d.x * e2y - ray.d.y * e2x
    );
    // double det = e1.dot(pvec);
    double det = pvec.x * e1x + pvec.y * e1y + pvec.z * e1z;
    if (doubleSided.get(index)) {
      if (det > -Ray.EPSILON && det < Ray.EPSILON) {
        return false;
      }
    } else if (det > -Ray.EPSILON) {
      return false;
    }
    double recip = 1 / det;

    float ox = BigArrays.get(points, pointsBaseIndex);
    float oy = BigArrays.get(points, pointsBaseIndex + 1);
    float oz = BigArrays.get(points, pointsBaseIndex + 2);

    // tvec.sub(ray.o, o);
    tvec.set(
      ray.o.x - ox,
      ray.o.y - oy,
      ray.o.z - oz
    );

    double u = tvec.dot(pvec) * recip;

    if (u < 0 || u > 1) {
      return false;
    }

    // qvec.cross(tvec, e1);
    qvec.set(
      tvec.y * e1z - tvec.z * e1y,
      tvec.z * e1x - tvec.x * e1z,
      tvec.x * e1y - tvec.y * e1x
    );

    double v = ray.d.dot(qvec) * recip;

    if (v < 0 || (u + v) > 1) {
      return false;
    }

    // double t = e2.dot(qvec) * recip;
    double t = (e2x * qvec.x + e2y * qvec.y + e2z * qvec.z) * recip;

    if (t > Ray.EPSILON && t < ray.t) {
      double w = 1 - u - v;

      long uvBaseIndex = 6L * index;
      float t1u = BigArrays.get(uv, uvBaseIndex);
      float t1v = BigArrays.get(uv, uvBaseIndex + 1);
      float t2u = BigArrays.get(uv, uvBaseIndex + 2);
      float t2v = BigArrays.get(uv, uvBaseIndex + 3);
      float t3u = BigArrays.get(uv, uvBaseIndex + 4);
      float t3v = BigArrays.get(uv, uvBaseIndex + 5);

      ray.u = t1u * u + t2u * v + t3u * w;
      ray.v = t1v * u + t2v * v + t3v * w;

      Material material = materialPalette[BigArrays.get(materialIds, index)];
      float[] color = material.getColor(ray.u, ray.v);
      if (color[3] > 0) {
        ray.color.set(color);
        ray.setCurrentMaterial(material);
        ray.t = t;

        // n.cross(e2, e1);
        ray.n.set(
          e2y * e1z - e2z * e1y,
          e2z * e1x - e2x * e1z,
          e2x * e1y - e2y * e1x
        );
        ray.n.normalize();
        return true;
      }
    }
    return false;
  }


  public static void main(String[] args) {
    PackedTrianglesBuilder builder = new PackedTrianglesBuilder();
    Vector3 zero = new Vector3(0, 0, 0);
    Vector2 zero2 = new Vector2(0, 0);
    builder.addTriangle(new TexturedTriangle(new Vector3(2, 0, 0), zero, zero, zero2, zero2, zero2, Air.INSTANCE));
    builder.addTriangle(new TexturedTriangle(new Vector3(0, 0, 0), zero, zero, zero2, zero2, zero2, Air.INSTANCE));
    builder.addTriangle(new TexturedTriangle(new Vector3(1, 0, 0), zero, zero, zero2, zero2, zero2, Air.INSTANCE));

    PackedTriangles triangles = builder.build();

    triangles.radixSort(0, 3, 0);
  }
}
