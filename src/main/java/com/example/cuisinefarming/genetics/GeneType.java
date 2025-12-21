package com.example.cuisinefarming.genetics;

/**
 * Defines the types of genes a crop can possess.
 */
public enum GeneType {
    // Base Growth Multiplier. Default 1.0. Range [0.5, 5.0].
    GROWTH_SPEED,
    
    // Extra Yield Count (Chance/Amount). Default 0.0. Range [0.0, 5.0].
    // Acts as a multiplier for specialty drops too.
    YIELD,
    
    // Optimal Temperature (Celsius). Default 20.0. Range [-10.0, 50.0].
    OPTIMAL_TEMP,
    
    // Temperature Tolerance Range (+/- Celsius). Default 5.0. Range [0.0, 20.0].
    // e.g. If Optimal is 20 and Tol is 5, crop is happy in [15, 25].
    TEMP_TOLERANCE,
    
    // Max Fertility Concentration it can withstand. Default 100.0. Range [50.0, 300.0].
    FERTILITY_RESISTANCE
}
