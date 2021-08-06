package dev.ferrand.chunky.bvh.implementation;

import dev.ferrand.chunky.bvh.util.PackedTriangles;
import dev.ferrand.chunky.bvh.util.PackedTrianglesBuilder;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.math3.util.FastMath;
import se.llbit.chunky.entity.Entity;
import se.llbit.math.AABB;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;
import se.llbit.math.bvh.BVH;
import se.llbit.math.primitive.Primitive;
import se.llbit.math.primitive.TexturedTriangle;
import se.llbit.util.TaskTracker;

import java.util.Collection;

import static se.llbit.math.Ray.OFFSET;

public class PackedSahMaBVH implements BVH {

  public static final int SPLIT_LIMIT = 5;

  public static void addImplementation() {
    Factory.addBVHBuilder(new Factory.BVHBuilder() {
      @Override
      public BVH create(Collection<Entity> entities, Vector3 origin, TaskTracker.Task task) {
        task.update(1000, 0);
        PackedTrianglesBuilder builder = new PackedTrianglesBuilder();
        double entityScaler = 500.0 / entities.size();
        int done = 0;
        for(Entity entity : entities) {
          Collection<Primitive> primitives = entity.primitives(origin);
          for(Primitive primitive : primitives) {
            if(primitive instanceof TexturedTriangle) {
              builder.addTriangle((TexturedTriangle) primitive);
            } else {
              // Not Supported, defer to SAH_MA
              builder = null;
              return Factory.create("SAH_MA", entities, origin, task);
            }
          }
          done++;
          task.updateInterval((int) (done * entityScaler), 1);
        }

        return new PackedSahMaBVH(builder.build(), task);
      }

      @Override
      public String getName() {
        return "PACKED_SAH_MA";
      }

      @Override
      public String getDescription() {
        return "Memory efficient, fast and nearly optimal BVH building method";
      }
    });
  }

  private final PackedTriangles triangles;
  private final FloatArrayList bbox = new FloatArrayList();
  private final IntArrayList children = new IntArrayList();
  private final int rootIndex;

  public PackedSahMaBVH(PackedTriangles triangles, TaskTracker.Task task) {
    this.triangles = triangles;
    rootIndex = construct(task);
  }

  private enum Action {
    PUSH,
    MERGE,
  }

  private int construct(TaskTracker.Task task) {
    int progress = 0;

    IntStack nodes = new IntArrayList();
    Stack<Action> actions = new ObjectArrayList<>();
    Stack<IntIntImmutablePair> chunks = new ObjectArrayList<>();
    chunks.push(new IntIntImmutablePair(0, triangles.count));
    actions.push(Action.PUSH);
    while (!actions.isEmpty()) {
      Action action = actions.pop();
      if (action == Action.MERGE) {
        int groupIndex = children.size() / 2;
        int left = nodes.popInt();
        int right = nodes.popInt();
        children.add(left);
        children.add(right);
        bbox.add(Math.min(bbox.getFloat(6*left), bbox.getFloat(6*right))); // xmin
        bbox.add(Math.max(bbox.getFloat(6*left+1), bbox.getFloat(6*right+1))); // xmax
        bbox.add(Math.min(bbox.getFloat(6*left+2), bbox.getFloat(6*right+2))); // ymin
        bbox.add(Math.max(bbox.getFloat(6*left+3), bbox.getFloat(6*right+3))); // ymax
        bbox.add(Math.min(bbox.getFloat(6*left+4), bbox.getFloat(6*right+4))); // zmin
        bbox.add(Math.max(bbox.getFloat(6*left+5), bbox.getFloat(6*right+5))); // zmax
        nodes.push(groupIndex);
      } else {
        IntIntImmutablePair chunk = chunks.pop();
        if (chunk.rightInt() - chunk.leftInt() < SPLIT_LIMIT) {
          int chunkIndex = children.size() / 2;

          children.add(-chunk.leftInt()-1); // Primitive index is negated and decremented
          children.add(chunk.rightInt() - chunk.leftInt()); // store size in second
          AABB bb = triangles.computeAABB(chunk.leftInt(), chunk.rightInt());
          bbox.add((float) bb.xmin);
          bbox.add((float) bb.xmax);
          bbox.add((float) bb.ymin);
          bbox.add((float) bb.ymax);
          bbox.add((float) bb.zmin);
          bbox.add((float) bb.zmax);

          nodes.push(chunkIndex);

          progress += chunk.rightInt() - chunk.leftInt();
          task.updateInterval((int) (progress * 500.0/triangles.count) + 500, 1);
        } else {
          split(chunk, actions, chunks);
        }
      }
    }
    return nodes.popInt();
  }

  private float surfaceArea(AABB aabb) {
    double xdiff = aabb.xmax - aabb.xmin;
    double ydiff = aabb.ymax - aabb.ymin;
    double zdiff = aabb.zmax - aabb.zmin;

    return (float) (xdiff*ydiff + xdiff*zdiff + ydiff*zdiff);
  }

