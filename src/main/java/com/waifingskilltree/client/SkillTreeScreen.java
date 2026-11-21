package com.waifingskilltree.client;

import com.waifingskilltree.data.AttrMod;
import com.waifingskilltree.data.SkillNode;
import com.waifingskilltree.data.SkillRules;
import com.waifingskilltree.data.SkillTree;
import com.waifingskilltree.data.TreeLocks;
import com.waifingskilltree.net.C2SBuyPoint;
import com.waifingskilltree.net.C2SRefund;
import com.waifingskilltree.net.C2SRespec;
import com.waifingskilltree.net.C2SToggleAttribute;
import com.waifingskilltree.net.C2SUnlock;
import com.waifingskilltree.net.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

import static com.waifingskilltree.client.TreeRender.*;

/**
 * Skill tree screen (play only; trees are edited with the HTML editor).
 * Drawn procedurally with the same look as the editor: dark starfield, coloured regions, metal frames
 * (silver small nodes, gold notables, bronze keystones; amber = one way, cyan = choice, violet = portal) and a bottom bar.
 * Lines that do not touch the player's path are almost transparent, so the tree reads as "where am I and where can I go".
 */
public class SkillTreeScreen extends Screen {
    private static final ResourceLocation DEFAULT_BG = new ResourceLocation(SkillTree.DEFAULT_BG);
    /** Links at least this long are drawn as a jump: a silver bridge, and from PORTAL_LEN on a violet portal. */
    private static final float BRIDGE_LEN = 250, PORTAL_LEN = 500;
    private static final int MARGIN = 10, TAB_H = 24, TAB_W = 28, BAR_H = 28;

    // palette (ARGB)
    private static final int BG = 0xFF0A0D16, BG_EDGE = 0xFF04060B, PANEL = 0xE00C1018, PANEL_LINE = 0xFF8A6A2E;
    private static final int GOLD = 0xFFF2C14E, CHOICE_COL = 0xFF55C8EE, ONEWAY_COL = 0xFFFFB347, BRIDGE_COL = 0xFFDCE6FF, GOLD_TXT = 0xFFF4D27A, PATH_DIM = 0xFFAAB4CC, WORM = 0xFFB07AFF, HIT = 0xFFFFE97A;
    private static final int DEFAULT_NODE = 0xFF6B6F7A;
    private static final int[] GOLD_M = {0xFFFFF2C4, 0xFFD8B36A, 0xFF8A6424}, SILVER_M = {0xFFFFFFFF, 0xFFC3C8D2, 0xFF6B7180},
            BRONZE_M = {0xFFFFD6B0, 0xFFC9754A, 0xFF6E3218},
            ONEWAY_BIG = {0xFFE6FBFF, 0xFF5CC8E8, 0xFF1D5F78}, ONEWAY_SMALL = {0xFFE6FBFF, 0xFF7FD3EC, 0xFF2A6A80},
            WORM_BIG = {0xFFF1E4FF, 0xFFA77BF0, 0xFF4B2A86}, WORM_SMALL = {0xFFF1E4FF, 0xFFB896F2, 0xFF55358F},
            AMBER_BIG = {0xFFFFF0D8, 0xFFFFB347, 0xFF8A5410}, AMBER_SMALL = {0xFFFFF4E0, 0xFFFFC46E, 0xFF94601C};

    private enum State {TAKEN, AVAILABLE, LOCKED}

    /** Line display: PATH = only the player's path and what they can reach stand out; ALL = every line visible. */
    private static boolean allLines = false;
    /** View per tree (pan x, pan y, zoom), kept while the game runs. */
    private static final Map<String, float[]> VIEWS = new HashMap<>();

    private String currentId;
    private double panX, panY;
    private float zoom = 0.8f;
    private boolean panning, dragged;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    // per-frame caches (recomputed when the player data, tree or second changes)
    private final Map<String, State> states = new HashMap<>();
    private String statesKey = "";
    private Map<String, Integer> conn = new HashMap<>();
    @Nullable private SkillNode hovered;

    // ---- node search (like Path of Exile's): matching nodes are ringed, the rest dimmed
    private EditBox search;
    private String searchQ = "";
    private String hitsKey = "";
    private final Set<String> hits = new HashSet<>();
    private final Map<String, String> haystacks = new HashMap<>();
    private int hitCursor = -1;
    private StyledButton buyBtn, linesBtn;

    public SkillTreeScreen() {
        super(Component.translatable("waifingskilltree.screen.title"));
    }

    // ================================================================ window and data

    private int wx0() { return MARGIN; }
    private int wy0() { return MARGIN + (tabs().size() > 1 ? TAB_H : 0); }
    private int wx1() { return width - MARGIN; }
    private int wy1() { return height - MARGIN; }
    private int barY() { return wy1() - BAR_H; }
    private int cx() { return (wx0() + wx1()) / 2; }
    private int cy() { return (wy0() + barY()) / 2; }

    /** Trees shown as tabs: a tree closed with requires_unlock stays hidden (no tab at all) until a node opens it. */
    private List<String> tabs() {
        Set<String> opened = TreeLocks.unlocked(ClientTrees.trees, ClientTrees.taken);
        List<String> out = new ArrayList<>();
        for (var e : ClientTrees.trees.entrySet())
            if (!e.getValue().requiresUnlock || TreeLocks.opened(e.getValue(), opened)) out.add(e.getKey());
        return out;
    }

    @Nullable
    private SkillTree tree() {
        Map<String, SkillTree> v = ClientTrees.trees;
        List<String> shown = tabs();
        if (currentId == null || !v.containsKey(currentId) || !shown.contains(currentId)) {
            currentId = shown.isEmpty() ? null : shown.get(0);
            loadView();
        }
        return currentId == null ? null : v.get(currentId);
    }

    private void saveView() {
        if (currentId != null) VIEWS.put(currentId, new float[]{(float) panX, (float) panY, zoom});
    }

    private void loadView() {
        float[] v = currentId == null ? null : VIEWS.get(currentId);
        if (v != null) { panX = v[0]; panY = v[1]; zoom = v[2]; }
        else { panX = panY = 0; zoom = 0.8f; }
    }

    public void onDataChanged() {
        statesKey = "";
        rebuildWidgets();
    }

    @Override
    public void removed() {
        saveView();
        super.removed();
    }

