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
        lore.add("<gray>" + skin.display + " <dark_gray>• " + skin.rarity);
        lore.add("<gray>Livello <" + Txt.QUANTUM + ">" + lvl + "</" + Txt.QUANTUM + "><dark_gray>/" + as.maxLevel() + " "
                + Txt.bar(lvl / (double) as.maxLevel(), 10, Txt.QUANTUM, "#3A2A4A"));
        lore.add("");
        lore.add("<#E8D7FF><b>Vantaggi:</b>");
        double[] b = as.pieceBonus(idx, lvl);
        lore.add("  <" + Txt.SOLDI + ">+" + Fmt.pct(b[ArmorService.SOLDI]) + " <gray>Soldi");
        lore.add("  <#FFB86B>+" + Fmt.pct(b[ArmorService.XP]) + " <gray>Esperienza piccone");
        lore.add("  <" + Txt.PEPITE + ">+" + Fmt.pct(b[ArmorService.PEPITE]) + " <gray>Pepite");
        if (b[ArmorService.QUANTUM] > 0 || idx == 3) lore.add("  <" + Txt.QUANTUM + ">+" + Fmt.pct(b[ArmorService.QUANTUM]) + " <gray>Quantum");
        lore.add("");
        lore.add(lvl >= as.maxLevel() ? "<gold>✔ Livello massimo" : "<#B98CFF>▶ Clicca per migliorare la corazza");
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

    /** Indossa la corazza con il set scelto (sostituisce solo i pezzi della corazza o slot vuoti). */
    public void applyArmor(Player p, PlayerData d) {
        ArmorSkin skin = ArmorSkin.byId(d.armorSkin);
        boolean pack = d.hasPack;
        PlayerInventory inv = p.getInventory();
        if (isEmpty(inv.getHelmet()) || ItemBuilder.is(inv.getHelmet(), ARMOR)) inv.setHelmet(armorPiece(EquipmentSlot.HEAD, skin, pack, d));
        if (isEmpty(inv.getChestplate()) || ItemBuilder.is(inv.getChestplate(), ARMOR)) inv.setChestplate(armorPiece(EquipmentSlot.CHEST, skin, pack, d));
        if (isEmpty(inv.getLeggings()) || ItemBuilder.is(inv.getLeggings(), ARMOR)) inv.setLeggings(armorPiece(EquipmentSlot.LEGS, skin, pack, d));
        if (isEmpty(inv.getBoots()) || ItemBuilder.is(inv.getBoots(), ARMOR)) inv.setBoots(armorPiece(EquipmentSlot.FEET, skin, pack, d));
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
