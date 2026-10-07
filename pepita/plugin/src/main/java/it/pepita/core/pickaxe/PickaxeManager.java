package it.pepita.core.pickaxe;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import it.pepita.core.PepitaCore;
import it.pepita.core.armor.ArmorService;
import it.pepita.core.cosmetics.ArmorSkin;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.data.PlayerData;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/** Crea e aggiorna il piccone personale, la corazza, la bussola e gli oggetti speciali. */
public final class PickaxeManager {
    public static final String PICKAXE = "piccone";
    public static final String MENU = "menu";
    public static final String ARMOR = "divisa";
    public static final String SELECTOR = "selettore";
    public static final String PEPITA_ORO = "pepita_oro";
    public static final String BOMB = "bomba";
    public static final String SKIN = "skin";
    /** Skin add-on della corazza: id "skin_corazza:&lt;set&gt;:&lt;pezzo 0-3&gt;". */
    public static final String ARMOR_SKIN = "skin_corazza";
    /** Prefisso nel deposito delle skin per gli add-on della corazza: "corazza:&lt;set&gt;:&lt;pezzo&gt;". */
    public static final String DEPOSIT_ARMOR = "corazza:";

    private final PepitaCore plugin;

    public PickaxeManager(PepitaCore plugin) {
        this.plugin = plugin;
    }

    // ---------------- Livello del piccone ----------------

    public static double xpFor(int level) {
        return 150 * Math.pow(level, 1.6);
    }