    @Override
    protected void init() {
        tree();
        int y = barY() + (BAR_H - 20) / 2;
        search = new EditBox(font, wx0() + 8, y + 1, 150, 18, Component.translatable("waifingskilltree.search"));
        search.setHint(Component.translatable("waifingskilltree.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(60);
        search.setValue(searchQ);
        search.setTooltip(Tooltip.create(Component.translatable("waifingskilltree.search.tip")));
        search.setResponder(v -> {
            searchQ = v.trim().toLowerCase(Locale.ROOT);
            hitCursor = -1;
        });
        addRenderableWidget(search);

        // right side of the bar, from the right: [reset] [points | + buy] [lines icon]
        int x = wx1() - 8;
        if (ClientTrees.selfRefund) {   // the Reset button only exists when the server allows free respecs
            x -= 64;
            addRenderableWidget(new StyledButton(x, y, 64, 20, b -> Network.sendToServer(new C2SRespec()),
                    (g, bx, by, w, h, hov, act) -> g.drawCenteredString(font, Component.translatable("waifingskilltree.reset").getString(), bx + w / 2, by + 6, hov ? 0xFFFF9A7A : 0xFFD08A70)));
            x -= 6;
        }
        if (ClientTrees.buyCost >= 0 || ClientTrees.buyCost == -2) {
            boolean maxed = ClientTrees.buyCost == -2;
            String cost = maxed ? Component.translatable("waifingskilltree.buy.max").getString() : Component.translatable("waifingskilltree.buy", ClientTrees.buyCost).getString();
            int bw = font.width(cost) + 22;
            x -= bw;
            buyBtn = addRenderableWidget(new StyledButton(x, y, bw, 20, b -> Network.sendToServer(new C2SBuyPoint()), (g, bx, by, w, h, hov, act) -> {
                int c = act ? (hov ? 0xFFB8FF70 : 0xFF80FF20) : 0xFF6A6F78;
                g.fill(bx + 4, by + 4, bx + 16, by + 16, act ? 0xFF1E3A10 : 0xFF1A1D24);   // "+" chip
                g.renderOutline(bx + 4, by + 4, 12, 12, c);
                g.fill(bx + 7, by + 9, bx + 13, by + 11, c);
                g.fill(bx + 9, by + 7, bx + 11, by + 13, c);
                g.drawString(font, cost, bx + 19, by + 6, c, true);
            }));
            buyBtn.joinLeft = true;
            buyBtn.setTooltip(Tooltip.create(maxed ? Component.translatable("waifingskilltree.buy.max_tip") : Component.translatable("waifingskilltree.buy.tip", ClientTrees.buyCost)));
        } else {
            buyBtn = null;
        }
        ptsRight = x;                  // the points panel is drawn by the HUD, glued to the left of the buy button
        x -= ptsWidth() + 6 + 20;
        linesBtn = addRenderableWidget(new StyledButton(x, y, 20, 20, b -> allLines = !allLines, (g, bx, by, w, h, hov, act) -> {
            // glyph: three nodes joined by lines; gold when every line is shown
            int c = allLines ? GOLD : (hov ? 0xFFC8D0E0 : 0xFF7F889C);
            g.fill(bx + 5, by + 13, bx + 10, by + 14, c);
            g.fill(bx + 10, by + 6, bx + 11, by + 14, c);
            g.fill(bx + 10, by + 6, bx + 15, by + 7, c);
            g.fill(bx + 3, by + 12, bx + 6, by + 15, c);
            g.fill(bx + 9, by + 9, bx + 12, by + 12, c);
            g.fill(bx + 14, by + 5, bx + 17, by + 8, c);
        }));
        linesBtn.setTooltip(Tooltip.create(Component.translatable("waifingskilltree.lines.tip")));
    }

    /** Right edge of the points panel (left edge of the buy button). */
    private int ptsRight;

    private String ptsText() {
        return Component.translatable("waifingskilltree.points.short", ClientTrees.points).getString();
    }

    private int ptsWidth() {
        return font.width(ptsText()) + 22;
    }

    @FunctionalInterface
    private interface Painter {
        void paint(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active);
    }

    /** Dark button with a thin gold frame, drawn like the rest of the screen instead of the vanilla stone button. */
    private static final class StyledButton extends Button {
        private final Painter painter;
        boolean joinLeft;

        StyledButton(int x, int y, int w, int h, OnPress press, Painter painter) {
            super(x, y, w, h, Component.empty(), press, DEFAULT_NARRATION);
            this.painter = painter;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHoveredOrFocused() && active;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, hov ? 0xFF1C2433 : 0xFF121722);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
            g.renderOutline(x, y, w, h, hov ? 0xFFE0B860 : PANEL_LINE);
            if (joinLeft) g.fill(x, y + 1, x + 1, y + h - 1, 0xFF3A3020);   // softer seam with the points panel
            painter.paint(g, x, y, w, h, hov, active);
        }
    }

    private void updateButtons() {
        if (buyBtn != null && minecraft != null && minecraft.player != null) {
            int cost = ClientTrees.buyCost;
            buyBtn.active = cost >= 0 && (minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= cost);
        }
    }

    // ---------------------------------------------------------------- search

    /** Text a node can be found by: title, description, icon, attributes (ids, names and the "+2 Max Health" line), recipes, commands. */
    private String haystack(SkillNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append(n.id).append(' ').append(n.title).append(' ').append(String.join(" ", n.description)).append(' ').append(n.icon).append(' ').append(n.kind);
        if (n.isStart()) sb.append(" start inicio");
        if (keystone(n)) sb.append(" keystone");
        if (!n.group.isEmpty()) sb.append(' ').append(n.group.replace('_', ' '));
        for (AttrMod a : n.attributes) {
            String id = a.attribute();
            sb.append(' ').append(id).append(' ').append(id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('.', ' '));
            sb.append(' ').append(attrLine(a, false).getString());
            if (a.condition() != null) sb.append(' ').append(a.condition().describe().getString());
        }
        n.recipes.forEach(r -> sb.append(' ').append(r));
        n.unlockCommands.forEach(c -> sb.append(' ').append(c));
        n.clickRecipes.forEach(c -> sb.append(' ').append(c));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Ids of the nodes of {@code t} matching every word of the query (recomputed only when tree or query change). */
    private Set<String> hits(SkillTree t) {
        String key = t.id + "|" + searchQ + "|" + ClientTrees.version;
        if (!key.equals(hitsKey)) {
            if (!hitsKey.startsWith(t.id + "|")) haystacks.clear();
            hitsKey = key;
            hits.clear();
            String[] words = searchQ.split("\\s+");
            if (!searchQ.isEmpty()) {
                for (SkillNode n : t.nodes.values()) {
                    String h = haystacks.computeIfAbsent(n.id, k -> haystack(n));
                    boolean all = true;
                    for (String w : words) if (!h.contains(w)) { all = false; break; }
                    if (all) hits.add(n.id);
                }
            }
        }
        return hits;
    }

    /** Enter in the search box: jump to the next match (Shift+Enter: previous). */
    private void gotoHit(int step) {
        SkillTree t = tree();
        if (t == null || searchQ.isEmpty()) return;
        List<String> ids = new ArrayList<>(hits(t));
        if (ids.isEmpty()) return;
        ids.sort(Comparator.comparingDouble((String id) -> Math.atan2(t.nodes.get(id).y, t.nodes.get(id).x)).thenComparing(id -> id));
        hitCursor = Math.floorMod(hitCursor + step, ids.size());
        SkillNode n = t.nodes.get(ids.get(hitCursor));
        if (n != null) {
            zoom = Math.max(zoom, 1.0f);
            panX = -n.x * zoom;
            panY = -n.y * zoom;
        }
    }

    // ---------------------------------------------------------------- rules

    /** Free refunds are allowed by the server config (ops always may). */
    private boolean canRefund() {
        return ClientTrees.selfRefund || (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2));
    }

    private float worldX(double mx) { return (float) ((mx - cx() - panX) / zoom); }
    private float worldY(double my) { return (float) ((my - cy() - panY) / zoom); }

    private boolean inWindow(double mx, double my) {
        return mx >= wx0() && mx < wx1() && my >= wy0() && my < barY();
    }

    @Nullable
    private SkillNode hit(SkillTree t, float wx, float wy) {
        SkillNode best = null;
        float bestD = Float.MAX_VALUE;
        for (SkillNode n : t.nodes.values()) {
            float dx = n.x - wx, dy = n.y - wy, d = dx * dx + dy * dy, r = n.radius() + 2;
            if (d <= r * r && d <= bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private State computeState(SkillTree t, SkillNode n) {
        if (ClientTrees.has(t.id, n.id)) return State.TAKEN;
        if (ClientTrees.isGated(t.id, n.id) || !ClientTrees.treeAllowed(t)) return State.LOCKED;
        if (!SkillRules.groupAllowed(t, n, id -> ClientTrees.level(t.id, id))) return State.LOCKED;
        return SkillRules.canTake(t, n, id -> ClientTrees.level(t.id, id)) ? State.AVAILABLE : State.LOCKED;
    }

    /** States of every node, cached until the player data or the tree changes (and refreshed once a second). */
    private void refreshStates(SkillTree t) {
        String key = t.id + "|" + ClientTrees.version + "|" + Util.getMillis() / 1000;
        if (key.equals(statesKey)) return;
        statesKey = key;
        states.clear();
        for (SkillNode n : t.nodes.values()) states.put(n.id, computeState(t, n));
        conn = connTypes(t);
    }

    private State state(SkillTree t, SkillNode n) {
        refreshStates(t);
        State s = states.get(n.id);
        return s != null ? s : computeState(t, n);
    }

    private boolean anyTaken(SkillTree t) {
        Map<String, Integer> m = ClientTrees.taken.get(t.id);
        if (m == null) return false;
        for (int v : m.values()) if (v > 0) return true;
        return false;
    }

    private ItemStack icon(String id) {
        return iconCache.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        });
    }

    /** Node colour #RRGGBB as ARGB, or the neutral grey. */
    private static int colour(SkillNode n) {
        String c = n.color;
        if (c != null && c.length() == 7 && c.charAt(0) == '#') {
            try {
                return 0xFF000000 | Integer.parseInt(c.substring(1), 16);
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_NODE;
    }

    /** Special lines touching each node: bit 1 = one way, 2 = portal (link of PORTAL_LEN or more), 4 = choice. The frame takes that metal. */
    private static Map<String, Integer> connTypes(SkillTree t) {
        Map<String, Integer> m = new HashMap<>();
        for (SkillTree.Link l : t.links.values()) {
            SkillNode a = t.nodes.get(l.from), b = t.nodes.get(l.to);
            if (a == null || b == null) continue;
            int bit = (SkillTree.ONEWAY.equals(l.type) ? 1 : 0) | (SkillTree.CHOICE.equals(l.type) ? 4 : 0)
                    | (Math.hypot(b.x - a.x, b.y - a.y) >= PORTAL_LEN ? 2 : 0);
            if (bit == 0) continue;
            m.merge(l.from, bit, (x, y) -> x | y);
            m.merge(l.to, bit, (x, y) -> x | y);
        }
        return m;
    }

    /** Nodes of a limited group are choices ("pick 2 of 4"): they get the bronze keystone frame. */
    private static boolean keystone(SkillNode n) {
        return n.group != null && !n.group.isEmpty();
    }

    /** Visual radius including the frame decorations (same as the editor's frameRadius). */
    private static float frameR(SkillNode n) {
        int r = n.radius();
        return keystone(n) ? r + 11 : SkillNode.LARGE.equals(n.shape) ? r + 9 : SkillNode.MEDIUM.equals(n.shape) ? r + 4 : r + 1;
    }

    /** 0..1 slow sine used by the pulsing rings. */
    private static float pulse(float speed) {
        return 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 1000.0 * speed * Math.PI * 2);
    }

    // ================================================================ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        SkillTree t = tree();
        updateButtons();
        int x0 = wx0(), y0 = wy0(), x1 = wx1(), y1 = wy1();

        // window frame: thin gold line on a black border
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF000000);
        g.enableScissor(x0, y0, x1, y1);
        hovered = null;
        if (t != null) {
            refreshStates(t);
            if (inWindow(mx, my)) hovered = hit(t, worldX(mx), worldY(my));
            drawBackground(g, t);
            g.pose().pushPose();
            g.pose().translate(cx() + panX, cy() + panY, 0);
            g.pose().scale(zoom, zoom, 1f);
            drawWorld(g, t);
            g.pose().popPose();
        } else {
            g.fill(x0, y0, x1, y1, BG);
        }
        g.disableScissor();
        g.renderOutline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, PANEL_LINE);

        // HUD above the item icons (they render at z 150+), so nothing pokes through the search box
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        drawHud(g, t);
        super.render(g, mx, my, pt);
        g.pose().popPose();
        renderTabs(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        boolean overAttrs = drawAttrPanel(g, mx, my);
        g.pose().popPose();
        if (overAttrs) hovered = null;

        if (hovered != null && t != null) g.renderComponentTooltip(font, tooltip(t, hovered), mx, my);
        int tab = tabAt(mx, my);
        if (tab >= 0) {
            SkillTree tt = ClientTrees.trees.get(tabs().get(tab));
            List<Component> tl = new ArrayList<>();
            tl.add(Component.literal(tt.title));
            if (!ClientTrees.treeAllowed(tt)) tl.add(Component.translatable("waifingskilltree.tab.locked").withStyle(ChatFormatting.RED));
            g.renderComponentTooltip(font, tl, mx, my);
        }
    }

    private void drawHud(GuiGraphics g, @Nullable SkillTree t) {
        int x0 = wx0(), x1 = wx1(), y0 = wy0();
        // bottom bar
        g.fill(x0, barY(), x1, wy1(), PANEL);
        g.fill(x0, barY(), x1, barY() + 1, PANEL_LINE);
        if (t == null) {
            g.drawCenteredString(font, Component.translatable("waifingskilltree.screen.empty").getString(), cx(), cy(), 0xFFAAAAAA);
            return;
        }
        // search result counter next to the box
        if (!searchQ.isEmpty()) {
            int n = hits(t).size();
            String s = n == 0 ? Component.translatable("waifingskilltree.search.none").getString()
                    : (hitCursor >= 0 ? (hitCursor + 1) + "/" : "") + n;
            g.drawString(font, s, search.getX() + search.getWidth() + 6, barY() + 10, n == 0 ? 0xFFFF7070 : HIT, true);
        }
        // points panel in the bar: gold gem + "3 points", glued to the buy button
        {
            int pr = ptsRight, pl = pr - ptsWidth(), py = barY() + (BAR_H - 20) / 2;
            boolean any = ClientTrees.points > 0;
            int gem = any ? GOLD : 0xFF6A6F78;
            g.fill(pl, py, pr, py + 20, 0xFF0E121B);
            g.renderOutline(pl, py, pr - pl + (buyBtn != null ? 1 : 0), 20, PANEL_LINE);
            int gx = pl + 9, gy = py + 10;                      // small diamond gem, row by row
            for (int i = -4; i <= 4; i++) {
                int half = 4 - Math.abs(i);
                g.fill(gx - half, gy + i, gx + half + 1, gy + i + 1, i < 0 ? lerp(gem, 0xFFFFFFFF, 0.35f) : gem);
            }
            g.drawString(font, ptsText(), pl + 16, py + 6, any ? GOLD_TXT : 0xFF8C93A6, true);
        }
        // tree name: only useful with several trees (centred banner above the tree)
        String title = t.title == null ? "" : t.title;
        if (tabs().size() > 1 && !title.isEmpty()) {
            int w = font.width(title);
            g.fill(cx() - w / 2 - 10, y0, cx() + w / 2 + 10, y0 + 16, PANEL);
            g.fill(cx() - w / 2 - 10, y0 + 16, cx() + w / 2 + 10, y0 + 17, PANEL_LINE);
            g.drawCenteredString(font, title, cx(), y0 + 4, GOLD_TXT);
            y0 += 20;
        }

        if (!ClientTrees.treeAllowed(t)) {   // closed tree, or exclusive tree locked by the player's choice
            SkillTree prim = ClientTrees.trees.get(ClientTrees.primary.get(t.exclusiveGroup));
            boolean closed = t.requiresUnlock && !TreeLocks.opened(t, TreeLocks.unlocked(ClientTrees.trees, ClientTrees.taken));
            String msg = closed ? Component.translatable("waifingskilltree.screen.tree_closed").getString()
                    : Component.translatable("waifingskilltree.screen.tree_locked", prim == null ? "?" : prim.title).getString();
            g.fill(cx() - font.width(msg) / 2 - 8, y0 + 24, cx() + font.width(msg) / 2 + 8, y0 + 40, PANEL);
            g.drawCenteredString(font, msg, cx(), y0 + 28, 0xFFFF6060);
        } else if (!anyTaken(t)) {           // first visit: tell the player where to begin
            String msg = Component.translatable("waifingskilltree.screen.begin").getString();
            g.fill(cx() - font.width(msg) / 2 - 8, y0 + 24, cx() + font.width(msg) / 2 + 8, y0 + 40, PANEL);
            g.drawCenteredString(font, msg, cx(), y0 + 28, GOLD_TXT);
        }
    }

    private void drawBackground(GuiGraphics g, SkillTree t) {
        int x0 = wx0(), y0 = wy0(), x1 = wx1(), y1 = wy1();
        ResourceLocation tex = t.background == null || t.background.isEmpty() ? null : ResourceLocation.tryParse(t.background);
        if (tex != null && !tex.equals(DEFAULT_BG)) {      // a custom background picked by the tree author: tiled and darkened
            int ox = Math.floorMod((int) panX, 16), oy = Math.floorMod((int) panY, 16);
            for (int x = x0 - 16 + ox; x < x1; x += 16)
                for (int y = y0 - 16 + oy; y < y1; y += 16) g.blit(tex, x, y, 0, 0, 16, 16, 16, 16);
            g.fill(x0, y0, x1, y1, 0xB0050710);
            return;
        }
        // default: deep night blue with a vignette towards the edges
        g.fillGradient(x0, y0, x1, (y0 + y1) / 2, BG_EDGE, BG);
        g.fillGradient(x0, (y0 + y1) / 2, x1, y1, BG, BG_EDGE);
    }

    /** Everything in tree coordinates: regions, grid, links, frames, icons and badges. */
    private void drawWorld(GuiGraphics g, SkillTree t) {
        float margin = 90;
        float left = worldX(wx0()) - margin, right = worldX(wx1()) + margin;
        float top = worldY(wy0()) - margin, bottom = worldY(wy1()) + margin;
        List<SkillNode> visible = new ArrayList<>();
        for (SkillNode n : t.nodes.values()) if (n.x >= left && n.x <= right && n.y >= top && n.y <= bottom) visible.add(n);
        boolean searching = !searchQ.isEmpty();
        Set<String> found = searching ? hits(t) : Set.of();

        // ---- pass 1: coloured regions, grid and links
        begin(g.pose().last().pose());
        for (SkillNode n : visible) {     // soft colour cloud behind every node: classes read as coloured regions
            State st = state(t, n);
            int c = colour(n);
            float a = st == State.TAKEN ? 0.16f : 0.09f;
            disc(n.x, n.y, st == State.TAKEN ? 70 : 58, withAlpha(c, a), withAlpha(c, 0), 0, 0);
        }
        drawGrid(left + margin, right - margin, top + margin, bottom - margin);
        drawLinks(t, left, right, top, bottom);
        end();

        // ---- pass 2: node frames
        begin(g.pose().last().pose());
        for (SkillNode n : visible) drawNodeShape(t, n, searching, found.contains(n.id));
        end();

        // ---- pass 3: item icons
        for (SkillNode n : visible) {
            float s = keystone(n) ? 1.3f : switch (n.shape) {
                case SkillNode.LARGE -> 1.25f;
                case SkillNode.MEDIUM -> 1.0f;
                default -> 0.78f;
            };
            g.pose().pushPose();
            g.pose().translate(n.x, n.y, 0);
            g.pose().scale(s, s, 1f);
            g.renderFakeItem(icon(n.icon), -8, -8);
            g.pose().popPose();
        }

        // ---- pass 4: veil over locked / filtered icons, badges (above the icons)
        g.pose().pushPose();
        g.pose().translate(0, 0, 260);
        begin(g.pose().last().pose());
        boolean fresh = !anyTaken(t);
        for (SkillNode n : visible) {
            State st = state(t, n);
            boolean dim = searching && !found.contains(n.id);
            float r = n.radius();
            ox = n.x; oy = n.y;
            float core = SkillNode.SMALL.equals(n.shape) && !keystone(n) ? r - 1.5f : SkillNode.MEDIUM.equals(n.shape) ? r - 3 : r - 4;
            if (st == State.LOCKED || dim) {
                if (!SkillNode.CIRCLE.equals(n.form)) shape(n.form, r * 1.22f - (SkillNode.SMALL.equals(n.shape) ? 2.2f : 4.2f), null, 1, argb(dim ? 0xB0 : 0x80, 0x05070C));
                else disc(0, 0, core, argb(dim ? 0xB0 : 0x80, 0x05070C));
            }
            if (n.isStart()) {
                float fr = frameR(n);
                star(-fr * 0.72f, -fr * 0.72f, Math.max(5, r * 0.38f), 0xFFFFD34A, 0xFF3A2C00);
                if (fresh && st == State.AVAILABLE) {           // "start here": a breathing golden halo
                    float p = pulse(0.6f);
                    ring(0, 0, fr + 5 + p * 4, fr + 7 + p * 4, withAlpha(GOLD, 0.9f - p * 0.5f), null, 1);
                }
            }
            if (n.maxLevel() > 1) {
                int cur = ClientTrees.level(t.id, n.id);
                String lv = cur + "/" + n.maxLevel();
                float fr = frameR(n), w = font.width(lv) * 0.6f + 5, bx = fr * 0.62f, by = fr * 0.62f;
                pill(bx - w / 2 - 0.6f, by - 4.6f, bx + w / 2 + 0.6f, by + 4.6f, cur >= n.maxLevel() ? 0xFFF2C14E : 0xFF7FE3A1);
                pill(bx - w / 2, by - 4, bx + w / 2, by + 4, 0xF00D1A12);
            }
        }
        ox = oy = 0;
        for (float[] bdg : levelBadges) {
            String s = "\u2265" + (int) bdg[2];
            float w = font.width(s) * 0.6f + 6;
            pill(bdg[0] - w / 2 - 0.7f, bdg[1] - 5.2f, bdg[0] + w / 2 + 0.7f, bdg[1] + 5.2f, bdg[3] > 0 ? 0xFF7FE3A1 : CHOICE_COL);
            pill(bdg[0] - w / 2, bdg[1] - 4.5f, bdg[0] + w / 2, bdg[1] + 4.5f, 0xF0141008);
        }
        end();
        g.pose().popPose();

        // ---- pass 5: text (levels, AND counters, names of notables when zoomed in)
        g.pose().pushPose();
        g.pose().translate(0, 0, 280);
        for (float[] bdg : levelBadges) small(g, "\u2265" + (int) bdg[2], bdg[0], bdg[1] - 2.6f, 0.6f, bdg[3] > 0 ? 0xFF9FF0B8 : 0xFFFFD08A);
        for (SkillNode n : visible) {
            float fr = frameR(n);
            if (n.maxLevel() > 1) {
                int cur = ClientTrees.level(t.id, n.id);
                String lv = cur + "/" + n.maxLevel();
                small(g, lv, n.x + fr * 0.62f, n.y + fr * 0.62f - 2.4f, 0.6f, cur >= n.maxLevel() ? GOLD_TXT : 0xFF9FF0B8);
            }
            if (SkillNode.AND.equals(n.kind)) small(g, "&" + (n.require <= 0 ? t.incomingLinks(n.id).size() : n.require), n.x + n.radius() * 0.55f, n.y + n.radius() * 0.35f, 0.75f, 0xFFFFE97A);
            boolean notable = keystone(n) || !SkillNode.SMALL.equals(n.shape);
            if (zoom >= 1.15f && notable && n.title != null && !n.title.isEmpty()) {
                small(g, n.title, n.x, n.y + fr + 4, 0.55f, state(t, n) == State.LOCKED ? 0xFF8C93A6 : 0xFFE8DDB8);
            }
        }
        g.pose().popPose();
    }

    /** Centred text scaled by {@code s} at tree position (x, y). */
    private void small(GuiGraphics g, String s, float x, float y, float sc, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, s, -font.width(s) / 2, 0, col, true);
        g.pose().popPose();
    }

