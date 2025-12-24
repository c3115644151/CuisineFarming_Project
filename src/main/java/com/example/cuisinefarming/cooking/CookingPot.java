package com.example.cuisinefarming.cooking;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 烹饪锅 (Cooking Pot) 实体类
 * 负责管理单个锅具的状态、食材、视觉效果和物理交互。
 * 对应一个炼药锅方块。
 */
public class CookingPot {

    private final CuisineFarming plugin;
    private final Location location; // 锅具位置
    private final Inventory inventory; // 持久化存储容器 (包含食材和燃料)
    private final List<ItemDisplay> visualItems = new ArrayList<>(); // 视觉实体 (食材)
    private TextDisplay infoDisplay; // 悬浮文字 (温度条)

    private CookingState state = CookingState.IDLE;
    private double temperature = 20.0; // 当前温度 (摄氏度)
    private int burnTime = 0; // 燃料剩余燃烧时间 (tick)
    @SuppressWarnings("unused")
    private int maxBurnTime = 0; // 当前燃料总燃烧时间
    private boolean isHeated = false; // 是否被玩家激活加热 (通过扇子)
    private long lastInteractionTime = 0; // 上次玩家交互时间 (用于自动熄火)
    
    private CookingRecipe currentRecipe = null;
    private int cookingTimer = 0; // 烹饪进行时长 (tick)
    private int perfectCookingTicks = 0; // 在最佳温区内的时长 (tick)

    // 视觉任务
    private BukkitRunnable visualTask;
    private BukkitRunnable physicsTask; // 物理模拟任务 (温度/燃料)

    public enum CookingState {
        IDLE,       // 空闲/冷锅
        PREPARING,  // 备料阶段 (有食材，未达到烹饪温度)
        COOKING,    // 烹饪中 (锁定，QTE 进行中)
        FINISHED,   // 完成 (等待取出)
        BURNT       // 糊锅 (黑暗料理)
    }

    public static final int[] INGREDIENT_SLOTS = {10, 11, 19, 20, 28, 29};
    public static final int FUEL_SLOT = 40;
    public static final int OUTPUT_SLOT = 25;

    public CookingPot(CuisineFarming plugin, Location location) {
        this.plugin = plugin;
        this.location = location;
        // 创建持久化 Inventory (54格)
        this.inventory = new CookingPotInventory(this).getInventory();
        
        startVisualTask();
        startPhysicsTask();
        spawnInfoDisplay();
    }

