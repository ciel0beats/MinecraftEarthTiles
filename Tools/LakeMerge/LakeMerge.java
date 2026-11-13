import org.jnbt.*;
import org.pepsoft.minecraft.RegionFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Copies only the "lake" chunks of freshly generated tiles into copies of the server's region
 * files, leaving every other chunk (and any player builds there) untouched.
 *
 * A chunk is taken from the new tile when
 *  - the server file does not have it (the black missing chunks on the map), or
 *  - the new chunk has at least THRESHOLD more surface-water columns than the server's chunk
 *    (a lake that was mud or dry on the server and is water now),
 * plus MARGIN chunks around those so the shores line up.
 *
 * Usage: java -cp "<WorldPainter lib>/*" LakeMerge.java <newRegionDir> <serverRegionDir> <outDir> [margin=1] [threshold=24]
 * Region files in newRegionDir without a server copy are reported and skipped.
 */
public class LakeMerge {
    public static void main(String[] a) throws Exception {
        File newDir = new File(a[0]), serverDir = new File(a[1]), outDir = new File(a[2]);
        int margin = a.length > 3 ? Integer.parseInt(a[3]) : 1;
        int threshold = a.length > 4 ? Integer.parseInt(a[4]) : 24;
        outDir.mkdirs();
        File[] files = newDir.listFiles((d, n) -> n.endsWith(".mca"));
        Arrays.sort(files);
        int totalReplaced = 0;
        for (File nf : files) {
            File sf = new File(serverDir, nf.getName());
            if (!sf.isFile()) { System.out.println(nf.getName() + ": no server copy in " + serverDir + " - skipped"); continue; }
            boolean[][] pick = new boolean[32][32];
            int missing = 0, lakes = 0;
            try (RegionFile nr = new RegionFile(nf, true); RegionFile sr = new RegionFile(sf, true)) {
                for (int cz = 0; cz < 32; cz++) for (int cx = 0; cx < 32; cx++) {
                    if (!nr.containsChunk(cx, cz)) continue;
                    if (!sr.containsChunk(cx, cz)) { pick[cx][cz] = true; missing++; continue; }
                    int nw = surfaceWater(read(nr, cx, cz)), sw = surfaceWater(read(sr, cx, cz));
                    if (nw - sw >= threshold) { pick[cx][cz] = true; lakes++; }
                }
            }
            boolean[][] grown = new boolean[32][32];
            for (int cz = 0; cz < 32; cz++) for (int cx = 0; cx < 32; cx++) if (pick[cx][cz])
                for (int dz = -margin; dz <= margin; dz++) for (int dx = -margin; dx <= margin; dx++) {
                    int x = cx + dx, z = cz + dz;
                    if (x >= 0 && x < 32 && z >= 0 && z < 32) grown[x][z] = true;
                }
            File of = new File(outDir, nf.getName());
            Files.copy(sf.toPath(), of.toPath(), StandardCopyOption.REPLACE_EXISTING);
            int replaced = 0;
            try (RegionFile nr = new RegionFile(nf, true); RegionFile or = new RegionFile(of, false)) {
                for (int cz = 0; cz < 32; cz++) for (int cx = 0; cx < 32; cx++) {
                    if (!grown[cx][cz] || !nr.containsChunk(cx, cz)) continue;
                    CompoundTag chunk = read(nr, cx, cz);
                    try (NBTOutputStream out = new NBTOutputStream(or.getChunkDataOutputStream(cx, cz))) { out.writeTag(chunk); }
                    replaced++;
                }
            }
            totalReplaced += replaced;
            System.out.printf("%s: %d missing + %d lake chunks -> %d replaced with margin, %d kept from server%n",
                    nf.getName(), missing, lakes, replaced, 1024 - replaced);
        }
        System.out.println("total chunks replaced: " + totalReplaced);
    }

    static CompoundTag read(RegionFile r, int cx, int cz) throws IOException {
        try (NBTInputStream in = new NBTInputStream(r.getChunkDataInputStream(cx, cz))) { return (CompoundTag) in.readTag(); }
    }

    /** Number of the 256 columns whose highest non-air block is water (or ice with water under it). */
    static int surfaceWater(CompoundTag chunk) {
        String[][] top = new String[16][16], second = new String[16][16];
        int found = 0;
        List<?> sections = ((ListTag) chunk.getValue().get("sections")).getValue();
        List<CompoundTag> sorted = new ArrayList<>();
        for (Object o : sections) sorted.add((CompoundTag) o);
        sorted.sort((p, q) -> Byte.compare(((ByteTag) q.getValue().get("Y")).getValue(), ((ByteTag) p.getValue().get("Y")).getValue()));
        for (CompoundTag s : sorted) {
            CompoundTag bs = (CompoundTag) s.getValue().get("block_states");
            if (bs == null) continue;
            List<?> pal = ((ListTag) bs.getValue().get("palette")).getValue();
            String[] names = new String[pal.size()];
            for (int k = 0; k < names.length; k++) names[k] = ((StringTag) ((CompoundTag) pal.get(k)).getValue().get("Name")).getValue();
            if (names.length == 1 && names[0].endsWith(":air")) continue;
            long[] data = bs.getValue().containsKey("data") ? ((LongArrayTag) bs.getValue().get("data")).getValue() : null;
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, names.length - 1)));
            int per = 64 / bits;
            for (int y = 15; y >= 0; y--) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (second[x][z] != null) continue;
                int idx = (y * 16 + z) * 16 + x;
                String n = data == null ? names[0] : names[(int) ((data[idx / per] >>> ((idx % per) * bits)) & ((1L << bits) - 1))];
                if (n.endsWith(":air") || n.endsWith(":cave_air")) continue;
                if (top[x][z] == null) top[x][z] = n; else { second[x][z] = n; found++; }
            }
            if (found == 256) break;
        }
        int water = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            String n = top[x][z];
            if (n == null) continue;
            boolean isWater = n.endsWith(":water") || n.contains("seagrass") || n.contains("kelp");
            boolean frozenLake = n.endsWith(":ice") && second[x][z] != null && second[x][z].endsWith(":water");
            if (isWater || frozenLake) water++;
        }
        return water;
    }
}
