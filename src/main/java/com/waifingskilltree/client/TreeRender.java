package com.waifingskilltree.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Procedural shapes for the tree screen (same look as the HTML editor): filled discs, metal rings with a diagonal
 * gradient, diamonds, spikes and thick lines. Everything goes into one TRIANGLES batch (begin ... end), with culling
 * off so the winding of the triangles never hides them.
 */
final class TreeRender {
    private TreeRender() {}

    private static BufferBuilder bb;
    private static Matrix4f m;
    /** Offset added to every vertex: node shapes are drawn in local coordinates around (ox, oy). */
    static float ox, oy;
    /** Second metal for split frames (one way + wormhole): used on the lower-right half (x + y > 0). */
    static int[] split;

    static void begin(Matrix4f pose) {
        m = pose;
        ox = oy = 0;
        split = null;
        bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    static void end() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        bb = null;
    }

    // ------------------------------------------------------------ colours (ARGB ints)

    static int argb(int a, int rgb) { return (a & 255) << 24 | (rgb & 0xFFFFFF); }

    static int alpha(int c) { return c >>> 24; }

    static int withAlpha(int c, float a) { return argb(Math.round(Math.max(0, Math.min(1, a)) * 255), c); }

    static int mul(int c, float f) {
        int r = Math.min(255, Math.round((c >> 16 & 255) * f)), g = Math.min(255, Math.round((c >> 8 & 255) * f)), b = Math.min(255, Math.round((c & 255) * f));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = alpha(a) + Math.round((alpha(b) - alpha(a)) * t);
        int r = (a >> 16 & 255) + Math.round(((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (a >> 8 & 255) + Math.round(((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (a & 255) + Math.round(((b & 255) - (a & 255)) * t);
        return aa << 24 | r << 16 | g << 8 | bl;
    }

    /** Desaturate towards grey ({@code d} 0..1) and darken ({@code bright}). */
    static int dull(int c, float d, float bright) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        int l = (r * 3 + g * 6 + b) / 10;
        int grey = (c & 0xFF000000) | l << 16 | l << 8 | l;
        return mul(lerp(c, grey, d), bright);
    }

    /** Three-stop metal (light, mid, dark) along the diagonal from upper-left to lower-right, like the editor. */
    static int metal(int[] stops, float x, float y, float r) {
        if (split != null && x + y > 0.01f) stops = split;
        float t = Math.max(0, Math.min(1, ((x + r) + (y + r)) / (4 * r)));
        return t < 0.45f ? lerp(stops[0], stops[1], t / 0.45f) : lerp(stops[1], stops[2], (t - 0.45f) / 0.55f);
    }

    // ------------------------------------------------------------ primitives

    static void v(float x, float y, int c) {
        bb.vertex(m, x + ox, y + oy, 0).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    static void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        v(x1, y1, c1); v(x2, y2, c2); v(x3, y3, c3);
    }

    static int segs(float r) { return Math.max(12, Math.min(48, (int) (r * 1.6f))); }

    /** Disc whose centre colour fades to the rim colour (centre may be offset for a highlight). */
    static void disc(float cx, float cy, float r, int centre, int rim, float offX, float offY) {
        int n = segs(r);
        float px = cx + r, py = cy;
        for (int i = 1; i <= n; i++) {
            double a = i * Math.PI * 2 / n;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            tri(cx + offX, cy + offY, centre, px, py, rim, x, y, rim);
            px = x; py = y;
        }
    }

    static void disc(float cx, float cy, float r, int c) { disc(cx, cy, r, c, c, 0, 0); }

    /** Ring between r0 and r1; metal != null paints the diagonal gradient (scaled by shade), else flat colour. */
    static void ring(float cx, float cy, float r0, float r1, int flat, int[] metal, float gradR) {
        int n = segs(r1);
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ox0 = c0 * r1, oy0 = s0 * r1, ox1 = c1 * r1, oy1 = s1 * r1, ix0 = c0 * r0, iy0 = s0 * r0, ix1 = c1 * r0, iy1 = s1 * r0;
            int k0 = metal == null ? flat : metal(metal, ox0, oy0, gradR), k1 = metal == null ? flat : metal(metal, ox1, oy1, gradR);
            int k2 = metal == null ? flat : metal(metal, ix1, iy1, gradR), k3 = metal == null ? flat : metal(metal, ix0, iy0, gradR);
            tri(cx + ox0, cy + oy0, k0, cx + ox1, cy + oy1, k1, cx + ix1, cy + iy1, k2);
            tri(cx + ox0, cy + oy0, k0, cx + ix1, cy + iy1, k2, cx + ix0, cy + iy0, k3);
        }
    }

    /** Dashed ring: {@code dashes} pieces, each covering {@code fill} (0..1) of its slot, rotated by {@code phase} radians. */
    static void dashedRing(float cx, float cy, float r0, float r1, int c, int dashes, float fill, float phase) {
        for (int i = 0; i < dashes; i++) {
            double a0 = phase + i * Math.PI * 2 / dashes, a1 = a0 + fill * Math.PI * 2 / dashes;
            int steps = 3;
            for (int k = 0; k < steps; k++) {
                double b0 = a0 + (a1 - a0) * k / steps, b1 = a0 + (a1 - a0) * (k + 1) / steps;
                float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
                tri(cx + c0 * r1, cy + s0 * r1, c, cx + c1 * r1, cy + s1 * r1, c, cx + c1 * r0, cy + s1 * r0, c);
                tri(cx + c0 * r1, cy + s0 * r1, c, cx + c1 * r0, cy + s1 * r0, c, cx + c0 * r0, cy + s0 * r0, c);
            }
        }
    }

    /** Diamond at (x,y), long half-axis s along angle a (the editor's crowns). */
    static void diamond(float x, float y, float s, double a, int[] metal, float gradR, int flat) {
        float ux = (float) Math.cos(a - Math.PI / 2), uy = (float) Math.sin(a - Math.PI / 2);   // "up" of the diamond
        float wx = -uy * s * 0.62f, wy = ux * s * 0.62f;
        float tx = x + ux * s, ty = y + uy * s, bx = x - ux * s, by = y - uy * s;
        int ct = metal == null ? flat : metal(metal, tx, ty, gradR), cb = metal == null ? flat : metal(metal, bx, by, gradR);
        int cl = metal == null ? flat : metal(metal, x + wx, y + wy, gradR), cr = metal == null ? flat : metal(metal, x - wx, y - wy, gradR);
        tri(tx, ty, ct, x + wx, y + wy, cl, bx, by, cb);
        tri(tx, ty, ct, bx, by, cb, x - wx, y - wy, cr);
    }

    /** Square diamond (rhombus) centred on (x,y), half-diagonal s; metal gradient or flat colour. */
    static void rhomb(float x, float y, float s, int[] metal, float gradR, int flat) {
        int ct = metal == null ? flat : metal(metal, x, y - s, gradR), cb = metal == null ? flat : metal(metal, x, y + s, gradR);
        int cl = metal == null ? flat : metal(metal, x - s, y, gradR), cr = metal == null ? flat : metal(metal, x + s, y, gradR);
        tri(x, y - s, ct, x + s, y, cr, x, y + s, cb);
        tri(x, y - s, ct, x, y + s, cb, x - s, y, cl);
    }

    /** Node outline other than a circle: "square", "diamond" or "star", centred on (0,0), size s (half-diagonal of the diamond). */
    static void shape(String form, float s, int[] metal, float gradR, int flat) {
        if ("square".equals(form)) {
            float k = s * 0.78f;
            int a = metal == null ? flat : metal(metal, -k, -k, gradR), b = metal == null ? flat : metal(metal, k, -k, gradR);
            int c = metal == null ? flat : metal(metal, k, k, gradR), d = metal == null ? flat : metal(metal, -k, k, gradR);
            tri(-k, -k, a, k, -k, b, k, k, c);
            tri(-k, -k, a, k, k, c, -k, k, d);
        } else if ("star".equals(form)) {
            float[] px = new float[10], py = new float[10];
            for (int i = 0; i < 10; i++) {
                double an = -Math.PI / 2 + i * Math.PI / 5; float r = i % 2 == 0 ? s * 1.12f : s * 0.52f;
                px[i] = (float) Math.cos(an) * r; py[i] = (float) Math.sin(an) * r;
            }
            int cc = metal == null ? flat : metal(metal, 0, 0, gradR);
            for (int i = 0; i < 10; i++) {
                int j = (i + 1) % 10;
                tri(0, 0, cc, px[i], py[i], metal == null ? flat : metal(metal, px[i], py[i], gradR), px[j], py[j], metal == null ? flat : metal(metal, px[j], py[j], gradR));
            }
        } else rhomb(0, 0, s, metal, gradR, flat);
    }

    /** {@code count} triangular spikes between r0 and r1 (half = half width in radians). */
    static void spikes(int count, float r0, float r1, double half, double offset, int[] metal, float gradR) {
        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2 / count;
            float ax = (float) Math.cos(a - half) * r0, ay = (float) Math.sin(a - half) * r0;
            float tx = (float) Math.cos(a) * r1, ty = (float) Math.sin(a) * r1;
            float bx = (float) Math.cos(a + half) * r0, by = (float) Math.sin(a + half) * r0;
            tri(ax, ay, metal(metal, ax, ay, gradR), tx, ty, metal(metal, tx, ty, gradR), bx, by, metal(metal, bx, by, gradR));
        }
    }

    /** Thick straight segment; colour may fade from c1 to c2. */
    static void line(float x1, float y1, float x2, float y2, float th, int c1, int c2) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * th / 2f, ny = dx / len * th / 2f;
        tri(x1 + nx, y1 + ny, c1, x1 - nx, y1 - ny, c1, x2 - nx, y2 - ny, c2);
        tri(x1 + nx, y1 + ny, c1, x2 - nx, y2 - ny, c2, x2 + nx, y2 + ny, c2);
    }

    /** Line with a soft halo: a wide transparent band under a solid core. */
    static void glowLine(float x1, float y1, float x2, float y2, float th, int core, int halo) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float hw = th * 2.2f;
        float nx = -dy / len, ny = dx / len;
        int clear = halo & 0xFFFFFF;
        // halo: from the edge of the core (halo colour) out to hw (transparent), both sides
        for (int s = -1; s <= 1; s += 2) {
            float ix = nx * th / 2 * s, iy = ny * th / 2 * s, ox = nx * hw * s, oy = ny * hw * s;
            tri(x1 + ix, y1 + iy, halo, x2 + ix, y2 + iy, halo, x2 + ox, y2 + oy, clear);
            tri(x1 + ix, y1 + iy, halo, x2 + ox, y2 + oy, clear, x1 + ox, y1 + oy, clear);
        }
        line(x1, y1, x2, y2, th, core, core);
    }

    /** Filled arrow head pointing along (dx,dy) at (x,y). */
    static void arrow(float x, float y, float dx, float dy, float size, int c) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        dx /= len; dy /= len;
        float tipX = x + dx * size, tipY = y + dy * size, bx = x - dx * size * 0.7f, by = y - dy * size * 0.7f;
        float px = -dy * size * 0.85f, py = dx * size * 0.85f;
        float nx = x - dx * size * 0.25f, ny = y - dy * size * 0.25f;
        tri(tipX, tipY, c, bx + px, by + py, c, nx, ny, c);
        tri(tipX, tipY, c, nx, ny, c, bx - px, by - py, c);
    }