    /**
     * 销毁锅具 (移除所有视觉实体)
     */
    public void destroy() {
        if (visualTask != null) visualTask.cancel();
        if (physicsTask != null) physicsTask.cancel();
        clearVisuals();
        if (infoDisplay != null) infoDisplay.remove();
        
        // 弹出物品 (从 Inventory 弹出)
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                location.getWorld().dropItem(location.clone().add(0.5, 1.0, 0.5), item);
            }
        }
        inventory.clear();
    }

    private void clearVisuals() {
        for (ItemDisplay display : visualItems) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        visualItems.clear();
    }

    /**
     * 尝试向锅内添加食材
     */
    public boolean addIngredient(ItemStack item, Player player) {
        if (state == CookingState.COOKING || state == CookingState.FINISHED) {
            player.sendMessage("§c锅正在烹饪或已完成，无法添加食材！");
            return false;
        }
        
        List<ItemStack> currentIngs = getIngredients();
        if (currentIngs.size() >= 6) {
            player.sendMessage("§c锅满了！(最多6个食材)");
            return false;
        }

        // 添加逻辑
        ItemStack toAdd = item.clone();
        toAdd.setAmount(1);
        addIngredientDirectly(toAdd);
        
        // 播放音效
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_WATER_AMBIENT, 0.5f, 1.5f);
        location.getWorld().spawnParticle(Particle.SPLASH, location.clone().add(0.5, 0.8, 0.5), 10, 0.2, 0.1, 0.2, 0.1);
        
        player.sendMessage(Component.text("§a加入了 " + item.getType().name()));
        return true;
    }

    public void addIngredientDirectly(ItemStack item) {
        lastInteractionTime = System.currentTimeMillis();
        // Find first empty slot in INGREDIENT_SLOTS
        for (int slot : INGREDIENT_SLOTS) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item);
                break;
            }
        }
        updateVisuals(); // Sync visuals from inventory
        
        if (state == CookingState.IDLE) {
            state = CookingState.PREPARING;
        }
    }

    public void clearIngredients() {
        // Clear slots INGREDIENT_SLOTS
        for (int slot : INGREDIENT_SLOTS) {
            inventory.setItem(slot, null);
        }
        clearVisuals();
        state = CookingState.IDLE;
    }

    public void updateVisuals() {
        clearVisuals();
        List<ItemStack> currentIngs = getIngredients();
        for (ItemStack item : currentIngs) {
            spawnIngredientVisual(item);
        }
        if (!currentIngs.isEmpty() && state == CookingState.IDLE) {
            state = CookingState.PREPARING;
        } else if (currentIngs.isEmpty() && state == CookingState.PREPARING) {
            state = CookingState.IDLE;
        }
    }

    public void openGUI(Player player) {
        // Open the persistent inventory
        player.openInventory(this.inventory);
    }

    public List<ItemStack> getIngredients() {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : INGREDIENT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) list.add(item);
        }
        return list;
    }


    /**
     * 生成单个食材的视觉实体
     */
    private void spawnIngredientVisual(ItemStack item) {
        Location spawnLoc = location.clone().add(0.5, 0.4 + (visualItems.size() * 0.1), 0.5);
        ItemDisplay display = location.getWorld().spawn(spawnLoc, ItemDisplay.class);
        display.setItemStack(item);
        
        // 缩小模型
        Transformation transform = display.getTransformation();
        transform.getScale().set(0.4f, 0.4f, 0.4f);
        display.setTransformation(transform);
        
        display.setBillboard(Display.Billboard.FIXED); // 固定朝向，我们将手动旋转
        visualItems.add(display);
    }

    /**
     * 启动视觉循环任务 (旋转、冒泡)
     */
    private void startVisualTask() {
        visualTask = new BukkitRunnable() {
            float angle = 0;
            
            @Override
            public void run() {
                // Update Ingredients Visuals
                List<ItemStack> currentIngs = getIngredients();
                if (!currentIngs.isEmpty()) {
                    // 旋转动画
                    angle += 0.1f;
                    for (int i = 0; i < visualItems.size(); i++) {
                        ItemDisplay display = visualItems.get(i);
                        if (display == null || !display.isValid()) continue;

                        // 让食材绕中心旋转并上下浮动
                        double offsetAngle = angle + (i * (Math.PI * 2 / visualItems.size()));
                        double radius = 0.2;
                        double x = Math.cos(offsetAngle) * radius;
                        double z = Math.sin(offsetAngle) * radius;
                        double y = 0.4 + Math.sin(angle * 2 + i) * 0.05; // 浮动

                        Location newLoc = location.clone().add(0.5 + x, y, 0.5 + z);
                        
                        // 自转
                        Transformation t = display.getTransformation();
                        t.getLeftRotation().set(new AxisAngle4f(angle + i, 0, 1, 0));
                        display.setTransformation(t);
                        
                        display.teleport(newLoc);
                    }
                }

                // 粒子效果
                if (state == CookingState.COOKING) {
                    // 使用 Cloud 替代 Campfire Smoke 防止与下方营火烟雾重叠
                    location.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0.5, 0.8, 0.5), 1, 0, 0.05, 0, 0.02);
                    if (Math.random() < 0.1) {
                        location.getWorld().playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.0f);
                    }
                }
            }
        };
        visualTask.runTaskTimer(plugin, 0L, 2L); // 每0.1秒刷新一次
    }

    private void startPhysicsTask() {
        physicsTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickPhysics();
                updateInfoDisplay();
            }
        };
        physicsTask.runTaskTimer(plugin, 0L, 20L); // 每秒计算一次物理
    }
    
    public boolean hasIngredients() {
        for (int i = 0; i < 6; i++) {
            if (inventory.getItem(i) != null) return true;
        }
        return false;
    }

    private void tickPhysics() {
        // 0. Check Completion -> Stop Heating
        if (state == CookingState.FINISHED || state == CookingState.BURNT) {
            isHeated = false;
            // Auto-reset if output is taken
            if (inventory.getItem(OUTPUT_SLOT) == null) {
                state = CookingState.IDLE;
                updateVisuals();
            }
        }
        
        // 0.5. Check Idle Timeout
        // If player hasn't interacted for 15s AND not currently cooking, stop heating to prevent infinite fuel burn.
        if (state != CookingState.COOKING && isHeated) {
            long timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime;
            if (timeSinceInteraction > 15000) { // 15 seconds timeout
                isHeated = false;
                // Optional: Play hiss sound to indicate auto-off
                // location.getWorld().playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
            }
        }

        // 1. Fuel Consumption
        if (isHeated) {
            if (burnTime > 0) {
                burnTime -= 20; // 消耗 1 秒
                if (burnTime < 0) burnTime = 0;
            } else {
                // Try consume new fuel
            ItemStack fuel = inventory.getItem(FUEL_SLOT);
            if (fuel != null && getFuelTime(fuel.getType()) > 0) {
                int time = getFuelTime(fuel.getType());
                fuel.setAmount(fuel.getAmount() - 1);
                inventory.setItem(FUEL_SLOT, fuel); // Update inventory
                    
                    burnTime = time;
                    maxBurnTime = time;
                    location.getWorld().playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
                } else {
                    // No fuel -> Stop heating
                    isHeated = false;
                }
            }
        }
        
        // 2. Temperature Logic
        double ambientTemp = 20.0;
        double targetTemp = ambientTemp;
        
        if (burnTime > 0 && isHeated) {
            targetTemp = 300.0; // Max temp with fuel
        }
        
        // Revised Physics:
        // - Fuel heating is SLOW (+1.0/s)
        // - Fan boost is handled in ignite()
        // - Cooling is MODERATE (-2.0/s)
        
        if (temperature < targetTemp) {
            temperature += 1.0; // Slow heating from fuel alone
        } else if (temperature > targetTemp) {
            // Only cool if NOT cooking (or if target temp is low, i.e. no fuel)
            // User requirement: "During cooking, pot will not naturally cool down"
            if (state != CookingState.COOKING) {
                temperature -= 2.0; // Natural cooling
            }
        }
        
        // Clamp
        if (temperature < 0) temperature = 0;
        
        // 3. State Transition (Auto-fail only)
        if (state == CookingState.COOKING) {
            cookingTimer += 20; // 物理tick每秒运行一次 (20ticks)
            
            // Check QTE (Temperature in Optimal Zone)
            if (currentRecipe != null) {
                if (temperature >= currentRecipe.getOptimalTempMin() && temperature <= currentRecipe.getOptimalTempMax()) {
                    perfectCookingTicks += 20; // Accumulate ticks (20 per second)
                }
                
                // Check Completion
                if (cookingTimer >= currentRecipe.getCookingTime()) {
                    finishCooking();
                }
            } else {
                // Dark Food Logic (Default 10s)
                if (cookingTimer >= 200) {
                    finishCooking();
                }
            }
        }
    }
    
    // Helper method to consume ingredients according to recipe requirements
    private void consumeIngredients(CookingRecipe recipe) {
        if (recipe == null) {
            clearIngredients(); // Fallback for dark food/null recipe
            return;
        }

        // We need to consume items that match the requirements.
        // Strategy: Iterate requirements, find matching items in slots, decrement them.
        for (Map.Entry<FoodTag, Integer> entry : recipe.getRequirements().entrySet()) {
            FoodTag tag = entry.getKey();
            int amountNeeded = entry.getValue();

            for (int slot : INGREDIENT_SLOTS) {
                if (amountNeeded <= 0) break;

                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType() == Material.AIR) continue;

                if (plugin.getCookingManager().getItemTags(item).contains(tag)) {
                    // Found a match
                    int amount = item.getAmount();
                    if (amount >= amountNeeded) {
                        // Consume required amount
                        item.setAmount(amount - amountNeeded);
                        inventory.setItem(slot, item); // Update inventory (if 0, Spigot might handle it, but let's be safe)
                        if (item.getAmount() <= 0) {
                            inventory.setItem(slot, null);
                        }
                        amountNeeded = 0;
                    } else {
                        // Consume all of this item and continue
                        amountNeeded -= amount;
                        inventory.setItem(slot, null);
                    }
                }
            }
        }
    }

    private void finishCooking() {
        // 1. Generate Result
        ItemStack result;
        double qteScore = 0.0;
        int finalStars = 1;

        if (currentRecipe != null) {
            // Calculate Score
            int totalTime = currentRecipe.getCookingTime();
            qteScore = (double) perfectCookingTicks / totalTime;
            if (qteScore > 1.0) qteScore = 1.0;
            
            // Ingredient Stars
            double totalStars = 0;
            int count = 0;
            NamespacedKey starKey = new NamespacedKey(plugin, "star_rating");
            
            for (ItemStack item : getIngredients()) {
                if (item == null) continue;
                int star = 1;
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(starKey, PersistentDataType.INTEGER)) {
                    star = meta.getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
                }
                totalStars += star;
                count++;
            }
            double avgStars = count > 0 ? totalStars / count : 1.0;
            
            // Final Stars
            double finalScore = (avgStars * 0.5) + (qteScore * 5.0 * 0.5);
            finalStars = (int) Math.round(finalScore);
            if (finalStars < 1) finalStars = 1;
            if (finalStars > 5) finalStars = 5;
            
            // 1-Star Result -> Dark Food (Mystery Stew)
            if (finalStars == 1) {
                result = new ItemStack(Material.SUSPICIOUS_STEW);
                ItemMeta meta = result.getItemMeta();
                meta.displayName(Component.text("§8黑暗料理 (1⭐)"));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7原本是: " + currentRecipe.getDisplayName()));
                lore.add(Component.text("§7但现在它只是一团不可名状的物质..."));
                meta.lore(lore);
                result.setItemMeta(meta);
                
                location.getWorld().playSound(location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                // Create Result Item
                result = currentRecipe.getResultTemplate().clone();
                ItemMeta meta = result.getItemMeta();
                
                // Set PDC
                meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, finalStars);
                
                // Add Lore
                List<Component> lore = meta.lore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("§7品质: " + "⭐".repeat(finalStars)));
                lore.add(Component.text("§8(QTE: " + String.format("%.0f%%", qteScore * 100) + ")"));
                
                // 5-Star Special Lore
                if (finalStars == 5) {
                    String flavorText = getFiveStarLore(currentRecipe.getId());
                    if (flavorText != null) {
                        lore.add(Component.text(""));
                        lore.add(Component.text("§6§o\"" + flavorText + "\""));
                    }
                }
                
                meta.lore(lore);
                result.setItemMeta(meta);
            }
            
        } else {
            // Dark Food
            result = new ItemStack(Material.SUSPICIOUS_STEW);
            ItemMeta meta = result.getItemMeta();
            meta.displayName(Component.text("§8黑暗料理"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7散发着诡异的气息..."));
            meta.lore(lore);
            result.setItemMeta(meta);
        }

        // 2. Consume Ingredients
        if (currentRecipe != null) {
            consumeIngredients(currentRecipe);
        } else {
            // Dark Food: Consume 1 of each ingredient in the pot
            for (int slot : INGREDIENT_SLOTS) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    item.setAmount(item.getAmount() - 1);
                    inventory.setItem(slot, item); // Update (Spigot handles 0->AIR)
                }
            }
        }
        
        // Clear visuals for consumed ingredients and force refresh for remaining ones
        clearVisuals(); 
        updateVisuals(); 

        // 3. Place Result in Output Slot
        ItemStack existing = inventory.getItem(OUTPUT_SLOT);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(OUTPUT_SLOT, result);
        } else {
            // If output slot is occupied, drop item
            location.getWorld().dropItem(location.clone().add(0, 1, 0), result);
        }

        // 4. Update State
        state = CookingState.FINISHED;
        isHeated = false; // Stop heating
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f); // Ding!
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5, 1.0, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
        
        // Optional: Play toast sound for high quality
        if (finalStars >= 4) {
            location.getWorld().playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
        }
    }
    
    public void startCooking() {
        if (state == CookingState.PREPARING || state == CookingState.IDLE) {
            // Match recipe and lock
            CookingRecipe match = plugin.getCookingManager().matchRecipe(getIngredients());
            
            if (match != null) {
                this.currentRecipe = match;
                this.cookingTimer = 0;
                this.perfectCookingTicks = 0;
                this.state = CookingState.COOKING;
                
                location.getWorld().playSound(location, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f);
                // Lock inventory (prevent adding/removing) - Handled in listener/addIngredient
            } else {
                // No matching recipe -> Dark Food logic if they force start?
                // For now, prevent start if no match? Or start as "Mystery Stew"?
                // Let's prevent start for better UX unless we want to punish.
                // User said "If recipe match fails... produce dark food".
                // So we should allow start, but mark as Dark Food recipe?
                // Or just null recipe and handle result generation as Dark Food.
                this.currentRecipe = null; // Represents Dark Food path
                this.cookingTimer = 0;
                this.perfectCookingTicks = 0;
                this.state = CookingState.COOKING; // Still start cooking
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
            }
        }
    }

    public void ignite() {
        lastInteractionTime = System.currentTimeMillis();
        
        // Fan usage: Instant heat boost
        // Limit max temp via fan to prevent instant burning? Or allow it?
        // Let's cap at 400 (dangerous)
        if (temperature < 400) {
            temperature += 2.0; // Reduced boost per click (was 5.0)
        }
        
        if (!isHeated) {
            isHeated = true; // Activate fuel consumption
        }
        
        location.getWorld().playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
        location.getWorld().spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.2, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
        location.getWorld().spawnParticle(Particle.SMOKE, location.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
    }

    private String getFiveStarLore(String key) {
        switch (key) {
            case "simple_stew": return "妈妈的味道...不对，这是神厨的味道！";
            case "veggie_soup": return "这一碗下去，感觉身体被净化了。";
            case "crispy_fish": return "外酥里嫩，连骨头都炸得酥脆！";
            case "farmers_breakfast": return "美好的一天，从这顿完美的早餐开始。";
            case "miners_tonic": return "这一口下去，基岩都能挖穿！";
            case "shepherds_pie": return "这就叫扎实！这就是力量！";
            case "surf_and_turf": return "山珍海味，尽在掌握。";
            case "spicy_curry": return "燃烧吧，我的卡路里！";
            case "wisdom_broth": return "甚至能感觉到宇宙的真理在脑海中回响。";
            case "royal_feast": return "只有真正的王者才配享用这等盛宴。";
            default: return null;
        }
    }
    
    public void cool(double amount) {
        temperature -= amount;
        if (temperature < 20.0) temperature = 20.0;
        location.getWorld().playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        location.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, location.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
    }
    
    public void stir(Player player) {
        lastInteractionTime = System.currentTimeMillis();
        
        if (state == CookingState.FINISHED) {
            retrieveResult(player);
            return;
        }
        
        // Visual feedback
        location.getWorld().spawnParticle(Particle.SPLASH, location.clone().add(0.5, 0.8, 0.5), 15, 0.3, 0.1, 0.3, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
        
        // Ladle usage: Mix and potentially start cooking
        cool(5.0); // Stirring cools slightly
        
        if (state == CookingState.PREPARING && temperature > 100.0) {
            startCooking(); // Force start
            location.getWorld().playSound(location, Sound.ENTITY_PLAYER_SPLASH, 0.5f, 1.0f);
        }
    }
    
    // Legacy method for non-player interaction if any
    public void stir() {
        stir(null);
    }
    
    public void retrieveResult(Player player) {
        if (state != CookingState.FINISHED && state != CookingState.BURNT) return;
        
        ItemStack output = inventory.getItem(OUTPUT_SLOT);
        if (output != null && output.getType() != org.bukkit.Material.AIR) {
            // Give to player
            player.getInventory().addItem(output).forEach((idx, leftover) -> {
                location.getWorld().dropItem(location, leftover);
            });
            inventory.setItem(OUTPUT_SLOT, null);
            player.playSound(location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            player.sendMessage("§a成功取出料理！");
        } else {
            // Already empty?
        }
        
        // Reset to IDLE if empty
        if (!hasIngredients() && (inventory.getItem(OUTPUT_SLOT) == null)) {
            state = CookingState.IDLE;
        }
    }

    private int getFuelTime(org.bukkit.Material material) {
        // Simple custom fuel mapping
        switch (material) {
            case COAL: return 1600; // 80s
            case CHARCOAL: return 1600;
            case OAK_LOG: return 300; // 15s
            case OAK_PLANKS: return 300;
            case BLAZE_ROD: return 2400;
            default: return 0;
        }
    }

    private void spawnInfoDisplay() {
        // Cleanup old displays first
        // 搜索半径设为 1.0，确保覆盖到方块中心的实体
        location.getWorld().getNearbyEntities(location.clone().add(0.5, 1.5, 0.5), 0.8, 0.8, 0.8).forEach(e -> {
            if (e instanceof TextDisplay) {
                e.remove();
            }
        });

        infoDisplay = (TextDisplay) location.getWorld().spawn(location.clone().add(0.5, 1.5, 0.5), TextDisplay.class);
        infoDisplay.setBillboard(Display.Billboard.CENTER);
        infoDisplay.setSeeThrough(true); // Can see through walls? Maybe better false
        infoDisplay.setSeeThrough(false);
        infoDisplay.setShadowed(true);
        infoDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // Transparent
        updateInfoDisplay();
    }
    
    private void updateInfoDisplay() {
        if (infoDisplay == null || !infoDisplay.isValid()) return;
        
        // Bar: [||||||||||]
        // Color: Blue (<100), Green (100-200), Red (>200)
        // Refined Logic for QTE Zone
        
        StringBuilder bar = new StringBuilder();
        int totalBars = 20;
        double maxTemp = 300.0;
        double tempPerBar = maxTemp / totalBars;
        int fill = (int) ((temperature / maxTemp) * totalBars);
        if (fill > totalBars) fill = totalBars;
        
        bar.append("§8[");
        
        // Determine thresholds
        double minOpt = (currentRecipe != null) ? currentRecipe.getOptimalTempMin() : 100.0;
        double maxOpt = (currentRecipe != null) ? currentRecipe.getOptimalTempMax() : 200.0;

        for (int i = 0; i < totalBars; i++) {
            double barTemp = i * tempPerBar;
            
            // Check if this segment represents an optimal zone
            boolean isOptimalZone = (barTemp >= minOpt && barTemp <= maxOpt);

            if (i < fill) {
                // Filled Bar
                String color;
                if (barTemp < minOpt) color = "§b"; // Cold (Blue)
                else if (barTemp > maxOpt) color = "§c"; // Hot (Red)
                else color = "§a"; // Optimal (Green)
                
                bar.append(color).append("|");
            } else {
                // Empty Bar
                if (isOptimalZone) {
                    bar.append("§2."); // Dark Green marker for target zone
                } else {
                    bar.append("§8."); // Grey for others
                }
            }
        }
        bar.append("§8]");
        
        String status = "";
        switch (state) {
            case IDLE: status = "§7空闲"; break;
            case PREPARING: status = "§e备料中..."; break;
            case COOKING: status = "§6烹饪中 🔥 " + (int)((double)cookingTimer/ (currentRecipe!=null?currentRecipe.getCookingTime():200) * 100) + "%"; break;
            case FINISHED: status = "§a完成! (右键取出)"; break;
            case BURNT: status = "§4糊了!"; break;
        }
        
        infoDisplay.text(Component.text(status + "\n" + bar.toString() + " §f" + (int)temperature + "°C"));
    }
    
    // Getters
    public Location getLocation() { return location; }
    @SuppressWarnings("unused")
    public double getTemperature() { return temperature; }
    @SuppressWarnings("unused")
    public CookingState getState() { return state; }
}
