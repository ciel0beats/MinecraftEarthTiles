# LakeMerge

Repairs lakes on a live server without replacing whole tiles.

It takes freshly generated region files and copies of the server's region files, and writes
new region files that are the server's own files with only these chunks swapped in:

- chunks the server file is missing, and
- "lake" chunks, where the new tile has noticeably more surface water than the server
  (dried mud lakes that are water again), counting ice only when there is water under it,
- plus a margin of chunks around those so the shorelines line up.

Every other chunk, including player builds, is copied unchanged from the server file.

## Usage

Needs Java and WorldPainter installed (it uses WorldPainter's region and NBT libraries).

```
java -cp "C:\Program Files\WorldPainter\lib\*" LakeMerge.java <newRegionDir> <serverRegionDir> <outDir> [margin=1] [threshold=24]
```

- `newRegionDir`: region files from the generator for the tiles being repaired
- `serverRegionDir`: copies of the same region files downloaded from the server
- `outDir`: where the merged files are written (upload these)
- `margin`: chunks of shoreline around each lake chunk (default 1)
- `threshold`: how many more water columns (of 256) a chunk needs to count as a lake (default 24)

It prints, per file, how many chunks were missing, how many were lakes, how many were replaced
and how many were kept from the server. Always back up the server's files before uploading.
