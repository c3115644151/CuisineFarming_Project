package com.example.cuisinefarming.fertility;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ChunkFertilityData {
    private final Map<Integer, Entry> dataMap = new HashMap<>();
    private boolean dirty = false;

    public boolean isDirty() {
        return dirty;
    }

    public void setClean() {
        this.dirty = false;
    }

    public static class Entry {
        public int baseFertility;
        public long lastUpdateTime;
        public double fertilizerConcentration; // New: Concentration value

        public Entry(int baseFertility, long lastUpdateTime, double fertilizerConcentration) {
            this.baseFertility = baseFertility;
            this.lastUpdateTime = lastUpdateTime;
            this.fertilizerConcentration = fertilizerConcentration;
        }
    }

    public Entry getData(int x, int y, int z) {
        return dataMap.get(packKey(x, y, z));
    }

    public void setBaseData(int x, int y, int z, int baseFertility, long time) {
        Entry entry = dataMap.get(packKey(x, y, z));
        if (entry != null) {
            entry.baseFertility = baseFertility;
            entry.lastUpdateTime = time;
        } else {
            dataMap.put(packKey(x, y, z), new Entry(baseFertility, time, 0.0));
        }
        dirty = true;
    }

    public void setFertilizerData(int x, int y, int z, double concentration, long time) {
        Entry entry = dataMap.get(packKey(x, y, z));
        if (entry != null) {
            entry.fertilizerConcentration = concentration;
            entry.lastUpdateTime = time; // Usually we update time when modifying concentration
        } else {
            dataMap.put(packKey(x, y, z), new Entry(0, time, concentration));
        }
        dirty = true;
    }

    public void updateTime(int x, int y, int z, long time) {
        Entry entry = dataMap.get(packKey(x, y, z));
        if (entry != null) {
            entry.lastUpdateTime = time;
            dirty = true;
        }
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            
            // Version header to avoid crash with old data
            dos.writeInt(2); // Version 2
            
            dos.writeInt(dataMap.size());
            for (Map.Entry<Integer, Entry> entry : dataMap.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeByte(entry.getValue().baseFertility);
                dos.writeLong(entry.getValue().lastUpdateTime);
                dos.writeDouble(entry.getValue().fertilizerConcentration);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static ChunkFertilityData deserialize(byte[] bytes) {
        ChunkFertilityData data = new ChunkFertilityData();
        if (bytes == null || bytes.length == 0) return data;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream dis = new DataInputStream(bais)) {
            
            // Check version/size
            // Old format started with size (int) directly. 
            // If we read a small int (like 2) it might be version, but it could be size.
            // However, old format: size -> key -> byte -> long -> int -> long.
            // New format: version -> size -> key -> byte -> long -> double.
            
            int firstInt = dis.readInt();
            if (firstInt == 2) {
                // New Format
                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    int key = dis.readInt();
                    int baseFertility = dis.readByte() & 0xFF;
                    long lastTime = dis.readLong();
                    double concentration = dis.readDouble();
                    data.dataMap.put(key, new Entry(baseFertility, lastTime, concentration));
                }
            } else {
                // Old Format (firstInt is size) or Unknown
                // Try to read old format to migrate? Or just discard.
                // Discarding is safer for development.
                // System.out.println("CuisineFarming: Detected old data format or unknown version. Resetting chunk data.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    public Map<Integer, Entry> getAllEntries() {
        return dataMap;
    }

    public static int unpackX(int key) {
        return (key >> 24) & 0xFF;
    }

    public static int unpackY(int key) {
        return (key >> 8) & 0xFFFF;
    }

    public static int unpackZ(int key) {
        return key & 0xFF;
    }

    private int packKey(int x, int y, int z) {
        return ((x & 0xFF) << 24) | ((y & 0xFFFF) << 8) | (z & 0xFF);
    }
}
