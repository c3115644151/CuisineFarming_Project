import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import matplotlib.font_manager as fm
import os

plt.rcParams['axes.unicode_minus'] = False
plt.style.use('seaborn-v0_8-whitegrid')

# --- Configuration ---
# 1. Font Handling (Platform Independent)
# Explicitly try to load SimHei on Windows which we verified exists
font_names = ['SimHei', 'Microsoft YaHei', 'Arial Unicode MS']
if os.name == 'nt':
    # Check for simhei.ttf specifically to ensure we can load it
    simhei_path = r'C:\Windows\Fonts\simhei.ttf'
    if os.path.exists(simhei_path):
        # Add the font to the manager
        fm.fontManager.addfont(simhei_path)
        plt.rcParams['font.sans-serif'] = ['SimHei']
        plt.rcParams['font.family'] = ['sans-serif'] # Ensure family is sans-serif
    else:
         plt.rcParams['font.sans-serif'] = font_names
else:
    # Fallback for other OS
    chinese_fonts = [f for f in fm.findSystemFonts() if 'hei' in f.lower() or 'kai' in f.lower() or 'song' in f.lower()]
    if chinese_fonts:
        prop = fm.FontProperties(fname=chinese_fonts[0])
        plt.rcParams['font.family'] = prop.get_name()
    else:
        plt.rcParams['font.sans-serif'] = font_names

# Load Data
try:
    df = pd.read_csv('efficiency_data.csv')
except FileNotFoundError:
    print("Error: efficiency_data.csv not found. Please run the Java generator first.")
    exit(1)

# Create Multi-Panel Plot
fig = plt.figure(figsize=(20, 12), dpi=100)
gs = fig.add_gridspec(2, 2, height_ratios=[1, 1], hspace=0.3, wspace=0.2)

# --- Plot 1: Total Efficiency vs Concentration (The "Big Picture") ---
ax1 = fig.add_subplot(gs[0, 0])
resistance_types = ['Vanilla', 'Gene_R50', 'Gene_R100', 'Gene_R300']
colors = {'Vanilla': '#555555', 'Gene_R50': '#E74C3C', 'Gene_R100': '#F1C40F', 'Gene_R300': '#2ECC71'}
labels = {'Vanilla': '普通作物 (Vanilla)', 'Gene_R50': '基因作物 (R=50)', 
          'Gene_R100': '基因作物 (R=100)', 'Gene_R300': '基因作物 (R=300)'}

for r_type in resistance_types:
    subset = df[df['Type'] == r_type]
    if subset.empty: continue
    
    # Filter for the Concentration Sweep scenario (Growth=1.0)
    subset = subset[subset['GrowthGene'] == 1.0]
    
    style = '--' if 'Vanilla' in r_type else '-'
    width = 2 if 'Vanilla' in r_type else 3
    alpha = 0.8 if 'Vanilla' in r_type else 0.9
    
    ax1.plot(subset['Concentration'], subset['TotalEff'], label=labels[r_type], 
             color=colors[r_type], linestyle=style, linewidth=width, alpha=alpha)

    # Annotate Peaks
    if 'Vanilla' not in r_type:
        peak_row = subset.loc[subset['TotalEff'].idxmax()]
        ax1.plot(peak_row['Concentration'], peak_row['TotalEff'], 'o', color=colors[r_type])
        ax1.text(peak_row['Concentration'], peak_row['TotalEff'] + 0.1, 
                 f"{peak_row['TotalEff']:.2f}x", ha='center', color=colors[r_type], fontweight='bold')

ax1.set_title('图表 A: 综合生长效率 vs 肥料浓度 (基础肥力=100)', fontsize=16, fontweight='bold')
ax1.set_ylabel('总效率 (倍率)', fontsize=12)
ax1.set_xlabel('肥料浓度 (Concentration)', fontsize=12)
ax1.axhline(1.5, color='gray', linestyle=':', alpha=0.5)
ax1.text(0, 1.52, '普通作物基准 (1.5x)', color='gray')
ax1.legend(loc='upper right', frameon=True)
ax1.set_ylim(-1.0, 3.5)

# --- Plot 2: Full Stacked Impact Analysis (Waterfall/Bar Chart) ---
ax2 = fig.add_subplot(gs[0, 1])

# Extract Stacked Data
stack_types = ['Stack_1_Base', 'Stack_2_Fert', 'Stack_3_Res', 'Stack_4_Gene', 'Stack_5_Biome', 'Stack_6_Spirit']
stack_labels = ['1. 基础(1.0)', '+ 肥力修正', '+ 耐肥收益', '+ 生长基因', '+ 群系加成', '+ 地灵加成']
stack_values = []

# Get values
for st in stack_types:
    row = df[df['Type'] == st]
    if not row.empty:
        stack_values.append(row.iloc[0]['TotalEff'])
    else:
        stack_values.append(0)

# Calculate increments for waterfall
increments = [stack_values[0]] # First bar is total
for i in range(1, len(stack_values)):
    increments.append(stack_values[i] - stack_values[i-1])

# Colors for increments
# Base, Fert, Res, Gene, Biome, Spirit
bar_colors = ['#7F8C8D', '#95A5A6', '#2ECC71', '#9B59B6', '#3498DB', '#E67E22']

