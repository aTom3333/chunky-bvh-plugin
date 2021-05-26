# Chunky-bvh-plugin
This is a plugin for [Chunky][chunky] that adds more BVH implementations.

## Installation
Download the plug-in .jar file from the release page add it as a plug-in from the Chunky Launcher.

## Usage
A new BVH implementation will be available to choose from in the _Advanced_ tab.

The new implementation is `PACKED_SAH_MA`. It is based of `SAH_MA` but is optimized to
reduce memory usage.

It use from 3x to 4x less memory than the built-in BVH of chunky.
It also happens to build slightly faster but it not what is optimized
for and could no longer hold in the future if the built-in BVH are improved.


[chunky]: https://chunky.llbit.se/