    public void addXp(Player p, PlayerData d, double xp) {
        d.pickXp += xp * (1 + plugin.armor().bonus(d, ArmorService.XP));
        boolean up = false;
        while (d.pickXp >= xpFor(d.pickLevel) && d.pickLevel < 500) {
            d.pickXp -= xpFor(d.pickLevel);
            d.pickLevel++;
            double reward = 25.0 * d.pickLevel;
            plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, reward, "livello-piccone");
            up = true;
            Txt.send(p, "Il tuo piccone è salito al <gold>livello " + d.pickLevel + "</gold>! "
                    + Txt.pepite(reward) + " <dark_gray>• <gray>+0.5% soldi");
        }
        if (up) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.5);
            refresh(p);
        }
    }

    public PickTier tier(PlayerData d) {
        return PickTier.of(Math.max(0, d.pickTier));
    }

    /** Bonus effettivi della skin montata: base della rarità × moltiplicatore del tier. [soldi, pepite, quantum] */
    public double[] skinBonus(PlayerData d) {
        PickaxeSkin s = PickaxeSkin.byId(d.pickSkin);
        double t = tier(d).mult;
        return new double[]{s.rar.soldi * t, s.rar.pepite * t, s.rar.quantum * t};
    }

    // ---------------- Oggetti ----------------

    public ItemStack pickaxe(Player p, PlayerData d) {
        PickaxeSkin skin = PickaxeSkin.byId(d.pickSkin);
        PickTier tier = tier(d);
        List<String> lore = new ArrayList<>();
        double pct = d.pickXp / xpFor(d.pickLevel);
        lore.add("<gray>Proprietario: <white>" + Txt.esc(p.getName()));
        lore.add("<gray>Tier: " + tier.label() + " <dark_gray>(skin x" + Fmt.num(tier.mult) + ")");
        lore.add("<gray>Livello <gold>" + d.pickLevel + "</gold> " + Txt.bar(pct, 10, Txt.GOLD, "#4A4A4A") + " <gray>" + Fmt.pct(pct));
        lore.add("<gray>Blocchi scavati: <white>" + Fmt.num(d.blocchi));
        if (skin == PickaxeSkin.CLASSICO) lore.add("<gray>Skin: <dark_gray>nessuna <gray>(trascinane una qui)");
        else {
            double[] b = skinBonus(d);
            lore.add("<gray>Skin: " + skin.rar.color + skin.display + " <dark_gray>(" + Txt.esc(Txt.plain(Txt.mm(skin.rarity))) + ")");
            lore.add("  <" + Txt.SOLDI + ">+" + Fmt.pct(b[0]) + " soldi</" + Txt.SOLDI + "> <" + Txt.PEPITE + ">+" + Fmt.pct(b[1]) + " pepite</" + Txt.PEPITE + ">"
                    + (b[2] > 0 ? " <" + Txt.QUANTUM + ">+" + Fmt.pct(b[2]) + " quantum</" + Txt.QUANTUM + ">" : ""));
        }
        lore.add("");
        boolean any = false;
        for (Enchant.Cat cat : Enchant.Cat.values()) {
            List<String> lines = new ArrayList<>();
            for (Enchant e : Enchant.values()) {
                if (e.cat != cat) continue;
                int l = d.ench(e.id);
                if (l <= 0) continue;
                boolean off = d.disabledEnchants.contains(e.id);
                lines.add((off ? "<dark_gray><st>" : "<gray>") + " ▸ " + e.display + " <white>" + Fmt.num(l) + (off ? "</st> (spento)" : ""));
            }
            if (!lines.isEmpty()) {
                lore.add(cat.label);
                lore.addAll(lines);
                any = true;
            }
        }
        if (!any) lore.add("<dark_gray>Nessun incantesimo... ancora!");
        lore.add("");
        lore.add("<#FFD54A>▶ Tasto destro: incantesimi");
        lore.add("<#FFD54A>▶ Shift + tasto destro: tier e skin");
        lore.add("<#FFD54A>▶ Trascina una skin sul piccone per montarla");

        ItemBuilder b = new ItemBuilder(tier.material)
                .name("<gradient:#FFE259:#FFA751><b>Piccone di Pepita</b></gradient> <gray>[Lv." + d.pickLevel + "]")
                .lore(lore).unbreakable().id(PICKAXE).owner(p.getUniqueId().toString()).glow();
        if (skin.model != null) b.model(skin.model);
        else if (tier.model != null) b.model(tier.model);
        int eff = d.active("efficienza");
        if (eff > 0) b.enchant(Enchantment.EFFICIENCY, eff);
        return b.build();
    }

    public static boolean isPickaxe(ItemStack it) {
        return ItemBuilder.is(it, PICKAXE);
    }

    public ItemStack menuItem() {
        return new ItemBuilder(Material.NETHER_STAR)
                .name("<gradient:#FFE259:#FFA751><b>Menu di Pepita</b></gradient>")
                .lore("<gray>Miniere, incantesimi, casse,", "<gray>negozio, classifiche e altro.", "",
                        "<#FFD54A>▶ Tasto destro per aprire")
                .model("menu").id(MENU).build();
    }

    public ItemStack selector() {
        return new ItemBuilder(Material.COMPASS)
                .name("<gradient:#FFE259:#FFA751><b>Selettore</b></gradient>")
                .lore("<gray>Hub, Prigione, Miniere, PvP,", "<gray>Celle e Tutorial.", "",
                        "<#FFD54A>▶ Tasto destro per scegliere dove andare")
                .model("selettore").id(SELECTOR).build();
    }

    // ---------------- Skin come oggetti ----------------

    public ItemStack skinItem(PickaxeSkin s, int amount) {
        List<String> lore = new ArrayList<>();
        lore.add(s.rarity + (s.is3d ? " <dark_gray>• <gray>3D" : ""));
        lore.add("<gray>" + s.desc);
        lore.add("");
        lore.add("<gray>Bonus base:");
        lore.add("  <" + Txt.SOLDI + ">+" + Fmt.pct(s.rar.soldi) + " soldi");
        lore.add("  <" + Txt.PEPITE + ">+" + Fmt.pct(s.rar.pepite) + " pepite");
        if (s.rar.quantum > 0) lore.add("  <" + Txt.QUANTUM + ">+" + Fmt.pct(s.rar.quantum) + " quantum");
        lore.add("<dark_gray>× moltiplicatore del tier del piccone");
        lore.add("");
        lore.add("<#FFD54A>▶ Trascinala sul piccone per montarla");
        lore.add("<dark_gray>Si può scambiare con altri giocatori.");
        return new ItemBuilder(Material.PAPER, amount).name(s.rar.color + "<b>Skin: " + s.display)
                .lore(lore).model(s.model).id(SKIN + ":" + s.id).glow().build();
    }

    public static PickaxeSkin skinOf(ItemStack it) {
        String id = ItemBuilder.idOf(it);
        if (id == null || !id.startsWith(SKIN + ":")) return null;
        return PickaxeSkin.byIdOrNull(id.substring(SKIN.length() + 1));
    }

    /** Dà una skin come oggetto; se l'inventario è pieno finisce nel deposito delle skin. */
    public void giveSkin(Player p, PlayerData d, PickaxeSkin s) {
        if (s == null || s == PickaxeSkin.CLASSICO) return;
        if (p != null && p.isOnline() && p.getInventory().firstEmpty() >= 0) {
            var left = p.getInventory().addItem(skinItem(s, 1));
            if (left.isEmpty()) return;
        }
        d.skinDeposito.add(s.id);
        d.dirty = true;
        if (p != null) Txt.send(p, "Inventario pieno: la skin <white>" + s.display + "</white> è nel deposito (<yellow>/skin</yellow>).");
    }

    /** Monta una skin sul piccone (quella vecchia torna come oggetto). */
    public void mount(Player p, PlayerData d, PickaxeSkin s) {
        PickaxeSkin old = PickaxeSkin.byId(d.pickSkin);
        d.pickSkin = s.id;
        d.dirty = true;
        if (old != PickaxeSkin.CLASSICO && old != s) giveSkin(p, d, old);
        refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.3f);
        double[] b = skinBonus(d);
        Txt.send(p, "Skin montata: " + s.rar.color + s.display + " <gray>(+" + Fmt.pct(b[0]) + " soldi, +" + Fmt.pct(b[1]) + " pepite"
                + (b[2] > 0 ? ", +" + Fmt.pct(b[2]) + " quantum" : "") + ")");
        plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.SKIN, 1);
    }

    public void unmount(Player p, PlayerData d) {
        PickaxeSkin old = PickaxeSkin.byId(d.pickSkin);
        if (old == PickaxeSkin.CLASSICO) return;
        d.pickSkin = PickaxeSkin.CLASSICO.id;
        d.dirty = true;
        giveSkin(p, d, old);
        refresh(p);
        Txt.send(p, "Hai smontato la skin <white>" + old.display + "</white>: ora è un oggetto.");
    }

    /** Prima volta con la v2: le skin possedute diventano oggetti. */
    public void migrateSkins(Player p, PlayerData d) {
        if (d.skinMigrate) return;
        int n = 0;
        for (String id : new ArrayList<>(d.pickSkins)) {
            PickaxeSkin s = PickaxeSkin.byIdOrNull(id);
            if (s == null || s == PickaxeSkin.CLASSICO || s.id.equals(d.pickSkin)) continue;
            giveSkin(p, d, s);
            n++;
        }
        d.pickSkins.clear();
        d.pickSkins.add(PickaxeSkin.CLASSICO.id);
        if (PickaxeSkin.byIdOrNull(d.pickSkin) == null) d.pickSkin = PickaxeSkin.CLASSICO.id;
        d.skinMigrate = true;
        d.dirty = true;
        if (n > 0) Txt.send(p, "Novità: le skin del piccone ora sono <white>oggetti</white>! Ti abbiamo dato le tue <white>" + n
                + "</white> skin: trascinale sul piccone per montarle (o scambiale).");
    }

    // ---------------- Skin add-on della corazza ----------------

    /** Un add-on della corazza: set e pezzo (0 elmo, 1 corpetto, 2 gambali, 3 stivali). */
    public record ArmorAddon(ArmorSkin skin, int piece) {}

    private static Material leather(int piece) {
        return switch (piece) {
            case 0 -> Material.LEATHER_HELMET;
            case 1 -> Material.LEATHER_CHESTPLATE;
            case 2 -> Material.LEATHER_LEGGINGS;
            default -> Material.LEATHER_BOOTS;
        };
    }

    /** Pezzo (0-3) di un oggetto di cuoio della corazza, -1 se non è un pezzo. */
    public static int pieceOf(Material m) {
        return switch (m) {
            case LEATHER_HELMET -> 0;
            case LEATHER_CHESTPLATE -> 1;
            case LEATHER_LEGGINGS -> 2;
            case LEATHER_BOOTS -> 3;
            default -> -1;
        };
    }

    private static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};

    /** Oggetto add-on: il pezzo di cuoio con l'aspetto del set, non indossabile, da montare sulla corazza. */
    public ItemStack armorAddonItem(ArmorSkin s, int piece, PlayerData d) {
        ArmorService as = plugin.armor();
        List<String> lore = new ArrayList<>();
        lore.add(s.rarity + (s.model ? " <dark_gray>• <gray>3D" : "") + " <dark_gray>• <gray>Add-on per " + PIECE_NAMES[piece].toLowerCase());
        lore.add("<gray>" + s.desc);
        lore.add("");
        lore.add("<gray>Potenzia il pezzo su cui è montata:");
        lore.add("  " + ArmorService.mainStat(piece) + " +" + Fmt.pct(as.skinBase(s, piece)) + " <dark_gray>(base)");
        lore.add("<dark_gray>× (1 + " + Fmt.pct(as.skinLevelMult(1) - 1) + " per livello del pezzo)");
        if (d != null) lore.add("<gray>Sul tuo " + PIECE_NAMES[piece].toLowerCase() + " (lv " + d.armorLevels[piece] + "): " + ArmorService.mainStat(piece)
                + " +" + Fmt.pct(as.skinBonus(s, piece, d.armorLevels[piece])));
        lore.add("");
        lore.add("<#FFD54A>▶ Trascinala sul " + PIECE_NAMES[piece].toLowerCase() + " della corazza");
        lore.add("<#FFD54A>▶ oppure tasto destro per montarla");
        lore.add("<dark_gray>Si può scambiare con altri giocatori.");
        ItemBuilder b = new ItemBuilder(leather(piece)).name(s.rar.color + "<b>Skin: " + PIECE_NAMES[piece] + " " + s.display)
                .lore(lore).dye(s.color).id(ARMOR_SKIN + ":" + s.id + ":" + piece).glow();
        if (s.model) b.model(s.id + "_" + SUFFIX[piece]);
        ItemStack it = b.build();
        // è solo un add-on: non si indossa e non dà punti armatura
        it.unsetData(DataComponentTypes.EQUIPPABLE);
        it.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return it;
    }

    public static ArmorAddon addonOf(ItemStack it) {
        String id = ItemBuilder.idOf(it);
        if (id == null || !id.startsWith(ARMOR_SKIN + ":")) return null;
        return parseAddon(id.substring(ARMOR_SKIN.length() + 1));
    }

    /** "set:pezzo" → add-on (null se non valido o GALEOTTO). */
    public static ArmorAddon parseAddon(String v) {
        int c = v.lastIndexOf(':');
        if (c < 0) return null;
        ArmorSkin s = ArmorSkin.byIdOrNull(v.substring(0, c));
        int piece;
        try {
            piece = Integer.parseInt(v.substring(c + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (s == null || s == ArmorSkin.GALEOTTO || piece < 0 || piece > 3) return null;
        return new ArmorAddon(s, piece);
    }

    /** Dà un add-on della corazza; se l'inventario è pieno finisce nel deposito delle skin. */
    public void giveArmorAddon(Player p, PlayerData d, ArmorSkin s, int piece) {
        if (s == null || s == ArmorSkin.GALEOTTO) return;
        if (p != null && p.isOnline() && p.getInventory().firstEmpty() >= 0) {
            var left = p.getInventory().addItem(armorAddonItem(s, piece, d));
            if (left.isEmpty()) return;
        }
        d.skinDeposito.add(DEPOSIT_ARMOR + s.id + ":" + piece);
        d.dirty = true;
        if (p != null) Txt.send(p, "Inventario pieno: la skin <white>" + PIECE_NAMES[piece] + " " + s.display + "</white> è nel deposito (<yellow>/skin</yellow>).");
    }

    /** Dà i 4 add-on di un set (elmo, corpetto, gambali, stivali). */
    public void giveArmorSet(Player p, PlayerData d, ArmorSkin s) {
        for (int i = 0; i < 4; i++) giveArmorAddon(p, d, s, i);
    }

    /** Oggetto del deposito (skin del piccone o add-on della corazza). */
    public ItemStack depositItem(String entry, PlayerData d) {
        if (entry.startsWith(DEPOSIT_ARMOR)) {
            ArmorAddon a = parseAddon(entry.substring(DEPOSIT_ARMOR.length()));
            return a == null ? null : armorAddonItem(a.skin(), a.piece(), d);
        }
        PickaxeSkin s = PickaxeSkin.byIdOrNull(entry);
        return s == null || s == PickaxeSkin.CLASSICO ? null : skinItem(s, 1);
    }

    /** Monta un add-on su un pezzo della corazza (quello vecchio torna come oggetto). */
    public void mountArmorAddon(Player p, PlayerData d, ArmorSkin s, int piece) {
        ArmorSkin old = ArmorService.skinOn(d, piece);
        d.armorSkinPieces[piece] = s.id;
        d.dirty = true;
        if (old != ArmorSkin.GALEOTTO && old != s) giveArmorAddon(p, d, old, piece);
        applyArmor(p, d);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.3f);
        Txt.send(p, "Skin montata sul " + PIECE_NAMES[piece].toLowerCase() + ": " + s.rar.color + s.display + " <gray>("
                + Txt.plain(Txt.mm(ArmorService.mainStat(piece))) + " +" + Fmt.pct(plugin.armor().skinBonus(s, piece, d.armorLevels[piece])) + ")");
        plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.SKIN, 1);
    }

    public void unmountArmorAddon(Player p, PlayerData d, int piece) {
        ArmorSkin old = ArmorService.skinOn(d, piece);
        if (old == ArmorSkin.GALEOTTO) return;
        d.armorSkinPieces[piece] = ArmorSkin.GALEOTTO.id;
        d.dirty = true;
        giveArmorAddon(p, d, old, piece);
        applyArmor(p, d);
        Txt.send(p, "Hai smontato la skin <white>" + PIECE_NAMES[piece] + " " + old.display + "</white>: ora è un oggetto.");
    }

    /** Prima volta con le skin add-on: il set indossato va su tutti i pezzi, gli altri set posseduti diventano oggetti. */
    public void migrateArmorSkins(Player p, PlayerData d) {
        if (d.armorSkinMigrate) return;
        ArmorSkin worn = ArmorSkin.byId(d.armorSkin);
        for (int i = 0; i < 4; i++) if (ArmorService.skinOn(d, i) == ArmorSkin.GALEOTTO) d.armorSkinPieces[i] = worn.id;
        int n = 0;
        for (String id : new ArrayList<>(d.armorSkins)) {
            ArmorSkin s = ArmorSkin.byIdOrNull(id);
            if (s == null || s == ArmorSkin.GALEOTTO || s == worn) continue;
            giveArmorSet(p, d, s);
            n++;
        }
        d.armorSkins.clear();
        d.armorSkins.add(ArmorSkin.GALEOTTO.id);
        d.armorSkinMigrate = true;
        d.dirty = true;
        if (n > 0 || worn != ArmorSkin.GALEOTTO)
            Txt.send(p, "Novità: le skin della corazza ora sono <white>add-on</white>, una per pezzo, e potenziano il pezzo su cui le monti!"
                    + (n > 0 ? " Ti abbiamo dato le skin di <white>" + n + "</white> set: trascinale sui pezzi della corazza." : ""));
    }

    // ---------------- Corazza ----------------

    public static final String[] PIECE_NAMES = {"Elmo", "Corpetto", "Gambali", "Stivali"};
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int pieceIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            default -> 3;
        };
    }

    /** Pezzo della corazza con l'aspetto del set e i vantaggi del livello. */
    public ItemStack armorPiece(EquipmentSlot slot, ArmorSkin skin, boolean pack, PlayerData d) {
        int idx = pieceIndex(slot);
        Material m = switch (slot) {
            case HEAD -> Material.LEATHER_HELMET;
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case LEGS -> Material.LEATHER_LEGGINGS;
            default -> Material.LEATHER_BOOTS;
        };
        String suffix = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            default -> "boots";
        };
        int lvl = d == null ? 0 : d.armorLevels[idx];
        ArmorService as = plugin.armor();
        List<String> lore = new ArrayList<>();
        if (skin == ArmorSkin.GALEOTTO) lore.add("<gray>" + skin.display + " <dark_gray>• <gray>nessuna skin add-on");
        else lore.add("<gray>Skin: " + skin.rar.color + skin.display + " <dark_gray>• " + skin.rarity);
        lore.add("<gray>Livello <" + Txt.QUANTUM + ">" + lvl + "</" + Txt.QUANTUM + "><dark_gray>/" + as.maxLevel() + " "
                + Txt.bar(lvl / (double) as.maxLevel(), 10, Txt.QUANTUM, "#3A2A4A"));
        lore.add("");
        lore.add("<#E8D7FF><b>Vantaggi:</b>");
        double[] b = as.pieceBonus(idx, lvl);
        lore.add("  <" + Txt.SOLDI + ">+" + Fmt.pct(b[ArmorService.SOLDI]) + " <gray>Soldi");
        lore.add("  <#FFB86B>+" + Fmt.pct(b[ArmorService.XP]) + " <gray>Esperienza piccone");
        lore.add("  <" + Txt.PEPITE + ">+" + Fmt.pct(b[ArmorService.PEPITE]) + " <gray>Pepite");
        if (b[ArmorService.QUANTUM] > 0 || idx == 3) lore.add("  <" + Txt.QUANTUM + ">+" + Fmt.pct(b[ArmorService.QUANTUM]) + " <gray>Quantum");
        if (skin != ArmorSkin.GALEOTTO) lore.add("  " + ArmorService.mainStat(idx) + " +" + Fmt.pct(as.skinBonus(skin, idx, lvl)) + " <dark_gray>(skin x"
                + Fmt.num(as.skinLevelMult(lvl)) + ")");
        lore.add("");
        lore.add(lvl >= as.maxLevel() ? "<gold>✔ Livello massimo" : "<#B98CFF>▶ Clicca per migliorare la corazza");
        lore.add("<#FFD54A>▶ Trascina qui una skin add-on per montarla");
        ItemBuilder ib = new ItemBuilder(m)
                .name("<gradient:#3FE0F0:#D15BFF><b>" + PIECE_NAMES[idx] + " della Corazza</b></gradient> <gray>[" + lvl + "]")
                .lore(lore).unbreakable().id(ARMOR).dye(skin.color);
        if (pack) {
            ib.tooltipStyle("quantum");
            if (skin.model) ib.model(skin.id + "_" + suffix);
        }
        ItemStack it = ib.build();
        if (pack) {
            it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot)
                    .assetId(Key.key("pepita", skin.id))
                    .equipSound(Key.key("minecraft", "item.armor.equip_leather"))
                    .build());
        }
        return it;
    }

    public ItemStack armorPiece(EquipmentSlot slot, ArmorSkin skin, boolean pack) {
        return armorPiece(slot, skin, pack, null);
    }

    public ItemStack pepitaOro() {
        return new ItemBuilder(Material.GOLD_NUGGET)
                .name("<gradient:#FFF6B7:#FFD54A:#FFA726><b>✦ PEPITA D'ORO ✦</b></gradient>")
                .lore("<gray>La pepita leggendaria che dà il nome", "<gray>a questa prigione. Rarissima.", "",
                        "<#FFD54A>▶ Tasto destro per riscattarla", "<dark_gray>Scatena la Corsa all'Oro per tutti!")
                .model("pepita_oro").id(PEPITA_ORO).glow().build();
    }

    /** tipo: normale, grande, nucleare */
    public ItemStack bomb(String tipo, int amount) {
        String name = switch (tipo) {
            case "grande" -> "<#FF9F43><b>Bomba Grande</b>";
            case "nucleare" -> "<#B6FF3B><b>Bomba Nucleare</b>";
            default -> "<#FF6B6B><b>Bomba</b>";
        };
        int r = radius(tipo);
        return new ItemBuilder(Material.FIRE_CHARGE, amount)
                .name(name)
                .lore("<gray>Lanciala in una miniera per far", "<gray>esplodere i blocchi (raggio <white>" + r + "</white>).", "",
                        "<#FFD54A>▶ Tasto destro per lanciare")
                .model("bomba_" + tipo).id(BOMB + ":" + tipo).build();
    }

    public static int radius(String tipo) {
        return switch (tipo) {
            case "grande" -> 7;
            case "nucleare" -> 12;
            default -> 4;
        };
    }

    // ---------------- Inventario ----------------

    /** Ridà al giocatore piccone, bussola, menu e corazza se mancano. */
    public void ensureKit(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.pickTier < 0) {
            // giocatori della versione vecchia avevano già il piccone di diamante
            d.pickTier = d.newData ? PickTier.LEGNO.ordinal() : PickTier.DIAMANTE.ordinal();
            d.dirty = true;
        }
        migrateSkins(p, d);
        migrateArmorSkins(p, d);
        PlayerInventory inv = p.getInventory();
        boolean hasPick = false, hasMenu = false, hasSel = false;
        for (ItemStack it : inv.getContents()) {
            if (isPickaxe(it)) hasPick = true;
            if (ItemBuilder.is(it, MENU)) hasMenu = true;
            if (ItemBuilder.is(it, SELECTOR)) hasSel = true;
        }
        if (!hasPick) put(inv, 0, pickaxe(p, d));
        if (!hasSel) put(inv, 4, selector());
        if (!hasMenu) put(inv, 8, menuItem());
        applyArmor(p, d);
        refresh(p);
    }

    private static void put(PlayerInventory inv, int slot, ItemStack it) {
        ItemStack cur = inv.getItem(slot);
        if (cur == null || cur.getType().isAir()) inv.setItem(slot, it);
        else inv.addItem(it);
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    /** Indossa la corazza con la skin add-on di ogni pezzo (sostituisce solo i pezzi della corazza o slot vuoti). */
    public void applyArmor(Player p, PlayerData d) {
        boolean pack = d.hasPack;
        PlayerInventory inv = p.getInventory();
        if (isEmpty(inv.getHelmet()) || ItemBuilder.is(inv.getHelmet(), ARMOR)) inv.setHelmet(armorPiece(EquipmentSlot.HEAD, ArmorService.skinOn(d, 0), pack, d));
        if (isEmpty(inv.getChestplate()) || ItemBuilder.is(inv.getChestplate(), ARMOR)) inv.setChestplate(armorPiece(EquipmentSlot.CHEST, ArmorService.skinOn(d, 1), pack, d));
        if (isEmpty(inv.getLeggings()) || ItemBuilder.is(inv.getLeggings(), ARMOR)) inv.setLeggings(armorPiece(EquipmentSlot.LEGS, ArmorService.skinOn(d, 2), pack, d));
        if (isEmpty(inv.getBoots()) || ItemBuilder.is(inv.getBoots(), ARMOR)) inv.setBoots(armorPiece(EquipmentSlot.FEET, ArmorService.skinOn(d, 3), pack, d));
    }

    /** Ricostruisce il piccone nell'inventario con i dati aggiornati. */
    public void refresh(Player p) {
        PlayerData d = plugin.data().get(p);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isPickaxe(it)) inv.setItem(i, pickaxe(p, d));
        }
    }

    public boolean holdingPickaxe(Player p) {
        return isPickaxe(p.getInventory().getItemInMainHand());
    }
}