    private void drawGrid(float l, float r, float t, float b) {
        float step = zoom < 0.45f ? 200 : 50, th = 1f / zoom;
        int c = 0x0EFFFFFF;
        for (float x = (float) Math.floor(l / step) * step; x <= r; x += step) line(x, t, x, b, th, c, c);
        for (float y = (float) Math.floor(t / step) * step; y <= b; y += step) line(l, y, r, y, th, c, c);
    }

    // ---------------------------------------------------------------- links

    /** Links with a minimum level, collected while drawing the lines: their "5" badge is drawn above the icons. */
    private final List<float[]> levelBadges = new ArrayList<>();

    private void drawLinks(SkillTree t, float l, float r, float top, float bot) {
        boolean fresh = !anyTaken(t);
        levelBadges.clear();
        float glow = pulse(0.7f);
        // two passes: faint lines first, then the bright ones on top so the path is never hidden
        for (int pass = 0; pass < 2; pass++) {
            for (SkillTree.Link link : t.links.values()) {
                SkillNode a = t.nodes.get(link.from), b = t.nodes.get(link.to);
                if (a == null || b == null) continue;
                if (Math.max(a.x, b.x) < l || Math.min(a.x, b.x) > r || Math.max(a.y, b.y) < top || Math.min(a.y, b.y) > bot) continue;
                State sa = state(t, a), sb = state(t, b);
                boolean ta = sa == State.TAKEN, tb = sb == State.TAKEN;
                boolean hover = hovered != null && (hovered == a || hovered == b);
                float len = (float) Math.hypot(b.x - a.x, b.y - a.y);
                boolean portal = len >= PORTAL_LEN, bridge = !portal && len >= BRIDGE_LEN;   // long links look like a jump
                boolean bright = (ta && tb) || hover || ((ta || tb) && (sa == State.AVAILABLE || sb == State.AVAILABLE))
                        || (fresh && (a.isStart() || b.isStart()) && sa != State.LOCKED && sb != State.LOCKED);
                if ((pass == 1) != bright) continue;

                int c; float th; boolean dashed = false, halo = false;
                if (portal || bridge) {           // thick band with marching dashes: violet portal (500+), silver bridge (250-500)
                    if (portal && !(ta || tb || hover) && !allLines) continue;   // portals stay hidden until you reach one end or hover it
                    boolean closed = SkillTree.CHOICE.equals(link.type) && !tb && SkillRules.chosenSibling(t, b, id -> ClientTrees.level(t.id, id)) != null;
                    float wa = closed ? 0.12f : ta && tb ? 0.95f : hover ? 0.9f : (ta || tb) ? 0.55f + 0.3f * glow : bright ? 0.7f : portal ? 0.4f : 0.3f;
                    int col = portal ? WORM : BRIDGE_COL;
                    wormhole(a.x, a.y, b.x, b.y, link.curve, withAlpha(col, wa), !closed && (ta || tb || hover), link.directed(), portal ? 0xFF2A1450 : 0xFF1E2636);
                    if (link.directed() && wa > 0.15f) {   // arrows in the band's own colour, near both ends (violet portal / white jump)
                        int ac = withAlpha(portal ? 0xFFD2B4FF : 0xFFFFFFFF, Math.max(wa, 0.55f));
                        typeArrow(link, a, b, ac, 7f, 0.15f);
                        typeArrow(link, a, b, ac, 7f, 0.85f);
                    }
                    if (link.minLevel > 0) {
                        float[] p = curvePoint(a.x, a.y, b.x, b.y, link.curve, 0.3f);
                        levelBadges.add(new float[]{p[0], p[1], link.minLevel, ClientTrees.level(t.id, link.from) >= link.minLevel ? 1 : 0});
                    }
                    continue;
                } else if (SkillTree.CHOICE.equals(link.type)) {   // cyan double arrows: "pick one of these"
                    boolean closed = !tb && SkillRules.chosenSibling(t, b, id -> ClientTrees.level(t.id, id)) != null;
                    if (closed) { c = withAlpha(CHOICE_COL, hover ? 0.35f : 0.1f); th = 1.6f; dashed = true; }
                    else if (ta && tb) { c = CHOICE_COL; th = 3.4f; halo = true; }
                    else if (bright) { c = lerp(0x9955C8EE, 0xFFC8F2FF, glow); th = 2.6f; if (hover) c = 0xEEC8F2FF; }
                    else if (ta || tb) { c = 0x772A8AA8; th = 2f; }
                    else { c = withAlpha(CHOICE_COL, allLines ? 0.45f : 0.18f); th = allLines ? 2f : 1.8f; }
                } else if (ta && tb) {
                    c = GOLD; th = 3.4f; halo = true;
                } else if (bright) {              // frontier: what the player can buy next, breathing
                    c = lerp(0x99E8DDB8, 0xFFFFF4D0, glow); th = 2.4f;
                    if (hover) c = 0xEEFFFFFF;
                } else if (ta || tb) {            // leads from the path to something still locked
                    c = 0x66B89A50; th = 2f;
                } else {                          // unrelated: almost transparent
                    c = withAlpha(PATH_DIM, allLines ? 0.4f : 0.11f); th = allLines ? 2f : 1.6f;
                }
                curve(a.x, a.y, b.x, b.y, link.curve, th, c, dashed, halo);
                if (link.directed() && alpha(c) > 0x18) {   // one way: amber arrow; choice: double cyan arrow
                    int ac = SkillTree.CHOICE.equals(link.type) ? c : lerp(c, ONEWAY_COL, 0.6f);
                    typeArrow(link, a, b, withAlpha(ac, Math.max(alpha(c), 0x50) / 255f), bright ? 6.5f : 5f);
                }
                if (link.minLevel > 0 && (bright || ta || tb || allLines || hover)) {   // "needs level N of the origin" badge
                    float[] p = curvePoint(a.x, a.y, b.x, b.y, link.curve, 0.3f);
                    levelBadges.add(new float[]{p[0], p[1], link.minLevel, ClientTrees.level(t.id, link.from) >= link.minLevel ? 1 : 0});
                }
            }
        }
    }

