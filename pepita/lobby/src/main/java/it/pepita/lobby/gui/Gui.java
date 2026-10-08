package it.pepita.lobby.gui;

import it.pepita.lobby.PepitaLobby;
import it.pepita.lobby.util.ItemBuilder;
import it.pepita.lobby.util.Txt;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Stile "fantasy" dei menu (resource pack v2): titolo composto con il font pepita:gui
 * [-8] [cornice N righe] [-169] [emblema] [spazio] [titolo colorato], riempitivi di pietra e icone su carta.
 * Senza resource pack i menu restano testuali con oggetti vanilla.
 */
public final class Gui {
    private Gui() {}

    /** Emblemi 12x12 del font pepita:gui, nell'ordine dei glifi da U+E010. */
    public enum Emblem {
        MAIN, MINIERE, INCANTESIMI, NEGOZIO, SKIN, CASSE, ARMATURA, BATTLEPASS, TRAGUARDI, CELLE, SELETTORE,
        CONFERMA, CLASSIFICHE, IMPOSTAZIONI, QUANTUM, TUTORIAL, LUCKY;

        public char glyph() {
            return (char) (0xE010 + ordinal());
        }
    }

    // spazi del font pepita:gui
    private static final String NEG_8 = "";
    /** -169 = -128 -32 -8 -1: dopo la cornice (avanza 177) si torna a x=8. */
    private static final String BACK_169 = "";

    public static boolean pack(Player p) {
        return PepitaLobby.get().hasPack(p);
    }

    /** Titolo del menu per quel giocatore. title = MiniMessage senza colore di base. */
    public static Component title(boolean pack, int rows, Emblem e, String title) {
        if (!pack) return Txt.mm("<dark_gray>" + title);
        int n = Math.max(1, Math.min(6, rows));
        String glyphs = NEG_8 + (char) (0xE000 + n) + BACK_169 + (e == null ? Emblem.MAIN : e).glyph();
        return Txt.mm("<white><font:pepita:gui>" + Txt.esc(glyphs) + "</font></white> <#FFE7A3>" + title);
    }

    /** Riempitivo: pietra scura (riempi) o con filo d'oro (riempi_oro) senza tooltip. */
    public static ItemStack filler(boolean pack, boolean gold) {
        ItemBuilder b = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").hideTooltip();
        if (pack) b.model(gold ? "riempi_oro" : "riempi");
        return b.build();
    }

    /** Pulsante con icona del pack (carta + custom_model_data) o materiale vanilla senza pack. */
    public static ItemBuilder button(boolean pack, String icon, Material fallback) {
        return pack ? new ItemBuilder(Material.PAPER).model(icon) : new ItemBuilder(fallback);
    }

    public static ItemBuilder button(Player p, String icon, Material fallback) {
        return button(pack(p), icon, fallback);
    }

    public static ItemStack back(Player p) {
        return button(p, "gui_indietro", Material.ARROW).name("<gray>← Indietro").build();
    }

    public static ItemStack close(Player p) {
        return button(p, "gui_chiudi", Material.BARRIER).name("<red>Chiudi").build();
    }

    public static ItemStack next(Player p) {
        return button(p, "gui_avanti", Material.SPECTRAL_ARROW).name("<gray>Avanti →").build();
    }

    public static ItemStack prev(Player p) {
        return button(p, "gui_indietro", Material.ARROW).name("<gray>← Pagina precedente").build();
    }
}