# Plot Waterfall
bottom = 0
for i in range(len(stack_types)):
    if i == 0:
        # Base bar
        ax2.bar(i, stack_values[i], color=bar_colors[i], label=stack_labels[i], width=0.6)
        ax2.text(i, stack_values[i] + 0.05, f"{stack_values[i]:.2f}x", ha='center', fontweight='bold')
    else:
        # Increment bars (floating)
        ax2.bar(i, increments[i], bottom=stack_values[i-1], color=bar_colors[i], label=stack_labels[i], width=0.6)
        # Add connecting line
        ax2.plot([i-1.4, i+0.4], [stack_values[i-1], stack_values[i-1]], 'k--', alpha=0.3, linewidth=1)
        # Label total at top
        ax2.text(i, stack_values[i] + 0.05, f"{stack_values[i]:.2f}x", ha='center', fontweight='bold')
        # Label increment inside/near bar
        ax2.text(i, stack_values[i-1] + increments[i]/2, f"+{increments[i]:.2f}", ha='center', color='white', fontweight='bold')

ax2.set_xticks(range(len(stack_types)))
ax2.set_xticklabels(stack_labels, rotation=0, fontsize=9)
ax2.set_title('图表 B: 全变量叠加效应 (理想极限状态)', fontsize=16, fontweight='bold')
ax2.set_ylabel('总生长效率 (倍率)', fontsize=12)
ax2.set_ylim(0, 4.8)
ax2.grid(axis='y', linestyle='--', alpha=0.5)

# Text box explaining conditions
desc = (
    "模拟环境设定 (极限叠加):\n"
    "• 基础肥力: 100 (+0.5)\n"
    "• 肥料浓度: 300 (配合R300=+1.5)\n"
    "• 基因: 耐肥300 / 生长5星\n"
    "• 群系: 特产富集 (+30%)\n"
    "• 地灵: 守护者模式 (+10%)\n"
    "注: 基础值从 1.0 开始"
)
ax2.text(0.02, 0.95, desc, transform=ax2.transAxes, verticalalignment='top', 
         bbox=dict(boxstyle='round', facecolor='white', alpha=0.8))


# --- Plot 3: Growth Gene Impact (Independent Variable) ---
ax3 = fig.add_subplot(gs[1, 0])
growth_data = df[df['Type'] == 'Growth_Sweep']

if not growth_data.empty:
    ax3.plot(growth_data['GrowthGene'], growth_data['TotalEff'], color='#9B59B6', linewidth=3)
    
    # Mark 1.0 (Neutral) and 5.0 (Max)
    val_1 = growth_data[growth_data['GrowthGene'] >= 1.0].iloc[0]
    val_5 = growth_data.iloc[-1]
    
    ax3.plot(val_1['GrowthGene'], val_1['TotalEff'], 'o', color='#9B59B6')
    ax3.text(val_1['GrowthGene'], val_1['TotalEff'] - 0.2, f"1.0星: {val_1['TotalEff']:.2f}x", ha='center')
    
    ax3.plot(val_5['GrowthGene'], val_5['TotalEff'], 'o', color='#9B59B6')
    ax3.text(val_5['GrowthGene'], val_5['TotalEff'] - 0.2, f"5.0星: {val_5['TotalEff']:.2f}x", ha='center')

    ax3.set_title('图表 C: 生长基因(Growth Speed) 独立影响 (环境: R=300, C=300)', fontsize=14)
    ax3.set_ylabel('总效率 (基准=3.0)', fontsize=12)
    ax3.set_xlabel('生长基因数值 (0.5 - 5.0)', fontsize=12)
    ax3.grid(True, linestyle='--')


# --- Plot 4: Component Breakdown (Stacked Area) for R=300 ---
ax4 = fig.add_subplot(gs[1, 1])
r300_data = df[(df['Type'] == 'Gene_R300') & (df['GrowthGene'] == 1.0)]

if not r300_data.empty:
    x = r300_data['Concentration']
    y_base = r300_data['BaseEff'] # This includes 1.0 + Fertility
    y_res = r300_data['ResBonus'] # Can be negative!
    
    # Stacked plot is tricky with negative values, so we plot lines + fill
    ax4.plot(x, y_base, label='基础效率 (Base+Fertility)', color='#95A5A6', linestyle='--')
    ax4.plot(x, r300_data['TotalEff'], label='最终效率 (Total)', color='#2ECC71', linewidth=2)
    
    # Fill the "Bonus" area
    ax4.fill_between(x, y_base, r300_data['TotalEff'], where=(r300_data['TotalEff'] >= y_base),
                     color='#2ECC71', alpha=0.3, label='耐肥收益 (Positive)')
    
    # Fill the "Penalty" area
    ax4.fill_between(x, y_base, r300_data['TotalEff'], where=(r300_data['TotalEff'] < y_base),
                     color='#E74C3C', alpha=0.3, label='烧苗惩罚 (Negative)')

    ax4.set_title('图表 D: 效率构成细分 (以 R=300 为例)', fontsize=14)
    ax4.set_ylabel('效率贡献', fontsize=12)
    ax4.set_xlabel('肥料浓度', fontsize=12)
    ax4.legend(loc='lower left', fontsize=9)
    ax4.set_ylim(-1.0, 3.5)

# Save
plt.suptitle('CuisineFarming 综合生长模型全解 (V2)', fontsize=20, fontweight='bold')
plt.tight_layout(rect=[0, 0.03, 1, 0.95])

plt.savefig('comprehensive_efficiency_chart.png')
print("Chart saved to comprehensive_efficiency_chart.png")