  private void split(IntIntImmutablePair chunk, Stack<Action> actions, Stack<IntIntImmutablePair> chunks) {
    AABB bb = triangles.computeAABB(chunk.leftInt(), chunk.rightInt());
    double xl = bb.xmax - bb.xmin;
    double yl = bb.ymax - bb.ymin;
    double zl = bb.zmax - bb.zmin;
    int axis; // 0 - x, 1 - y, 2 - z
    if (xl >= yl && xl >= zl) {
      axis = 0;
    } else if (yl >= zl) {
      axis = 1;
    } else {
      axis = 2;
    }

    AABB bounds = new AABB(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    float cmin = Float.POSITIVE_INFINITY;
    int split = 0;
    int end = chunk.rightInt() - chunk.leftInt();

    float[] sl = new float[end];
    float[] sr = new float[end];

    triangles.sort(chunk.leftInt(), chunk.rightInt(), axis);
    for (int i = 0; i < end - 1; ++i) {
      triangles.expandAABB(bounds, chunk.leftInt() + i);
      sl[i] = surfaceArea(bounds);
    }
    bounds = new AABB(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    for (int i = end - 1; i > 0; --i) {
      triangles.expandAABB(bounds, chunk.leftInt() + i);
      sr[i - 1] = surfaceArea(bounds);
    }
    for (int i = 0; i < end - 1; ++i) {
      float c = sl[i] * (i + 1) + sr[i] * (end - i - 1);
      if (c < cmin) {
        cmin = c;
        split = i;
      }
    }

    split += 1;

    actions.push(Action.MERGE);
    chunks.push(new IntIntImmutablePair(chunk.leftInt(), chunk.leftInt()+split));
    actions.push(Action.PUSH);

    chunks.push(new IntIntImmutablePair(chunk.leftInt()+split, chunk.rightInt()));
    actions.push(Action.PUSH);
  }

  @Override
  public boolean closestIntersection(Ray ray) {
    boolean hit = false;
    int currentNode = rootIndex;
    IntStack nodesToVisit = new IntArrayList();

    double rx = 1 / ray.d.x;
    double ry = 1 / ray.d.y;
    double rz = 1 / ray.d.z;

    while (true) {
      int childIndex = children.getInt(currentNode*2);
      if (childIndex < 0) {
        // Is leaf
        int primFrom = -childIndex - 1;
        int primTo = primFrom + children.getInt(currentNode*2+1);
        for(int triangleIndex = primFrom; triangleIndex < primTo; ++triangleIndex) {
          hit = triangles.intersect(triangleIndex, ray) | hit;
        }

        if (nodesToVisit.isEmpty()) break;
        currentNode = nodesToVisit.popInt();
      } else {
        // Is branch, find closest node
        int bbBaseIndex = 6*childIndex;
        double t1 = quickAabbIntersect(ray,
                bbox.getFloat(bbBaseIndex),
                bbox.getFloat(bbBaseIndex+1),
                bbox.getFloat(bbBaseIndex+2),
                bbox.getFloat(bbBaseIndex+3),
                bbox.getFloat(bbBaseIndex+4),
                bbox.getFloat(bbBaseIndex+5),
                rx, ry, rz);
        int rightChildIndex = children.getInt(currentNode*2+1);
        bbBaseIndex = 6*rightChildIndex;
        double t2 = quickAabbIntersect(ray,
                bbox.getFloat(bbBaseIndex),
                bbox.getFloat(bbBaseIndex+1),
                bbox.getFloat(bbBaseIndex+2),
                bbox.getFloat(bbBaseIndex+3),
                bbox.getFloat(bbBaseIndex+4),
                bbox.getFloat(bbBaseIndex+5),
                rx, ry, rz);

        if (t1 > ray.t | t1 == -1) {
          if (t2 > ray.t | t2 == -1) {
            if (nodesToVisit.isEmpty()) break;
            currentNode = nodesToVisit.popInt();
          } else {
            currentNode = rightChildIndex;
          }
        } else if (t2 > ray.t | t2 == -1) {
          currentNode = childIndex;
        } else if (t1 < t2) {
          nodesToVisit.push(rightChildIndex);
          currentNode = childIndex;
        } else {
          nodesToVisit.push(childIndex);
          currentNode = rightChildIndex;
        }
      }
    }

    return hit;
  }

  /**
   * Perform a fast AABB intersection with cached reciprocal direction. This is a branchless approach based on:
   * https://gamedev.stackexchange.com/a/146362
   */
  public double quickAabbIntersect(Ray ray, float xmin, float xmax, float ymin, float ymax, float zmin, float zmax, double rx, double ry, double rz) {
    if (ray.o.x >= xmin && ray.o.x <= xmax && ray.o.y >= ymin && ray.o.y <= ymax && ray.o.z >= zmin && ray.o.z <= zmax) {
      return 0;
    }

    double tx1 = (xmin - ray.o.x) * rx;
    double tx2 = (xmax - ray.o.x) * rx;

    double ty1 = (ymin - ray.o.y) * ry;
    double ty2 = (ymax - ray.o.y) * ry;

    double tz1 = (zmin - ray.o.z) * rz;
    double tz2 = (zmax - ray.o.z) * rz;

    double tmin = FastMath.max(FastMath.max(FastMath.min(tx1, tx2), FastMath.min(ty1, ty2)), FastMath.min(tz1, tz2));
    double tmax = FastMath.min(FastMath.min(FastMath.max(tx1, tx2), FastMath.max(ty1, ty2)), FastMath.max(tz1, tz2));

    return (tmin <= tmax + OFFSET) & (tmin >= 0) ? tmin : -1;
  }
}