    /** Rounded rectangle (pill when h is the full height). */
    static void pill(float x0, float y0, float x1, float y1, int c) {
        float r = (y1 - y0) / 2;
        tri(x0 + r, y0, c, x1 - r, y0, c, x1 - r, y1, c);
        tri(x0 + r, y0, c, x1 - r, y1, c, x0 + r, y1, c);
        halfDisc(x0 + r, y0 + r, r, Math.PI / 2, c);
        halfDisc(x1 - r, y0 + r, r, -Math.PI / 2, c);
    }

    private static void halfDisc(float cx, float cy, float r, double start, int c) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = start + i * Math.PI / n, a1 = start + (i + 1) * Math.PI / n;
            tri(cx, cy, c, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, c, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, c);
        }
    }

    /** Five-pointed star (start badge). */
    static void star(float cx, float cy, float r, int fill, int edge) {
        float[] xs = new float[10], ys = new float[10];
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 1 ? r * 0.45f : r;
            xs[i] = cx + (float) Math.cos(a) * rr;
            ys[i] = cy + (float) Math.sin(a) * rr;
        }
        for (int i = 0; i < 10; i++) {   // dark outline: a slightly bigger star underneath
            int j = (i + 1) % 10;
            float ex = (xs[i] - cx) * 1.25f + cx, ey = (ys[i] - cy) * 1.25f + cy, fx = (xs[j] - cx) * 1.25f + cx, fy = (ys[j] - cy) * 1.25f + cy;
            tri(cx, cy, edge, ex, ey, edge, fx, fy, edge);
        }
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            tri(cx, cy, fill, xs[i], ys[i], fill, xs[j], ys[j], fill);
        }
    }
}