    /** Arrow in the middle of a directed link: one head for one way, two heads (->>) for a choice. */
    private static void typeArrow(SkillTree.Link link, SkillNode a, SkillNode b, int c, float size) {
        typeArrow(link, a, b, c, size, 0.5f);
    }

    /** Same, at position {@code at} (0..1) along the link. */
    private static void typeArrow(SkillTree.Link link, SkillNode a, SkillNode b, int c, float size, float at) {
        float[] mid = curvePoint(a.x, a.y, b.x, b.y, link.curve, at), dir = curveDir(a.x, a.y, b.x, b.y, link.curve, at);
        arrow(mid[0], mid[1], dir[0], dir[1], size, c);
        if (SkillTree.CHOICE.equals(link.type)) {
            float dl = (float) Math.hypot(dir[0], dir[1]);
            if (dl > 0.001f) arrow(mid[0] - dir[0] / dl * size * 1.3f, mid[1] - dir[1] / dl * size * 1.3f, dir[0], dir[1], size, c);
        }
    }

    /** Quadratic curve (same maths as the editor): control point = middle + left normal * curve * length. */
    private static void curve(float x1, float y1, float x2, float y2, float curve, float th, int c, boolean dashed, boolean halo) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        int seg = curve == 0f && !dashed ? 1 : Math.max(dashed ? 4 : 6, Math.min(64, (int) (len * (1 + Math.abs(curve)) / (dashed ? 7f : 12f))));
        float[] p = {x1, y1};
        for (int i = 1; i <= seg; i++) {
            float[] q = curvePoint(x1, y1, x2, y2, curve, i / (float) seg);
            if (!dashed || i % 2 == 1) {
                if (halo) glowLine(p[0], p[1], q[0], q[1], th, c, withAlpha(c, 0.28f));
                else line(p[0], p[1], q[0], q[1], th, c, c);
            }
            p = q;
        }
    }

    /** Wormhole link: dark violet bed, bright dashes that march from one end to the other, soft halo when active. */
    private static void wormhole(float x1, float y1, float x2, float y2, float curve, int c, boolean active, boolean march, int bedCol) {
        float a = alpha(c) / 255f;
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        int seg = Math.max(8, Math.min(160, (int) (len * (1 + Math.abs(curve)) / 3f)));
        float dash = 9f, gap = 6f, period = dash + gap;
        float offset = march ? (float) (Util.getMillis() / 1000.0 * 14.0 % period) : 0f;   // directed: 14 units per second towards the end
        float dist = 0;
        float[] p = {x1, y1};
        int bed = withAlpha(bedCol, a * 0.85f), halo = withAlpha(c, a * 0.22f);
        for (int i = 1; i <= seg; i++) {
            float[] q = curvePoint(x1, y1, x2, y2, curve, i / (float) seg);
            float sl = (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
            if (active) glowLine(p[0], p[1], q[0], q[1], 6.5f, bed, halo);
            else line(p[0], p[1], q[0], q[1], 6.5f, bed, bed);
            float phase = ((dist - offset) % period + period) % period;
            if (phase < dash) line(p[0], p[1], q[0], q[1], 3.6f, c, c);
            dist += sl;
            p = q;
        }
    }

    private static float[] curvePoint(float x1, float y1, float x2, float y2, float curve, float t) {
        float dx = x2 - x1, dy = y2 - y1;
        float cx = (x1 + x2) / 2f - dy * curve, cy = (y1 + y2) / 2f + dx * curve, u = 1 - t;
        return new float[]{u * u * x1 + 2 * u * t * cx + t * t * x2, u * u * y1 + 2 * u * t * cy + t * t * y2};
    }

    private static float[] curveDir(float x1, float y1, float x2, float y2, float curve, float t) {
        float dx = x2 - x1, dy = y2 - y1;
        float cx = (x1 + x2) / 2f - dy * curve, cy = (y1 + y2) / 2f + dx * curve;
        return new float[]{2 * (1 - t) * (cx - x1) + 2 * t * (x2 - cx), 2 * (1 - t) * (cy - y1) + 2 * t * (y2 - cy)};
    }

    // ---------------------------------------------------------------- nodes

    private static int[] stops(int[] m, float desat, float bright) {
        return new int[]{dull(m[0], desat, bright), dull(m[1], desat, bright), dull(m[2], desat, bright)};
    }

    /** Frame and coloured core of one node, in the editor's style. */
    private void drawNodeShape(SkillTree t, SkillNode n, boolean searching, boolean found) {
        State st = state(t, n);
        float r = n.radius(), fr = frameR(n);
        boolean key = keystone(n), small = SkillNode.SMALL.equals(n.shape) && !key, medium = SkillNode.MEDIUM.equals(n.shape) && !key;
        float desat = st == State.LOCKED ? 0.35f : 0f;
        float bright = (st == State.LOCKED ? 0.6f : st == State.AVAILABLE ? 0.9f : 1f) * (searching && !found ? 0.4f : 1f);
        int col = colour(n);
        int ct = key ? 0 : conn.getOrDefault(n.id, 0);
        // frame metal follows the special links of the node: amber = one way, cyan = choice, violet = portal (split when two)
        int[] bigBase = key ? BRONZE_M : (ct & 1) != 0 ? AMBER_BIG : (ct & 4) != 0 ? ONEWAY_BIG : (ct & 2) != 0 ? WORM_BIG : GOLD_M;
        int[] smallBase = (ct & 1) != 0 ? AMBER_SMALL : (ct & 4) != 0 ? ONEWAY_SMALL : (ct & 2) != 0 ? WORM_SMALL : SILVER_M;
        int[] big = stops(bigBase, desat, bright), sml = stops(smallBase, desat, bright);
        int dark = key ? 0xFF3D1608 : 0xFF3A2A0C;
        float p = pulse(0.8f);

        ox = n.x; oy = n.y;
        // under-glow: taken nodes shine in their colour; available ones get a soft white aura
        if (st == State.TAKEN) disc(0, 0, fr + 10, withAlpha(col, 0.55f), withAlpha(col, 0), 0, 0);
        else if (st == State.AVAILABLE && !(searching && !found)) disc(0, 0, fr + 7, withAlpha(0xFFFFFFFF, 0.10f + 0.12f * p), 0x00FFFFFF, 0, 0);

        split = (ct & 2) != 0 && (ct & 5) != 0 ? stops(small ? WORM_SMALL : WORM_BIG, desat, bright) : null;   // portal + one way / choice
        float strength = st == State.TAKEN ? 0.9f : st == State.AVAILABLE ? 0.55f : 0.38f;
        if (!SkillNode.CIRCLE.equals(n.form)) {      // square, diamond or star outline: same metals as the round frames
            String f = n.form;
            float s = r * 1.22f;
            int[] m = small ? sml : big;
            int edge = mul(dark, bright);
            if (key) spikes(12, s * 0.75f, s + 9, 0.13, -Math.PI / 2 + Math.PI / 12, big, s + 8);
            else if (SkillNode.LARGE.equals(n.shape)) spikes(4, s * 0.7f, s + 9, 0.22, Math.PI / 4, big, s + 8);
            shape(f, s + 1.8f, null, s, edge);
            shape(f, s + 0.8f, m, s, 0);
            float in = small ? 2.2f : 4.2f;
            shape(f, s - in + 0.8f, null, s, edge);
            shape(f, s - in, null, s, 0xFF15161A);
            shape(f, s - in, null, s, withAlpha(dull(col, desat, bright), strength));
        } else if (small) {
            core(r, col, strength, desat, bright);
            ring(0, 0, r - 1.2f, r + 1.2f, 0, sml, r);
            ring(0, 0, r - 2.2f, r - 1.2f, 0x8C000000, null, r);
        } else if (medium) {
            core(r - 2, col, strength, desat, bright);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + i * Math.PI / 2;
                float x = (float) Math.cos(a) * (r + 1), y = (float) Math.sin(a) * (r + 1);
                diamond(x, y, 5.3f, a + Math.PI / 2, null, r, mul(dark, bright));
                diamond(x, y, 4.5f, a + Math.PI / 2, big, r, 0);
            }
            ring(0, 0, r + 1, r + 1.6f, mul(dark, bright), null, r);
            ring(0, 0, r - 3, r + 1, 0, big, r);
            ring(0, 0, r - 3.7f, r - 3, mul(dark, bright), null, r);
        } else {                                       // large and keystone
            if (key) {
                spikes(12, r + 1, r + 10, 0.13, -Math.PI / 2 + Math.PI / 12, big, r + 8);
                ring(0, 0, r + 5.3f, r + 6.7f, 0, big, r + 8);
            } else {
                spikes(8, r + 1, r + 8, 0.16, Math.PI / 8, big, r + 8);
            }
            core(r - 3, col, strength, desat, bright);
            ring(0, 0, r + 1, r + 1.6f, mul(dark, bright), null, r);
            ring(0, 0, r - 4, r + 1, 0, big, r);
            ring(0, 0, r - 4.6f, r - 4, mul(dark, bright), null, r);
            for (int i = 0; i < 4; i++) {               // crowns at the cardinal points
                double a = -Math.PI / 2 + i * Math.PI / 2;
                float x = (float) Math.cos(a) * (r + 1), y = (float) Math.sin(a) * (r + 1), s = key ? 8 : 7;
                diamond(x, y, s + 0.9f, a + Math.PI / 2, null, r, mul(dark, bright));
                diamond(x, y, s, a + Math.PI / 2, big, r, 0);
                diamond(x, y, key ? 3.6f : 2.6f, a + Math.PI / 2, null, r, key ? dull(col, desat, bright) : dull(0xFFFFF6D8, desat, bright));
            }
        }
        split = null;

        if ((conn.getOrDefault(n.id, 0) & 2) != 0) {   // turning violet dashes around nodes with a portal (a link of PORTAL_LEN+)
            float ph = (float) (Util.getMillis() / 4000.0 % 1.0 * Math.PI * 2);
            dashedRing(0, 0, fr + 3.5f, fr + 5f, withAlpha(0xFFA070FF, st == State.LOCKED ? 0.45f : 0.9f), 10, 0.55f, ph);
        }
        if (st == State.AVAILABLE && !(searching && !found)) {   // thin breathing ring: "you can buy this"
            ring(0, 0, fr + 2.2f, fr + 3.4f, withAlpha(0xFFFFF4D0, 0.35f + 0.45f * p), null, 1);
        }
        if (found) {
            float q = pulse(1.2f);
            ring(0, 0, fr + 6, fr + 9.5f, HIT, null, 1);
            ring(0, 0, fr + 9.5f, fr + 13 + q * 3, withAlpha(HIT, 0.35f), null, 1);
        }
        if (hovered == n) ring(0, 0, fr + 3.5f, fr + 5.5f, 0xDDFFFFFF, null, 1);
        ox = oy = 0;
    }

    /** Coloured core: dark base, tinted fill and a soft highlight (upper left) fading to shadow. */
    private static void core(float r, int col, float strength, float desat, float bright) {
        disc(0, 0, r, 0xFF15161A);
        disc(0, 0, r, withAlpha(dull(col, desat, bright), strength));
        disc(0, 0, r, 0x48FFFFFF, 0x70000000, -r * 0.3f, -r * 0.35f);
    }

    // ================================================================ tooltip
    private List<Component> tooltip(SkillTree t, SkillNode n) {
        List<Component> tip = new ArrayList<>();   // shown always: what the node gives
        List<Component> req = new ArrayList<>();   // shown with Shift: what the node needs
        java.util.function.ToIntFunction<String> lv = id -> ClientTrees.level(t.id, id);
        ChatFormatting color = switch (n.shape) {
            case SkillNode.MEDIUM -> ChatFormatting.GOLD;
            case SkillNode.LARGE -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
        int cur = ClientTrees.level(t.id, n.id);
        boolean maxed = cur >= n.maxLevel();
        tip.add(Component.literal(n.title).withStyle(color));
        for (String d : n.description) tip.add(Component.literal(d).withStyle(ChatFormatting.GRAY));
        if (n.isStart()) tip.add(Component.translatable("waifingskilltree.node.start").withStyle(ChatFormatting.GOLD));
        // ------------------------------------------------ rewards
        if (!n.attributes.isEmpty()) {
            tip.add(Component.empty());
            for (AttrMod a : n.attributes) {
                tip.add(attrLine(a, n.maxLevel() > 1));
                if (a.condition() != null) tip.add(condLine(a));   // the condition on its own line, under the bonus
            }
        }
        if (!n.recipes.isEmpty()) tip.add(Component.translatable("waifingskilltree.node.recipes", n.recipes.size()).withStyle(ChatFormatting.AQUA));
        for (String c : n.unlockCommands) tip.add(Component.translatable("waifingskilltree.node.command", "/" + c).withStyle(ChatFormatting.AQUA));
        if (!n.clickRecipes.isEmpty()) tip.add(Component.translatable("waifingskilltree.node.click_recipes", n.clickRecipes.size()).withStyle(ChatFormatting.AQUA));
        for (String u : n.unlockTrees) {
            SkillTree ut = u.startsWith("#") ? null : ClientTrees.trees.get(u);
            tip.add(Component.translatable("waifingskilltree.node.unlock_tree", ut != null ? ut.title : u).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        n.groupBonus.forEach((g, b) -> {
            if (g.startsWith("choice:")) {   // one more pick at a choice ("+1 choice at Mage")
                SkillNode o = t.nodes.get(g.substring(7));
                tip.add(Component.translatable("waifingskilltree.node.choice_bonus", b, o != null ? o.title : g.substring(7)).withStyle(ChatFormatting.BLUE));
            } else if (g.startsWith("choices:")) {   // extra pick shared by a choice group ("+1 extra pick: Class Paths")
                tip.add(Component.translatable("waifingskilltree.node.choice_pool_bonus", b, groupName(g.substring(8))).withStyle(ChatFormatting.BLUE));
            } else tip.add(Component.translatable("waifingskilltree.node.group_bonus", b, groupName(g)).withStyle(ChatFormatting.BLUE));
        });
        if (n.maxLevel() > 1) tip.add(Component.translatable("waifingskilltree.node.level", cur, n.maxLevel()).withStyle(ChatFormatting.YELLOW));

        // ------------------------------------------------ requirements (Shift)
        if (SkillNode.AND.equals(n.kind)) req(req, n.require <= 0 ? Component.translatable("waifingskilltree.node.and_all") : Component.translatable("waifingskilltree.node.and", n.require), null);
        if ((conn.getOrDefault(n.id, 0) & 2) != 0) req(req, Component.translatable("waifingskilltree.node.wormhole"), null);
        if (!n.advancements.isEmpty()) {
            boolean met = cur > 0 || !ClientTrees.isGated(t.id, n.id);
            for (String a : n.advancements) req(req, Component.translatable("waifingskilltree.node.req_advancement", advancementName(a)), met);
        }
        if (!n.requiresNodes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String id : n.requiresNodes) {
                SkillNode r = t.nodes.get(id);
                if (sb.length() > 0) sb.append(", ");
                sb.append(r != null ? r.title : id).append(ClientTrees.level(t.id, id) > 0 ? " ✔" : " ✘");
            }
            req(req, Component.translatable("waifingskilltree.node.requires_nodes", n.requiredNodeCount(), sb.toString()), SkillRules.requiredNodesMet(n, lv));
        }
        if (n.capLevel > 0 && !n.capNodes.isEmpty() && n.capLevel < n.maxLevel()) {   // "Levels 6-10: need 2 of: Warlord, Archmage..."
            StringBuilder sb = new StringBuilder();
            for (String id : n.capNodes) {
                SkillNode r = t.nodes.get(id);
                if (sb.length() > 0) sb.append(", ");
                sb.append(r != null ? r.title : id).append(ClientTrees.level(t.id, id) > 0 ? " ✔" : "");
            }
            req(req, Component.translatable("waifingskilltree.node.level_cap", n.capLevel + 1, n.maxLevel(), n.capNodeCount(), sb.toString()),
                    SkillRules.levelAllowed(n, n.capLevel + 1, lv));
        }
        if (cur == 0) {
            for (SkillNode.AttrReq r : n.attrReqs) {
                var rl = ResourceLocation.tryParse(r.attribute());
                var attr = rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
                double have = attr != null && minecraft.player.getAttribute(attr) != null ? minecraft.player.getAttributeValue(attr) : 0;
                Component name = attr == null ? Component.literal(r.attribute()) : Component.translatable(attr.getDescriptionId());
                String fmt = (r.amount() == Math.rint(r.amount()) ? String.valueOf((long) r.amount()) : String.valueOf(r.amount()));
                String haveFmt = (have == Math.rint(have) ? String.valueOf((long) have) : trim(have));
                req(req, Component.translatable("waifingskilltree.node.attr_req", fmt, name, haveFmt),
                        have + 1.0E-6 >= r.amount() || minecraft.player.getAbilities().instabuild);
            }
            for (SkillNode.ItemReq r : n.items) {
                var rl = ResourceLocation.tryParse(r.item());
                var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
                int have = 0;
                if (item != null) for (ItemStack is : minecraft.player.getInventory().items) if (is.is(item)) have += is.getCount();
                Component name = item == null ? Component.literal(r.item()) : item.getDescription();
                req(req, Component.translatable("waifingskilltree.node.item", r.count(), name, have),
                        have >= r.count() || minecraft.player.getAbilities().instabuild);
            }
        }
        if (n.prevLevel > 1) req(req, Component.translatable("waifingskilltree.node.prev_level", n.prevLevel), null);
        boolean freeNow = false;
        if (!n.freeChoiceIf.isEmpty()) {   // "Free choice: you have Archmage" / "Free choice if you have: Archmage"
            String f = SkillRules.freeBy(n, lv);
            if (f != null) {
                freeNow = true;
                SkillNode r = t.nodes.get(f);
                req(req, Component.translatable("waifingskilltree.node.free_choice", r != null ? r.title : f), true);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String id : n.freeChoiceIf) { SkillNode r = t.nodes.get(id); if (sb.length() > 0) sb.append(", "); sb.append(r != null ? r.title : id); }
                req(req, Component.translatable("waifingskilltree.node.free_choice_if", sb.toString()), null);
            }
        }
        if (!n.group.isEmpty() && t.groupLimits.containsKey(n.group)) {   // e.g. "Keystone Merchant: 1/2 chosen"
            int used = SkillRules.groupUsed(t, n.group, lv), lim = SkillRules.groupLimit(t, n.group, lv);
            req(req, Component.translatable("waifingskilltree.node.group_limit", groupName(n.group), used, lim), SkillRules.groupAllowed(t, n, lv));
        }
        // choice: "You chose another path: X" / links that need a level of the previous node
        String other = SkillRules.chosenSibling(t, n, lv);
        if (other != null && other.isEmpty()) {   // locked choice: "Locked choice: opens with X, Y"
            StringBuilder sb = new StringBuilder();
            for (SkillTree.Link l : t.incomingLinks(n.id)) {
                if (!SkillTree.CHOICE.equals(l.type)) continue;
                for (SkillNode o : SkillRules.choiceOpeners(t, l.from)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(o.title).append(ClientTrees.level(t.id, o.id) > 0 ? " ✔" : "");
                }
            }
            req(req, sb.length() > 0 ? Component.translatable("waifingskilltree.node.choice_locked_by", sb.toString())
                    : Component.translatable("waifingskilltree.node.choice_locked"), false);
        } else if (other != null) {
            SkillNode o = t.nodes.get(other);
            req(req, Component.translatable("waifingskilltree.node.choice_closed", o != null ? o.title : other), false);
        } else {
            for (SkillTree.Link l : t.incomingLinks(n.id)) {
                if (SkillTree.CHOICE.equals(l.type) && cur == 0 && !freeNow) {
                    String pool = SkillRules.poolNeeded(t, n, lv);
                    if (pool != null) {   // its own pick is spent: this one uses the shared extra pick
                        int left = SkillRules.choicePoolLimit(t, pool, lv) - SkillRules.choicePoolUsed(t, pool, lv);
                        req(req, Component.translatable("waifingskilltree.node.choice_pool", groupName(pool), left), null);
                    } else req(req, Component.translatable("waifingskilltree.node.choice_open"), null);
                }
                if (l.minLevel > 0) {
                    SkillNode from = t.nodes.get(l.other(n.id));
                    req(req, Component.translatable("waifingskilltree.node.link_level", from != null ? from.title : l.other(n.id), l.minLevel),
                            ClientTrees.level(t.id, l.other(n.id)) >= l.minLevel);
                }
            }
        }
        if (!req.isEmpty()) {
            tip.add(Component.empty());
            if (hasShiftDown()) {
                tip.add(Component.translatable("waifingskilltree.node.requirements").withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE));
                tip.addAll(req);
            } else {
                tip.add(Component.translatable("waifingskilltree.tip.shift").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }

        // ------------------------------------------------ state
        tip.add(Component.empty());
        State st = state(t, n);
        if (cur > 0) {
            tip.add(Component.translatable(maxed ? "waifingskilltree.node.maxed" : "waifingskilltree.node.taken").withStyle(ChatFormatting.GREEN));
        }
        if (!maxed) {
            int cost = SkillRules.cost(t, n, lv, cur + 1);
            boolean enough = ClientTrees.points >= cost || minecraft.player.getAbilities().instabuild;
            tip.add(Component.translatable("waifingskilltree.node.cost", cost).withStyle(enough ? ChatFormatting.YELLOW : ChatFormatting.RED));
            if (cur == 0) {
                if (!ClientTrees.treeAllowed(t)) {
                    tip.add(Component.translatable("waifingskilltree.tab.locked").withStyle(ChatFormatting.RED));
                } else if (ClientTrees.isGated(t.id, n.id)) {
                    tip.add(Component.translatable("waifingskilltree.node.advancement").withStyle(ChatFormatting.RED));
                } else {
                    tip.add(Component.translatable(st == State.AVAILABLE ? "waifingskilltree.node.available" : "waifingskilltree.node.locked")
                            .withStyle(st == State.AVAILABLE ? ChatFormatting.GREEN : ChatFormatting.DARK_RED));
                }
            } else {
                tip.add(Component.translatable("waifingskilltree.tip.upgrade").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (cur > 0 && canRefund()) tip.add(Component.translatable("waifingskilltree.tip.refund").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** A requirement line: italic, with a check (met), a cross (not met) or a dot (information). */
    private static void req(List<Component> out, Component text, @Nullable Boolean met) {
        MutableComponent mark = met == null ? Component.literal(" • ").withStyle(ChatFormatting.DARK_AQUA)
                : met ? Component.literal(" ✔ ").withStyle(ChatFormatting.GREEN)
                : Component.literal(" ✘ ").withStyle(ChatFormatting.RED);
        ChatFormatting col = met == null ? ChatFormatting.AQUA : met ? ChatFormatting.GRAY : ChatFormatting.YELLOW;
        out.add(mark.append(text.copy().withStyle(col, ChatFormatting.ITALIC)));
    }

    /** Display name of an advancement: from the client list when known, else the vanilla title key, else the id. */
    private Component advancementName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Component.literal(id);
        var listener = minecraft.getConnection();
        if (listener != null) {
            net.minecraft.advancements.Advancement adv = listener.getAdvancements().getAdvancements().get(rl);
            if (adv != null && adv.getDisplay() != null) return adv.getDisplay().getTitle().copy();
        }
        if ("minecraft".equals(rl.getNamespace())) return Component.translatable("advancements." + rl.getPath().replace('/', '.') + ".title");
        return Component.literal(id);
    }

    private Component attrLine(AttrMod a, boolean perLevel) {
        Attribute attr = a.resolve();
        Component name = attr == null ? Component.literal(a.attribute()) : Component.translatable(attr.getDescriptionId());
        double v = a.amount();
        String num = a.operation() == 0 ? (v >= 0 ? "+" : "") + trim(v) : (v >= 0 ? "+" : "") + pct(v) + "%";
        MutableComponent line = Component.literal(num + " ").append(name).append(perLevel ? Component.translatable("waifingskilltree.node.per_level") : Component.empty());
        if (a.condition() == null) return line.withStyle(v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        return line.withStyle(condActive(a) ? (v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.DARK_GRAY);
    }

    private boolean condActive(AttrMod a) {
        return minecraft != null && minecraft.player != null && a.condition().test(minecraft.player);
    }

    /** "  ↳ with a Shield in the off hand" under a conditional bonus: gold when it applies now, grey when it does not. */
    private Component condLine(AttrMod a) {
        boolean on = condActive(a);
        return Component.literal("   \u21B3 ").withStyle(ChatFormatting.DARK_GRAY)
                .append(a.condition().describe().copy().withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** "blood_magic" -> "Blood Magic". */
    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_\\s]+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Readable name of a group id: "keystone_merchant" -> "Keystone Merchant". */
    private static Component groupName(String g) {
        return Component.literal(pretty(g));
    }

    /** The number as written in the datapack: 2 -> "2", 0.5 -> "0.5", 0.125 -> "0.125". No decimal limit;
     *  only floating point noise is removed (0.1 + 0.2 shows 0.3, not 0.30000000000000004). */
    private static String trim(double d) {
        return new java.math.BigDecimal(Double.toString(d)).setScale(9, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** A fraction shown as a percent, without the noise of multiplying by 100 (0.07 -> "7", not "7.000000000000001"). */
    private static String pct(double d) {
        return trim(new java.math.BigDecimal(Double.toString(d)).movePointRight(2).doubleValue());
    }

    // ================================================================ attribute summary (top-right dropdown)

    /** Totals of one attribute over every taken node of every tree: flat part, percent part, and whether some of it is conditional. */
    private static final class AttrSum {
        final String id;
        double flat, pct;
        boolean conditional;
        AttrSum(String id) { this.id = id; }
    }

    private boolean attrOpen = false;
    private int attrScroll = 0;
    private static final int ATTR_ROW = 11, ATTR_W = 210;

    private List<AttrSum> attrSums() {
        Map<String, AttrSum> m = new TreeMap<>();
        for (var te : ClientTrees.taken.entrySet()) {
            SkillTree t = ClientTrees.trees.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                int lv = ne.getValue();
                if (n == null || lv <= 0) continue;
                for (AttrMod a : n.attributes) {
                    AttrSum sum = m.computeIfAbsent(a.attribute(), AttrSum::new);
                    if (a.operation() == 0) sum.flat += a.amount() * lv; else sum.pct += a.amount() * lv;
                    if (a.condition() != null) sum.conditional = true;
                }
            }
        }
        List<AttrSum> out = new ArrayList<>(m.values());
        out.removeIf(a -> Math.abs(a.flat) < 1.0E-9 && Math.abs(a.pct) < 1.0E-9);
        out.sort(Comparator.comparing(a -> attrName(a.id).getString()));
        return out;
    }

    private static Component attrName(String id) {
        var rl = ResourceLocation.tryParse(id);
        Attribute attr = rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
        return attr == null ? Component.literal(id) : Component.translatable(attr.getDescriptionId());
    }

    private String attrLabel() {
        return "\u03A3 " + Component.translatable("waifingskilltree.attrs.title").getString() + (attrOpen ? " \u25B4" : " \u25BE");
    }

    /** Toggle button of the dropdown: x, y, w, h. */
    private int[] attrButton() {
        int w = font.width(attrLabel()) + 12, h = 14;
        return new int[]{wx1() - w - 4, wy0() + 4, w, h};
    }

    private int attrVisibleRows(int total) {
        int[] b = attrButton();
        int room = (barY() - 8 - (b[1] + b[3] + 2) - 16) / ATTR_ROW;
        return Math.max(1, Math.min(total, room));
    }

    /** Draws the button and, when open, the list. Returns true when the mouse is over it (no node tooltip then). */
    private boolean drawAttrPanel(GuiGraphics g, int mx, int my) {
        int[] b = attrButton();
        boolean overBtn = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], overBtn ? 0xF01A2030 : PANEL);
        g.renderOutline(b[0], b[1], b[2], b[3], PANEL_LINE);
        g.drawString(font, attrLabel(), b[0] + 6, b[1] + 3, GOLD_TXT, true);
        if (!attrOpen) return overBtn;

        List<AttrSum> sums = attrSums();
        int rows = attrVisibleRows(Math.max(1, sums.size()));
        attrScroll = Math.max(0, Math.min(attrScroll, sums.size() - rows));
        int px1 = b[0] + b[2], px0 = px1 - ATTR_W, py0 = b[1] + b[3] + 2, py1 = py0 + rows * ATTR_ROW + 16;
        boolean over = overBtn || mx >= px0 && mx < px1 && my >= py0 && my < py1;
        g.fill(px0, py0, px1, py1, 0xF00C1018);
        g.renderOutline(px0, py0, ATTR_W, py1 - py0, PANEL_LINE);
        if (sums.isEmpty()) {
            g.drawString(font, Component.translatable("waifingskilltree.attrs.none").getString(), px0 + 6, py0 + 4, 0xFF8C93A6, true);
        }
        for (int i = 0; i < rows && attrScroll + i < sums.size(); i++) {
            AttrSum a = sums.get(attrScroll + i);
            boolean off = ClientTrees.disabled.contains(a.id);
            int ry = py0 + 3 + i * ATTR_ROW;
            boolean hov = mx >= px0 && mx < px1 && my >= ry - 1 && my < ry + ATTR_ROW - 1;
            if (hov) g.fill(px0 + 1, ry - 1, px1 - 1, ry + ATTR_ROW - 1, 0x40FFFFFF);
            // check box
            g.renderOutline(px0 + 4, ry, 8, 8, off ? 0xFF6A6F78 : 0xFF7FE3A1);
            if (!off) g.fill(px0 + 6, ry + 2, px0 + 10, ry + 6, 0xFF7FE3A1);
            StringBuilder v = new StringBuilder();
            if (Math.abs(a.flat) > 1.0E-9) v.append(a.flat >= 0 ? "+" : "").append(trim(a.flat));
            if (Math.abs(a.pct) > 1.0E-9) { if (v.length() > 0) v.append(' '); v.append(a.pct >= 0 ? "+" : "").append(pct(a.pct)).append('%'); }
            if (a.conditional) v.append('*');
            String val = v.toString();
            int vw = font.width(val);
            String name = font.plainSubstrByWidth(attrName(a.id).getString(), ATTR_W - 26 - vw);
            int col = off ? 0xFF6A6F78 : 0xFFE8DDB8;
            g.drawString(font, name, px0 + 16, ry, col, false);
            g.drawString(font, val, px1 - 5 - vw, ry, off ? 0xFF6A6F78 : (a.flat < 0 || a.pct < 0 ? 0xFFFF7070 : 0xFF7FE3A1), false);
            if (off) g.fill(px0 + 16, ry + 4, px0 + 16 + font.width(name), ry + 5, 0xFF6A6F78);
        }
        String foot = Component.translatable("waifingskilltree.attrs.hint").getString();
        g.drawString(font, font.plainSubstrByWidth(foot, ATTR_W - 8), px0 + 4, py1 - 11, 0xFF6A7080, false);
        if (sums.size() > rows) {   // scroll bar
            int th = Math.max(6, (py1 - py0 - 16) * rows / sums.size());
            int ty = py0 + 2 + (py1 - py0 - 18 - th) * attrScroll / Math.max(1, sums.size() - rows);
            g.fill(px1 - 2, ty, px1 - 1, ty + th, PANEL_LINE);
        }
        return over;
    }

    /** Click on the dropdown: true when consumed. */
    private boolean attrClick(double mx, double my) {
        int[] b = attrButton();
        if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { attrOpen = !attrOpen; return true; }
        if (!attrOpen) return false;
        List<AttrSum> sums = attrSums();
        int rows = attrVisibleRows(Math.max(1, sums.size()));
        int px1 = b[0] + b[2], px0 = px1 - ATTR_W, py0 = b[1] + b[3] + 2, py1 = py0 + rows * ATTR_ROW + 16;
        if (mx < px0 || mx >= px1 || my < py0 || my >= py1) return false;
        int i = (int) Math.floor((my - py0 - 2) / ATTR_ROW);
        if (i >= 0 && i < rows && attrScroll + i < sums.size()) {
            String id = sums.get(attrScroll + i).id;
            boolean off = !ClientTrees.disabled.contains(id);
            Set<String> nd = new HashSet<>(ClientTrees.disabled);   // instant feedback; the server sync confirms it
            if (off) nd.add(id); else nd.remove(id);
            ClientTrees.disabled = nd;
            Network.sendToServer(new C2SToggleAttribute(id, off));
        }
        return true;
    }

    private boolean attrScrolled(double mx, double my, double delta) {
        if (!attrOpen) return false;
        int[] b = attrButton();
        int px1 = b[0] + b[2], px0 = px1 - ATTR_W;
        if (mx < px0 || mx >= px1 || my < b[1] || my >= barY()) return false;
        List<AttrSum> sums = attrSums();
        int rows = attrVisibleRows(Math.max(1, sums.size()));
        int py1 = b[1] + b[3] + 2 + rows * ATTR_ROW + 16;
        if (my >= py1) return false;
        attrScroll = Math.max(0, Math.min(sums.size() - rows, attrScroll - (int) Math.signum(delta)));
        return true;
    }

    // ================================================================ tabs (only with more than one tree)

    private void renderTabs(GuiGraphics g) {
        List<String> ids = tabs();
        if (ids.size() <= 1) return;
        for (int i = 0; i < ids.size(); i++) {
            SkillTree t = ClientTrees.trees.get(ids.get(i));
            int x = MARGIN + i * (TAB_W + 2), y = MARGIN;
            boolean sel = ids.get(i).equals(currentId);
            g.fill(x, y + (sel ? 0 : 3), x + TAB_W, y + TAB_H, sel ? 0xFF1A2030 : PANEL);
            g.renderOutline(x, y + (sel ? 0 : 3), TAB_W, TAB_H - (sel ? 0 : 3), sel ? PANEL_LINE : 0xFF2A3040);
            g.renderFakeItem(icon(t.icon), x + 6, y + (sel ? 5 : 8));
            if (!ClientTrees.treeAllowed(t)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(x + 1, y + (sel ? 1 : 4), x + TAB_W - 1, y + TAB_H - 1, 0x99000000);
                g.pose().popPose();
            }
        }
    }

    private int tabAt(double mx, double my) {
        List<String> ids = tabs();
        if (ids.size() <= 1 || my < MARGIN || my >= MARGIN + TAB_H) return -1;
        int i = (int) ((mx - MARGIN) / (TAB_W + 2));
        return mx >= MARGIN && i >= 0 && i < ids.size() ? i : -1;
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && search.isFocused() && !search.isMouseOver(mx, my)) {
            search.setFocused(false);
            setFocused(null);
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && attrClick(mx, my)) return true;
        int tab = tabAt(mx, my);
        if (tab >= 0) {
            saveView();
            currentId = tabs().get(tab);
            loadView();
            statesKey = "";
            return true;
        }
        SkillTree t = tree();
        if (t == null || !inWindow(mx, my)) return false;
        SkillNode h = hit(t, worldX(mx), worldY(my));
        panning = false;
        dragged = false;
        if (h != null) {
            if (button == 0) Network.sendToServer(new C2SUnlock(t.id, h.id));
            else if (button == 1 && canRefund()) Network.sendToServer(new C2SRefund(t.id, h.id));
        } else {
            panning = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            panX += dx;
            panY += dy;
            dragged = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (attrScrolled(mx, my, delta)) return true;
        if (!inWindow(mx, my)) return super.mouseScrolled(mx, my, delta);
        float wx = worldX(mx), wy = worldY(my);
        zoom = Math.max(0.25f, Math.min(2.5f, zoom * (float) Math.pow(1.12, delta)));
        panX = mx - cx() - wx * zoom;
        panY = my - cy() - wy * zoom;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search != null && search.isFocused()) {      // typing in the search box: keep the screen open
            if (key == 257 || key == 335) { gotoHit((mods & 1) != 0 ? -1 : 1); return true; }   // Enter / Shift+Enter
            if (key == 256) { search.setFocused(false); setFocused(null); return true; }       // Esc leaves the box
            return super.keyPressed(key, scan, mods);
        }
        if (WaifingSkilltreeClient.OPEN_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        if (search != null && (key == 70 && (mods & 2) != 0 || key == 47)) {   // Ctrl+F or "/" focus the search
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (key == 76) {            // L: toggle lines
            allLines = !allLines;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
