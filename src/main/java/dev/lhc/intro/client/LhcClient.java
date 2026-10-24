package dev.lhc.intro.client;

import dev.lhc.intro.InfoCardPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * OPTIONAL client half. Without it, viewers still get the full cinematic (camera, titles, subtitle);
 * the info card is then shown as an action-bar line instead.
 */
public class LhcClient implements ClientModInitializer {
    private static boolean visible;
    private static long changedAt;
    private static InfoCardPayload card;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(InfoCardPayload.TYPE, (payload, ctx) -> {
            if (payload.show()) card = payload;
            visible = payload.show();
            changedAt = System.currentTimeMillis();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("lhc_intro", "info_card"), LhcClient::render);
    }

    // ---- drawing -----------------------------------------------------------------------------
    private static final int GOLD = 0xFFFFAA00, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA;

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (card == null) return;
        long now = System.currentTimeMillis();
        float k = visible ? ease((now - changedAt) / 380f) : 1f - ease((now - changedAt) / 300f);
        if (k <= 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenH = mc.getWindow().getGuiScaledHeight();

        int w = 168, pad = 8;
        List<String> lines = wrap(font, card.description(), w - pad * 2 - 4);
        int h = pad + 12 + 11 + 11 + 5 + lines.size() * 10 + pad;

        int x = 10 - (int) ((1f - k) * (w + 20));
        int y = screenH - 54 - h;                       // above the hotbar row, bottom-left like the reference

        // card body + gold frame + accent bar
        g.fill(x, y, x + w, y + h, 0xE6101010);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + h - 1, x + w, y + h, GOLD);
        g.fill(x, y, x + 1, y + h, GOLD);
        g.fill(x + w - 1, y, x + w, y + h, GOLD);
        g.fill(x + 1, y + 1, x + 4, y + h - 1, GOLD);

        int tx = x + pad + 4, ty = y + pad;
        text(g, font, Component.literal(String.format("#%02d %s", card.index(), card.name())).withStyle(ChatFormatting.BOLD), tx, ty, WHITE);
        text(g, font, Component.literal(card.role().toUpperCase()), tx, ty + 12, GOLD);
        text(g, font, Component.literal(String.format("MEMBER %02d OF %02d", card.index(), card.total())), tx, ty + 23, GRAY);
        int dy = ty + 23 + 11 + 5;
        for (String line : lines) { text(g, font, Component.literal(line), tx, dy, WHITE); dy += 10; }
    }

    /**
     * Only these two helper bodies touch GuiGraphicsExtractor text drawing. If a future snapshot renames the
     * method, this is the single place to change (drawString / text / etc).
     */
    private static void text(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int color) {
        g.text(font, c, x, y, color);
    }

    private static List<String> wrap(Font font, String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(test) > maxW && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
