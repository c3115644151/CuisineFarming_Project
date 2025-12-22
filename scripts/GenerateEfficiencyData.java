import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GenerateEfficiencyData {

    // --- Simulation Logic (Updated 2025-12-22) ---
    // Aligned with FarmingListener refactor for Mendelian Genetics.
    
    public static class SimulationResult {
        public double fertility;
        public double concentration;
        public double resistance; // Scaled resistance (Phenotype * 40.0)
        public double growthGene; // Phenotype Value (2.0 - 9.0)
        public double biomeBonus;
        public double spiritBonus;
        
        public double baseEfficiency; // 1.0 + fertilityBonus
        public double fertilityBonus; // (Standard)
        public double resistanceBonus; // (Gene Only)
        public double growthBonus; // (Gene Only)
        public double totalEfficiency; // Final
        
        public String type; // "Vanilla" or "Gene"
    }

    public static SimulationResult calculate(String type, double fertility, double concentration, 
                                           double resistance, double growthGene, 
                                           double biomeBonus, double spiritBonus) {
        SimulationResult res = new SimulationResult();
        res.type = type;
        res.fertility = fertility;
        res.concentration = concentration;
        res.resistance = resistance;
        res.growthGene = growthGene;
        res.biomeBonus = biomeBonus;
        res.spiritBonus = spiritBonus;

        // 1. Base Efficiency Calculation (Standard for all)
        // From FertilityManager: baseEfficiency = 1.0 + (min(100, fert) * 0.005)
        double effFertility = Math.min(100.0, Math.max(-100.0, fertility));
        res.fertilityBonus = effFertility * 0.005;
        
        // Base = 1.0 + Fertility + Biome + Spirit
        res.baseEfficiency = 1.0 + res.fertilityBonus + biomeBonus + spiritBonus;
        
        if (type.equals("Vanilla")) {
            res.resistanceBonus = 0.0;
            res.growthBonus = 0.0;
            res.totalEfficiency = res.baseEfficiency;
            return res;
        }

        // 2. Gene Logic (FarmingListener)
        
        // Resistance Bonus
        // Formula: 0.01 * C * (1 - C / (2 * R))
        // Note: resistance input here should be already scaled (Phenotype * 40.0)
        double R = Math.max(resistance, 1.0);
        double C = concentration;
        res.resistanceBonus = 0.01 * C * (1.0 - C / (2.0 * R));
        
        // Growth Bonus
        // Range: 2.0 (Wild) ~ 9.0 (Max)
        // <= 2.0: (Val / 2.0) - 1.0 (Penalty)
        // > 2.0: (Val - 2.0) / 7.0 (Bonus)
        if (growthGene <= 2.0) {
            res.growthBonus = (growthGene / 2.0) - 1.0;
        } else {
            res.growthBonus = (growthGene - 2.0) / 7.0;
        }
        
        // 3. Total
        // Efficiency = Base + ResistanceBonus + GrowthBonus
        res.totalEfficiency = res.baseEfficiency + res.resistanceBonus + res.growthBonus;
        
        return res;
    }

    public static void main(String[] args) {
        List<SimulationResult> results = new ArrayList<>();
        
        // --- Scenarios ---
        
        // Scenario 1: Concentration Sweep (Fixed Fertility=100, Growth=2.0 (Wild), Biome=0, Spirit=0)
        // Variable: Concentration 0-400
        // Cases: Vanilla, Wild Gene (2.0), Mid Gene (5.0), Max Gene (9.0)
        
        // Phenotypes and their Scaled Resistance (x40.0)
        // 2.0 -> 80.0
        // 5.0 -> 200.0
        // 9.0 -> 360.0
        double[] phenotypes = {2.0, 5.0, 9.0};
        
        for (int c = 0; c <= 400; c += 10) {
            // Vanilla
            results.add(calculate("Vanilla", 100, c, 0, 0, 0, 0)); // Growth/Res ignored for Vanilla
            
            // Genes
            for (double p : phenotypes) {
                double scaledRes = p * 40.0;
                results.add(calculate("Gene_P" + p, 100, c, scaledRes, p, 0, 0));
            }
        }
        
        // Output to CSV
        try (PrintWriter writer = new PrintWriter(new FileWriter("efficiency_data.csv"))) {
            writer.println("Type,Concentration,TotalEfficiency,ResistanceBonus,GrowthBonus");
            for (SimulationResult r : results) {
                writer.println(String.format(Locale.US, "%s,%d,%.4f,%.4f,%.4f", 
                    r.type, (int)r.concentration, r.totalEfficiency, r.resistanceBonus, r.growthBonus));
            }
            System.out.println("Data generated successfully: efficiency_data.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
