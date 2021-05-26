package dev.ferrand.chunky.bvh.benchmark;

import dev.ferrand.chunky.bvh.util.PackedTriangles;
import dev.ferrand.chunky.bvh.util.PackedTrianglesBuilder;
import org.openjdk.jmh.annotations.*;
import se.llbit.chunky.block.Air;
import se.llbit.math.Vector2;
import se.llbit.math.Vector3;
import se.llbit.math.primitive.TexturedTriangle;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class PackedTrianglesBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkData {
    public PackedTriangles triangles;

//    @Param({"2", "4", "8"})
    @Param({"2", "4", "8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096", "8192", "16384", "32768", "65536", "131072", "262144", "524288", "1048576"})
    public int n;

    @Setup
    public void setupArrayList() {
      PackedTrianglesBuilder builder = new PackedTrianglesBuilder();
      Random random = new Random();
      for(int i = 0; i < n; ++i) {
        Vector3 origin = new Vector3(
          random.nextGaussian() * 100,
          random.nextGaussian() * 100,
          random.nextGaussian() * 100
        );
        Vector2 zero = new Vector2(0, 0);
        builder.addTriangle(new TexturedTriangle(origin, origin, origin, zero, zero, zero, Air.INSTANCE));
      }
      triangles = builder.build();
    }
  }

  @Fork(value = 1, warmups = 1)
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @BenchmarkMode({Mode.Throughput})
  @Benchmark
  public void quickSort(BenchmarkData data) {
    data.triangles.quickSort(0, data.n, 0);
  }

  @Fork(value = 1, warmups = 1)
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @BenchmarkMode({Mode.Throughput})
  @Benchmark
  public void radixSort(BenchmarkData data) {
    data.triangles.radixSort(0, data.n, 0);
  }

  @Fork(value = 1, warmups = 1)
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @BenchmarkMode({Mode.Throughput})
  @Benchmark
  public void radixSortStable(BenchmarkData data) {
    data.triangles.radixSortStable(0, data.n, 0);
  }

  @Fork(value = 1, warmups = 1)
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @BenchmarkMode({Mode.Throughput})
  @Benchmark
  public void quickSortIndirect(BenchmarkData data) {
    data.triangles.quickSortIndirect(0, data.n, 0);
  }

  @Fork(value = 1, warmups = 1)
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @BenchmarkMode({Mode.Throughput})
  @Benchmark
  public void sort(BenchmarkData data) {
    data.triangles.sort(0, data.n, 0);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
