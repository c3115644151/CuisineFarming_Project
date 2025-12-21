package com.example.cuisinefarming.genetics;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the genetic makeup of a single seed or crop instance.
 */
public class GeneData {

    private final Map<GeneType, Double> genes;
    private boolean identified;

    public GeneData() {
        this.genes = new HashMap<>();
        this.identified = false;
        initializeDefaults();
    }
    
    public GeneData(Map<GeneType, Double> genes, boolean identified) {
        this.genes = new HashMap<>(genes);
        this.identified = identified;
        // Ensure all types exist
        for (GeneType type : GeneType.values()) {
            this.genes.putIfAbsent(type, getDefaultValue(type));
        }
    }

    private void initializeDefaults() {
        // Updated Defaults (2025-12-21)
        genes.put(GeneType.GROWTH_SPEED, 1.0);
        genes.put(GeneType.YIELD, 1.0); // 1.0 means standard drops (no extra guaranteed yet)
        genes.put(GeneType.OPTIMAL_TEMP, 20.0);
        genes.put(GeneType.TEMP_TOLERANCE, 5.0);
        genes.put(GeneType.FERTILITY_RESISTANCE, 100.0); // Standard resistance
    }

    // Helper for legacy method call
    private double getDefaultValue(GeneType type) {
        switch (type) {
            case GROWTH_SPEED: return 1.0;
            case YIELD: return 1.0;
            case OPTIMAL_TEMP: return 20.0;
            case TEMP_TOLERANCE: return 5.0;
            case FERTILITY_RESISTANCE: return 100.0;
            default: return 0.0;
        }
    }

    public double getGene(GeneType type) {
        return genes.getOrDefault(type, getDefaultValue(type));
    }

    public void setGene(GeneType type, double value) {
        genes.put(type, value);
    }

    public boolean isIdentified() {
        return identified;
    }

    public void setIdentified(boolean identified) {
        this.identified = identified;
    }
    
    public Map<GeneType, Double> getAllGenes() {
        return new HashMap<>(genes);
    }
    
    // --- Randomization Logic (Added 2025-12-21) ---
    
    /**
     * Randomizes genes for a newly generated seed.
     * Uses a weighted probability system to simulate "Lucky Drops".
     */
    public void randomize() {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        
        for (GeneType type : GeneType.values()) {
            if (type == GeneType.OPTIMAL_TEMP) {
                // Optimal Temp is not quality-based, just random biome preference.
                // Range -5 to 45 for variety.
                double temp = -5.0 + random.nextDouble() * 50.0;
                setGene(type, Math.round(temp * 10.0) / 10.0);
            } else {
                setGene(type, generateRandomValue(type, random));
            }
        }
    }
    
    private double generateRandomValue(GeneType type, java.util.concurrent.ThreadLocalRandom random) {
        // "Euro Luck" Distribution:
        // 70% Common (1-2 Stars)
        // 20% Uncommon (3 Stars)
        // 9% Rare (4 Stars)
        // 1% Legendary (5 Stars)
        
        double roll = random.nextDouble();
        int targetStars;
        
        if (roll < 0.01) targetStars = 5;      // 1%
        else if (roll < 0.10) targetStars = 4; // 9%
        else if (roll < 0.30) targetStars = 3; // 20%
        else targetStars = 1 + random.nextInt(2); // 70% (1 or 2)
        
        return generateValueForStars(type, targetStars, random);
    }
    
