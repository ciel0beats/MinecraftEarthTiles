import org.jnbt.*;
import org.pepsoft.minecraft.RegionFile;

import java.io.*;
import java.util.*;

/**
 * Counts surface water blocks that can spill: water on top of a column whose neighbour column's
 * surface is lower (so the water flows sideways into air).
 *
 * Usage: java -cp "<WorldPainter lib>/*" SpillCheck.java <region.mca> [more.mca ...]
 */
public class SpillCheck {
    public static void main(String[] a) throws Exception {
        for (String path : a) {
            int[][] topY = new int[512][512];
            boolean[][] water = new boolean[512][512];
            for (int[] r : topY) Arrays.fill(r, Integer.MIN_VALUE);
            try (RegionFile rf = new RegionFile(new File(path), true)) {
                for (int cz = 0; cz < 32; cz++) for (int cx = 0; cx < 32; cx++) {
                    if (!rf.containsChunk(cx, cz)) continue;
                    CompoundTag chunk;
                    try (NBTInputStream in = new NBTInputStream(rf.getChunkDataInputStream(cx, cz))) { chunk = (CompoundTag) in.readTag(); }
                    scan(chunk, cx * 16, cz * 16, topY, water);
                }
            }
            int surfaceWater = 0, spill = 0;
            for (int x = 1; x < 511; x++) for (int z = 1; z < 511; z++) {
                if (!water[x][z]) continue;
                surfaceWater++;
                int y = topY[x][z];
                if ((topY[x + 1][z] < y && !water[x + 1][z]) || (topY[x - 1][z] < y && !water[x - 1][z]) || (topY[x][z + 1] < y && !water[x][z + 1]) || (topY[x][z - 1] < y && !water[x][z - 1])) spill++;
            }
            System.out.printf("%s: surface water columns %d, spilling %d%n", new File(path).getName(), surfaceWater, spill);
        }
    }

    static void scan(CompoundTag chunk, int ox, int oz, int[][] topY, boolean[][] water) {
        List<CompoundTag> sorted = new ArrayList<>();
        for (Object o : ((ListTag) chunk.getValue().get("sections")).getValue()) sorted.add((CompoundTag) o);
        sorted.sort((p, q) -> Byte.compare(((ByteTag) q.getValue().get("Y")).getValue(), ((ByteTag) p.getValue().get("Y")).getValue()));
        int found = 0;
        boolean[][] done = new boolean[16][16];
        for (CompoundTag s : sorted) {
            int sy = ((ByteTag) s.getValue().get("Y")).getValue();
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
                if (done[x][z]) continue;
                int idx = (y * 16 + z) * 16 + x;
                String n = data == null ? names[0] : names[(int) ((data[idx / per] >>> ((idx % per) * bits)) & ((1L << bits) - 1))];
                if (n.endsWith(":air") || n.endsWith(":cave_air") || n.contains("leaves") || n.endsWith("short_grass") || n.endsWith("tall_grass")
                        || n.endsWith("fern") || n.contains("log") || n.contains("flower") || n.contains("bush") || n.contains("dripleaf") || n.contains("lily")) continue;
                done[x][z] = true;
                topY[ox + x][oz + z] = sy * 16 + y;
                water[ox + x][oz + z] = n.endsWith(":water");
                found++;
            }
            if (found == 256) break;
        }
    }
}