    private double generateValueForStars(GeneType type, int stars, java.util.concurrent.ThreadLocalRandom random) {
        // Generate a random value within the range of the target star level
        // Add some noise so not all 5-stars are identical.
        
        switch (type) {
            case GROWTH_SPEED:
                // 1*: [0.5, 1.1)
                // 2*: [1.1, 2.0)
                // 3*: [2.0, 3.0)
                // 4*: [3.0, 4.0)
                // 5*: [4.0, 5.0]
                switch (stars) {
                    case 5: return 4.0 + random.nextDouble() * 1.0;
                    case 4: return 3.0 + random.nextDouble() * 1.0;
                    case 3: return 2.0 + random.nextDouble() * 1.0;
                    case 2: return 1.1 + random.nextDouble() * 0.9;
                    default: return 0.5 + random.nextDouble() * 0.6;
                }
                
            case YIELD:
                // 1*: [0.0, 1.1)
                // 2*: [1.1, 2.0)
                // 3*: [2.0, 3.0)
                // 4*: [3.0, 4.0)
                // 5*: [4.0, 5.0]
                switch (stars) {
                    case 5: return 4.0 + random.nextDouble() * 1.0;
                    case 4: return 3.0 + random.nextDouble() * 1.0;
                    case 3: return 2.0 + random.nextDouble() * 1.0;
                    case 2: return 1.1 + random.nextDouble() * 0.9;
                    default: return 0.0 + random.nextDouble() * 1.1;
                }
                
            case TEMP_TOLERANCE:
                // 1*: [0, 5)
                // 2*: [5, 8)
                // 3*: [8, 12)
                // 4*: [12, 16)
                // 5*: [16, 20]
                switch (stars) {
                    case 5: return 16.0 + random.nextDouble() * 4.0;
                    case 4: return 12.0 + random.nextDouble() * 4.0;
                    case 3: return 8.0 + random.nextDouble() * 4.0;
                    case 2: return 5.0 + random.nextDouble() * 3.0;
                    default: return 0.0 + random.nextDouble() * 5.0;
                }
                
            case FERTILITY_RESISTANCE:
                // 1*: [50, 100)
                // 2*: [100, 150)
                // 3*: [150, 200)
                // 4*: [200, 250)
                // 5*: [250, 300]
                switch (stars) {
                    case 5: return 250.0 + random.nextDouble() * 50.0;
                    case 4: return 200.0 + random.nextDouble() * 50.0;
                    case 3: return 150.0 + random.nextDouble() * 50.0;
                    case 2: return 100.0 + random.nextDouble() * 50.0;
                    default: return 50.0 + random.nextDouble() * 50.0;
                }
                
            default:
                return getDefaultValue(type);
        }
    }
    
    // --- Star Rating System (Added 2025-12-21) ---
    
    /**
     * Calculates the overall star rating of the seed (1-5).
     * Based on the average of its quality genes.
     */
    public int calculateStarRating() {
        int totalStars = 0;
        int count = 0;
        
        // We evaluate "Quality" genes. Optimal Temp is a trait, not a quality scale.
        totalStars += getGeneStar(GeneType.GROWTH_SPEED); count++;
        totalStars += getGeneStar(GeneType.YIELD); count++;
        totalStars += getGeneStar(GeneType.TEMP_TOLERANCE); count++;
        totalStars += getGeneStar(GeneType.FERTILITY_RESISTANCE); count++;
        
        if (count == 0) return 1;
        
        // Simple average rounded
        return Math.max(1, Math.min(5, Math.round((float)totalStars / count)));
    }

    /**
     * Gets the star level (1-5) for a specific gene type.
     */
    public int getGeneStar(GeneType type) {
        double value = getGene(type);
        switch (type) {
            case GROWTH_SPEED:
                // Range [0.5, 5.0]. Default 1.0.
                if (value >= 4.0) return 5;
                if (value >= 3.0) return 4;
                if (value >= 2.0) return 3;
                if (value >= 1.1) return 2;
                return 1;
                
            case YIELD:
                // Range [0.0, 5.0]. Default 1.0.
                if (value >= 4.0) return 5;
                if (value >= 3.0) return 4;
                if (value >= 2.0) return 3;
                if (value >= 1.1) return 2;
                return 1;
                
            case TEMP_TOLERANCE:
                // Range [0.0, 20.0]. Default 5.0.
                if (value >= 16.0) return 5;
                if (value >= 12.0) return 4;
                if (value >= 8.0) return 3;
                if (value >= 5.0) return 2; // Default is 2 stars
                return 1;
                
            case FERTILITY_RESISTANCE:
                // Range [50.0, 300.0]. Default 100.0.
                if (value >= 250.0) return 5;
                if (value >= 200.0) return 4;
                if (value >= 150.0) return 3;
                if (value >= 100.0) return 2; // Default is 2 stars
                return 1;
                
            case OPTIMAL_TEMP:
                // Not a quality metric. Return 3 as neutral.
                return 3;
                
            default:
                return 1;
        }
    }
    
    @Override
    public String toString() {
        return "GeneData{stars=" + calculateStarRating() + ", genes=" + genes + "}";
    }
}
