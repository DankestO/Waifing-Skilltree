/* ============================================================
   View aids (colour modes, shapes, regions, values, minimap),
   curved links and the Arrange tools.
   ============================================================ */

/* ---------- attribute categories (colour mode "Category") ---------- */
const CATS = [
  ['offense', 'Offense', '#e0604f', /attack_damage|attack_speed|crit_|armor_pierce|armor_shred|prot_pierce|prot_shred|projectile|arrow_|fire_damage|cold_damage|backstab|ambush|pet_damage|summon_damage/],
  ['defense', 'Defense', '#4a90e2', /max_health|generic\.armor|armor_toughness|knockback|dodge|healing|overheal|spell_resist|magic_resist|ghost_health/],
  ['magic', 'Magic', '#b06cf0', /spell_power|mana|cooldown|cast_time|casting|mage_/],
  ['gather', 'Gathering & trade', '#e5c33a', /loot|harvest|catch|ore_amount|log_amount|sapling|luck|merchant_|haggle|pickpocket|experience|trophy|mining_speed|bar_size|exp_multiplier|line_strength|farmer_|fisherman_|breed|feed_keep/],
  ['craft', 'Crafting', '#4fc08d', /blacksmith_|alchemist_|enchanter_|chef_/],
  ['move', 'Mobility & stealth', '#9aa3b5', /movement|stealth|explorer_fall|nightkin/],
];
function nodeCategory(n) {
  const a = n.attributes[0]?.attribute || '';
  if (!a) return null;
  for (const c of CATS) if (c[3].test(a)) return c;
  return null;
}
function hashColor(s) {
  let h = 0;
  for (const ch of s) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return `hsl(${Math.abs(h) % 360},62%,58%)`;
}
/** Colour of a node in the current colour mode. */
function nodeColor(n) {
  const mode = $('#colorMode')?.value || 'branch';
  if (mode === 'category') { const c = nodeCategory(n); return c ? c[2] : '#6b6f7a'; }
  if (mode === 'group') return n.group ? hashColor(n.group) : (n.color ? n.color + '66' : '#55596288');
  if (mode === 'kind') return n.start ? '#ffd34a' : S.cur && hasPortal(S.cur, n.id) ? '#a070ff' : n.kind === 'and' ? '#ffe97a' : n.group ? '#ff7a59' : n.max_level > 1 ? '#7fe3a1' : '#8d93a1';
  return n.color || FRAME;
}
const isKeystone = n => !!n.group;   // nodes of a limited group are choices: bronze keystone frame

/* ---------- node shapes: small circle · notable hexagon · large octagon · keystone star frame ---------- */
function shapePath(n, r) {
  ctx.beginPath();
  if (isKeystone(n)) {
    for (let i = 0; i < 16; i++) { const a = -Math.PI / 2 + i * Math.PI / 8, rr = i % 2 ? r * 0.86 : r * 1.08; ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
    ctx.closePath();
  } else if (n.shape === 'medium') {
    for (let i = 0; i < 6; i++) { const a = i * Math.PI / 3; ctx.lineTo(Math.cos(a) * r * 1.06, Math.sin(a) * r * 1.06); }
    ctx.closePath();
  } else if (n.shape === 'large') {
    for (let i = 0; i < 8; i++) { const a = Math.PI / 8 + i * Math.PI / 4; ctx.lineTo(Math.cos(a) * r * 1.04, Math.sin(a) * r * 1.04); }
    ctx.closePath();
  } else ctx.arc(0, 0, r, 0, 7);
}

/* ---------- PoE-style frames ----------
   small    → plain circle, silver rim, coloured core
   medium   → notable: thick gold double ring + 4 diagonal studs
   large    → big gold ring with 8 spikes and 4 diamond "crowns"
   keystone → large frame in bronze-red with 12 spikes and coloured crowns */
var FX = null;   // context the frame helpers draw into (main canvas, or a small preview)
const GOLD_M = ['#fff2c4', '#d8b36a', '#8a6424'], SILVER_M = ['#ffffff', '#c3c8d2', '#6b7180'], BRONZE = ['#ffd6b0', '#c9754a', '#6e3218'];
function metal(r, m) {
  const g = FX.createLinearGradient(-r, -r, r, r);
  g.addColorStop(0, m[0]); g.addColorStop(0.45, m[1]); g.addColorStop(1, m[2]);
  return g;
}
/** Visual radius of a node including its frame decorations. */
function frameRadius(n) {
  const r = RADIUS[n.shape];
  return isKeystone(n) ? r + 11 : n.shape === 'large' ? r + 9 : n.shape === 'medium' ? r + 4 : r + 1;
}
function diamond(x, y, s, a) {
  FX.save(); FX.translate(x, y); FX.rotate(a); FX.beginPath();
  FX.moveTo(0, -s); FX.lineTo(s * 0.62, 0); FX.lineTo(0, s); FX.lineTo(-s * 0.62, 0); FX.closePath();
  FX.fill(); FX.stroke(); FX.restore();
}
function spikes(count, r0, r1, half, offset) {
  FX.beginPath();
  for (let i = 0; i < count; i++) {
    const a = offset + i * 2 * Math.PI / count;
    FX.moveTo(Math.cos(a - half) * r0, Math.sin(a - half) * r0);
    FX.lineTo(Math.cos(a) * r1, Math.sin(a) * r1);
    FX.lineTo(Math.cos(a + half) * r0, Math.sin(a + half) * r0);
  }
  FX.fill(); FX.stroke();
}
/** Coloured core: dark base, tinted fill and a soft highlight. */
function drawCore(r, col, strength) {
  FX.beginPath(); FX.arc(0, 0, r, 0, 7); FX.fillStyle = '#15161a'; FX.fill();
  FX.globalAlpha = strength; FX.fillStyle = col; FX.fill(); FX.globalAlpha = 1;
  const g = FX.createRadialGradient(-r * 0.3, -r * 0.35, r * 0.1, 0, 0, r);
  g.addColorStop(0, 'rgba(255,255,255,0.28)'); g.addColorStop(0.55, 'rgba(255,255,255,0.04)'); g.addColorStop(1, 'rgba(0,0,0,0.45)');
  FX.fillStyle = g; FX.fill();
}
/* Metal of the frame by the special lines touching the node: normal = gold / silver, one way = amber, choice = cyan steel,
   portal (a link of PORTAL_LEN or more) = violet; one way / choice + portal = split frame (portal on the lower-right half).
   Keystones stay bronze. The game screen tints its frames the same way. */
const METALS = {
  oneway: { big: ['#fff0d8', '#ffb347', '#8a5410'], small: ['#fff4e0', '#ffc46e', '#94601c'] },
  choice: { big: ['#e6fbff', '#5cc8e8', '#1d5f78'], small: ['#e6fbff', '#7fd3ec', '#2a6a80'] },
  portal: { big: ['#f1e4ff', '#a77bf0', '#4b2a86'], small: ['#f1e4ff', '#b896f2', '#55358f'] },
};
var connTypes = new Map();
function connTypeMap(t) {
  const m = new Map();
  const put = (id, k) => (m.get(id) || m.set(id, new Set()).get(id)).add(k);
  for (const l of t.links.values()) {
    if (l.type === 'oneway' || l.type === 'choice') { put(l.from, l.type); put(l.to, l.type); }
    if (linkLook(t, l) === 'portal') { put(l.from, 'portal'); put(l.to, 'portal'); }
  }
  return m;
}
/** Path of a node outline that is not a circle (size s = half-diagonal of the diamond). Same shapes as the game draws. */
function nodeOutline(g, form, s) {
  g.beginPath();
  if (form === 'square') { const k = s * 0.78; g.rect(-k, -k, 2 * k, 2 * k); }
  else if (form === 'star') { for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? s * 0.52 : s * 1.12; g.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); } g.closePath(); }
  else { g.moveTo(0, -s); g.lineTo(s, 0); g.lineTo(0, s); g.lineTo(-s, 0); g.closePath(); }
}
function drawFrame(n, r, col, c, conn) {
  if (conn && conn.size && !isKeystone(n)) {
    const order = ['oneway', 'choice', 'portal'], types = order.filter(k => conn.has(k));
    if (types.length > 2) types.splice(1, 1);   // one way + choice + portal: show one way and portal
    drawFrameMetal(n, r, col, c, types[0]);
    if (types.length > 1) {           // second metal over the lower-right half: no seam, the first one is underneath
      const g = c || ctx; g.save(); g.beginPath(); g.moveTo(-80, 80); g.lineTo(80, -80); g.lineTo(80, 80); g.closePath(); g.clip();
      drawFrameMetal(n, r, col, c, types[1]); g.restore();
    }
    return;
  }
  drawFrameMetal(n, r, col, c, null);
}
function drawFrameMetal(n, r, col, c, type) {
  const SILVER = type ? METALS[type].small : SILVER_M, GOLD = type ? METALS[type].big : GOLD_M;
  FX = c || ctx;
  const key = isKeystone(n);
  FX.lineJoin = 'round';
  if (n.form && n.form !== 'circle') {   // square, diamond or star outline: the same metals as the round frames
    const d = r * 1.22, m = key ? BRONZE : n.shape === 'small' ? SILVER : GOLD, edge = key ? '#3d1608' : '#3a2a0c';
    const rh = s2 => nodeOutline(FX, n.form, s2);
    if (key || n.shape === 'large') { FX.fillStyle = metal(d + 8, m); FX.strokeStyle = edge; FX.lineWidth = 1; spikes(key ? 12 : 4, d * 0.75, d + 9, key ? 0.13 : 0.22, key ? -Math.PI / 2 + Math.PI / 12 : Math.PI / 4); }
    FX.save(); rh(d - 1.5); FX.clip(); drawCore(d * (n.form === 'star' ? 1.1 : 1), col, n.shape === 'small' ? 0.62 : 0.72); FX.restore();
    FX.lineJoin = 'miter';
    FX.strokeStyle = metal(d, m); FX.lineWidth = n.shape === 'small' ? 2.6 : 4.2; rh(d - (n.shape === 'small' ? 0.5 : 1.2)); FX.stroke();
    FX.strokeStyle = edge; FX.lineWidth = 1; rh(d + 1.2); FX.stroke();
    if (n.shape !== 'small') { rh(d - 3.6); FX.stroke(); }
    FX.lineJoin = 'round';
    return;
  }
  if (n.shape === 'small' && !key) {
    drawCore(r, col, 0.62);
    FX.strokeStyle = metal(r, SILVER); FX.lineWidth = 2.2; FX.beginPath(); FX.arc(0, 0, r, 0, 7); FX.stroke();
    FX.strokeStyle = 'rgba(0,0,0,0.55)'; FX.lineWidth = 1; FX.beginPath(); FX.arc(0, 0, r - 1.6, 0, 7); FX.stroke();
    return;
  }
  const m = key ? BRONZE : GOLD;
  if (n.shape === 'medium' && !key) {
    drawCore(r - 2, col, 0.7);
    FX.fillStyle = metal(r, m); FX.strokeStyle = '#3a2a0c'; FX.lineWidth = 1;
    for (let i = 0; i < 4; i++) { const a = Math.PI / 4 + i * Math.PI / 2; diamond(Math.cos(a) * (r + 1), Math.sin(a) * (r + 1), 4.5, a + Math.PI / 2); }
    FX.strokeStyle = metal(r, m); FX.lineWidth = 4; FX.beginPath(); FX.arc(0, 0, r - 1, 0, 7); FX.stroke();
    FX.strokeStyle = '#3a2a0c'; FX.lineWidth = 1; FX.beginPath(); FX.arc(0, 0, r + 1, 0, 7); FX.stroke();
    FX.beginPath(); FX.arc(0, 0, r - 3.2, 0, 7); FX.stroke();
    return;
  }
  // large and keystone
  FX.fillStyle = metal(r + 8, m); FX.strokeStyle = key ? '#3d1608' : '#3a2a0c'; FX.lineWidth = 1;
  if (key) {
    spikes(12, r + 1, r + 10, 0.13, -Math.PI / 2 + Math.PI / 12);
    FX.strokeStyle = metal(r + 8, m); FX.lineWidth = 1.4; FX.beginPath(); FX.arc(0, 0, r + 6, 0, 7); FX.stroke();
    FX.strokeStyle = '#3d1608'; FX.lineWidth = 1;
  } else spikes(8, r + 1, r + 8, 0.16, Math.PI / 8);
  drawCore(r - 3, col, 0.75);
  FX.strokeStyle = metal(r, m); FX.lineWidth = 5; FX.beginPath(); FX.arc(0, 0, r - 1.5, 0, 7); FX.stroke();
  FX.strokeStyle = key ? '#3d1608' : '#3a2a0c'; FX.lineWidth = 1;
  FX.beginPath(); FX.arc(0, 0, r + 1, 0, 7); FX.stroke();
  FX.beginPath(); FX.arc(0, 0, r - 4, 0, 7); FX.stroke();
  // crowns at the cardinal points
  for (let i = 0; i < 4; i++) {
    const a = -Math.PI / 2 + i * Math.PI / 2, cx = Math.cos(a) * (r + 1), cy = Math.sin(a) * (r + 1);
    FX.fillStyle = metal(r, m); FX.strokeStyle = key ? '#3d1608' : '#3a2a0c';
    diamond(cx, cy, key ? 8 : 7, a + Math.PI / 2);
    FX.fillStyle = key ? col : '#fff6d8'; diamond(cx, cy, key ? 3.6 : 2.6, a + Math.PI / 2);
  }
}

/* ---------- short value labels ("+2 HP") ---------- */
const SHORT = { max_health: 'HP', armor: 'Armor', armor_toughness: 'Tough', attack_damage: 'Dmg', attack_speed: 'AtkSpd', movement_speed: 'Speed',
  luck: 'Luck', knockback_resistance: 'KB res', crit_chance: 'Crit', crit_damage: 'CritDmg', dodge_chance: 'Dodge', healing_received: 'Heal',
  spell_power: 'Spell', spell_resist: 'SpellRes', max_mana: 'Mana', mana_regen: 'ManaReg', cooldown_reduction: 'CDR', cast_time_reduction: 'Cast',
  projectile_damage: 'Proj', arrow_velocity: 'ArrowVel', experience_gained: 'XP', fire_damage: 'Fire', cold_damage: 'Cold', mining_speed: 'Mining' };
function shortValue(n) {
  const a = n.attributes[0];
  if (!a) return n.recipes.length || n.click_recipes?.length ? 'recipe' : n.commands.length ? 'cmd' : '';
  const ns = a.attribute.split(':')[0], key = a.attribute.split(':').pop().replace(/^generic\./, '');
  let name = SHORT[key];
  if (!name) {
    let k = key.replace(/_spell_power$/, ' pow').replace(/_magic_resist$/, ' res');
    if (ns === 'waifingattributes') k = k.split('_').slice(1).join('_');      // drop the class prefix
    k = k.replace(/_amount$/, '').replace(/_keep$/, ' keep').replace(/_/g, ' ');
    name = k.charAt(0).toUpperCase() + k.slice(1);
  }
  name = name.length > 10 ? name.slice(0, 9) + '.' : name;
  const v = Number(a.amount) || 0;
  const amt = a.operation === 'add' ? (v >= 0 ? '+' : '') + (+v.toFixed(3)) : (v >= 0 ? '+' : '') + (+(v * 100).toFixed(1)) + '%';
  return `${amt} ${name}${n.attributes.length > 1 ? ' …' : ''}${n.max_level > 1 ? ' /lv' : ''}`;
}

/* ---------- curved links: quadratic curve, control point = chord middle + normal * curve * length ---------- */
function linkCtrl(a, b, curve) {
  const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy) || 1;
  return [(a.x + b.x) / 2 - dy / len * curve * len, (a.y + b.y) / 2 + dx / len * curve * len];
}
function linkPath(a, b, l) {
  ctx.moveTo(a.x, a.y);
  if (l.curve) { const [cx, cy] = linkCtrl(a, b, l.curve); ctx.quadraticCurveTo(cx, cy, b.x, b.y); }
  else ctx.lineTo(b.x, b.y);
}
/** Point in the middle of a (curved) link and its direction, for one-way arrows. */
function linkMid(a, b, l) {
  if (!l.curve) return [(a.x + b.x) / 2, (a.y + b.y) / 2];
  const [cx, cy] = linkCtrl(a, b, l.curve);
  return [0.25 * a.x + 0.5 * cx + 0.25 * b.x, 0.25 * a.y + 0.5 * cy + 0.25 * b.y];
}
function arrowAt(mx, my, dx, dy, color) {
  const len = Math.hypot(dx, dy) || 1, ux = dx / len, uy = dy / len;
  ctx.fillStyle = color; ctx.beginPath();
  ctx.moveTo(mx + ux * 7, my + uy * 7);
  ctx.lineTo(mx - ux * 5 - uy * 6, my - uy * 5 + ux * 6);
  ctx.lineTo(mx - ux * 2, my - uy * 2);
  ctx.lineTo(mx - ux * 5 + uy * 6, my - uy * 5 - ux * 6);
  ctx.closePath(); ctx.fill();
}
/** Curve value so the link follows the circle through a and b around (cx, cy). */
function arcCurve(a, b, cx, cy) {
  const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy);
  if (len < 1) return 0;
  const r = (Math.hypot(a.x - cx, a.y - cy) + Math.hypot(b.x - cx, b.y - cy)) / 2;
  const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
  let ox = mx - cx, oy = my - cy, ol = Math.hypot(ox, oy);
  if (ol < 1e-6) { ox = -dy; oy = dx; ol = len; }
  const amx = cx + ox / ol * r, amy = cy + oy / ol * r;      // middle of the arc
  const px = 2 * amx - mx, py = 2 * amy - my;                  // quadratic control point
  return Math.round(((px - mx) * -dy + (py - my) * dx) / (len * len) * 1000) / 1000;
}
function distToLink(t, l, wx, wy) {
  const a = t.nodes.get(l.from), b = t.nodes.get(l.to);
  if (!a || !b) return Infinity;
  const [cx, cy] = l.curve ? linkCtrl(a, b, l.curve) : [(a.x + b.x) / 2, (a.y + b.y) / 2];
  let best = Infinity, px = a.x, py = a.y;
  for (let i = 1; i <= 20; i++) {
    const s = i / 20, x = (1 - s) * (1 - s) * a.x + 2 * (1 - s) * s * cx + s * s * b.x, y = (1 - s) * (1 - s) * a.y + 2 * (1 - s) * s * cy + s * s * b.y;
    const sx = x - px, sy = y - py, L = sx * sx + sy * sy || 1, u = Math.max(0, Math.min(1, ((wx - px) * sx + (wy - py) * sy) / L));
    best = Math.min(best, Math.hypot(wx - px - u * sx, wy - py - u * sy));
    px = x; py = y;
  }
  return best;
}
function linkAt(t, wx, wy, tol) {
  let best = null, bd = tol;
  for (const l of t.links.values()) { const d = distToLink(t, l, wx, wy); if (d < bd) { bd = d; best = l; } }
  return best;
}
/** Bend tool: the curve passes through the mouse (quadratic peak = half the control offset). */
function bendTo(t, l, wx, wy) {
  const a = t.nodes.get(l.from), b = t.nodes.get(l.to);
  const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy) || 1;
  const off = ((wx - (a.x + b.x) / 2) * -dy + (wy - (a.y + b.y) / 2) * dx) / len;
  let c = Math.round(off * 2 / len * 100) / 100;
  if (Math.abs(c) < 0.03) c = 0;
  l.curve = Math.max(-1.5, Math.min(1.5, c));
}

/* ---------- regions: soft hulls behind groups of linked nodes of the same colour ---------- */
function hull(pts) {
  pts = pts.slice().sort((p, q) => p[0] - q[0] || p[1] - q[1]);
  if (pts.length < 3) return pts;
  const cross = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], up = [];
  for (const p of pts) { while (lo.length >= 2 && cross(lo[lo.length - 2], lo[lo.length - 1], p) <= 0) lo.pop(); lo.push(p); }
  for (let i = pts.length - 1; i >= 0; i--) { const p = pts[i]; while (up.length >= 2 && cross(up[up.length - 2], up[up.length - 1], p) <= 0) up.pop(); up.push(p); }
  lo.pop(); up.pop();
  return lo.concat(up);
}
function regions(t) {
  const col = new Map([...t.nodes.values()].map(n => [n.id, nodeColor(n)]));
  const adj = new Map([...t.nodes.keys()].map(id => [id, []]));
  for (const l of t.links.values()) {
    if (linkLook(t, l) === 'portal') continue;
    if (col.get(l.from) === col.get(l.to)) { adj.get(l.from).push(l.to); adj.get(l.to).push(l.from); }
  }
  const seen = new Set(), out = [];
  for (const id of t.nodes.keys()) {
    if (seen.has(id)) continue;
    const comp = [], st = [id]; seen.add(id);
    while (st.length) { const x = st.pop(); comp.push(x); for (const y of adj.get(x)) if (!seen.has(y)) { seen.add(y); st.push(y); } }
    if (comp.length >= 4) out.push({ color: col.get(id), nodes: comp.map(i => t.nodes.get(i)) });
  }
  return out;
}
function drawRegions(t, z) {
  for (const g of regions(t)) {
    const h = hull(g.nodes.map(n => [n.x, n.y]));
    ctx.save();
    ctx.lineJoin = 'round'; ctx.lineCap = 'round';
    ctx.strokeStyle = g.color; ctx.fillStyle = g.color;
    ctx.globalAlpha = 0.08; ctx.lineWidth = 56;
    ctx.beginPath(); h.forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1])); ctx.closePath();
    ctx.stroke(); if (h.length > 2) ctx.fill();
    ctx.restore();
  }
}

/* ---------- minimap ---------- */
const mini = document.getElementById('minimap'), mctx = mini?.getContext('2d');
let miniBox = null;
function drawMinimap() {
  if (!mini) return;
  const t = S.cur, on = $('#showMinimap')?.checked;
  mini.hidden = !t || !on || !t.nodes.size;
  if (mini.hidden) return;
  const w = mini.width = 180 * dpr, h = mini.height = 180 * dpr;
  mctx.setTransform(1, 0, 0, 1, 0, 0);
  mctx.fillStyle = '#0c0d10ee'; mctx.fillRect(0, 0, w, h);
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const n of t.nodes.values()) { x0 = Math.min(x0, n.x); y0 = Math.min(y0, n.y); x1 = Math.max(x1, n.x); y1 = Math.max(y1, n.y); }
  const pad = 40, sc = Math.min((w - 12) / (x1 - x0 + pad * 2 || 1), (h - 12) / (y1 - y0 + pad * 2 || 1));
  const ox = w / 2 - (x0 + x1) / 2 * sc, oy = h / 2 - (y0 + y1) / 2 * sc;
  miniBox = { sc, ox, oy };
  for (const n of t.nodes.values()) {
    mctx.fillStyle = nodeColor(n);
    const r = n.shape === 'small' ? 1.2 : 2;
    mctx.fillRect(n.x * sc + ox - r * dpr, n.y * sc + oy - r * dpr, 2 * r * dpr, 2 * r * dpr);
  }
  const [vx0, vy0] = toWorld(0, 0), [vx1, vy1] = toWorld(W, H);
  mctx.strokeStyle = '#ffffffcc'; mctx.lineWidth = 1.5 * dpr;
  mctx.strokeRect(vx0 * sc + ox, vy0 * sc + oy, (vx1 - vx0) * sc, (vy1 - vy0) * sc);
}
if (mini) {
  const jump = e => {
    if (!miniBox) return;
    const r = mini.getBoundingClientRect(), px = (e.clientX - r.left) * dpr, py = (e.clientY - r.top) * dpr;
    const wx = (px - miniBox.ox) / miniBox.sc, wy = (py - miniBox.oy) / miniBox.sc;
    S.view.x = -wx * S.view.z; S.view.y = -wy * S.view.z; requestDraw();
  };
  let down = false;
  mini.addEventListener('pointerdown', e => { down = true; mini.setPointerCapture(e.pointerId); jump(e); });
  mini.addEventListener('pointermove', e => { if (down) jump(e); });
  mini.addEventListener('pointerup', () => { down = false; });
}

/* ---------- legend ---------- */
function updateLegend() {
  const el = $('#legend'); if (!el) return;
  const mode = $('#colorMode').value;
  let rows = [];
  if (mode === 'category') rows = CATS.map(c => [c[2], _(c[1])]).concat([['#6b6f7a', _('Other / empty')]]);
  if (mode === 'kind') rows = [['#ffd34a', _('Start')], ['#ff7a59', _('Keystone')], ['#7fe3a1', _('Several levels')], ['#a070ff', _('Portal')], ['#ffe97a', _('AND')], ['#8d93a1', _('Normal')]];
  if (mode === 'group' && S.cur) {
    const gs = new Map();
    for (const n of S.cur.nodes.values()) if (n.group) gs.set(n.group, hashColor(n.group));
    rows = [...gs].slice(0, 24).map(([b, c]) => [c, b]);
  }
  el.hidden = !rows.length;
  el.innerHTML = rows.map(([c, l]) => `<div><i style="background:${c}"></i>${esc(l)}</div>`).join('');
}

/* ---------- Arrange tools ---------- */
function needSel(min) {
  const ns = selNodes();
  if (ns.length < min) { toast(_('Select at least {n} nodes first (Select tool, drag a box or Shift+click).', { n: min })); return null; }
  return ns;
}
const centroid = ns => [ns.reduce((s, n) => s + n.x, 0) / ns.length, ns.reduce((s, n) => s + n.y, 0) / ns.length];
const step = () => Math.max(30, +$('#arrStep').value || 56);
const R1 = v => Math.round(v);

/** Even circle (orbit) around the centroid, keeping the angular order; links between neighbours follow the circle. */
function arrangeCircle() {
  const ns = needSel(3); if (!ns) return;
  armMirror(ns);
  const [cx, cy] = centroid(ns);
  const r = Math.max(step() * ns.length / (2 * Math.PI), ns.reduce((s, n) => s + Math.hypot(n.x - cx, n.y - cy), 0) / ns.length);
  ns.sort((a, b) => Math.atan2(a.y - cy, a.x - cx) - Math.atan2(b.y - cy, b.x - cx));
  const a0 = Math.atan2(ns[0].y - cy, ns[0].x - cx);
  ns.forEach((n, i) => { const a = a0 + i * 2 * Math.PI / ns.length; n.x = R1(cx + Math.cos(a) * r); n.y = R1(cy + Math.sin(a) * r); });
  curveSelected(cx, cy, true);
  commit();
}
/** Arc: keeps the end nodes, puts the rest evenly on the circle through them (bulging away from the centre of the tree). */
function arrangeArc() {
  const ns = needSel(3); if (!ns) return;
  armMirror(ns);
  const [a, b] = farthestPair(ns);
  const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy);
  const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
  let nx = -dy / len, ny = dx / len;
  if (nx * mx + ny * my < 0) { nx = -nx; ny = -ny; }   // bulge outward from (0,0)
  const sag = len * 0.25, r = (len * len / 4 + sag * sag) / (2 * sag);
  const cx = mx + nx * (sag - r), cy = my + ny * (sag - r);
  const t0 = Math.atan2(a.y - cy, a.x - cx), t1 = Math.atan2(b.y - cy, b.x - cx);
  let span = t1 - t0; while (span > Math.PI) span -= 2 * Math.PI; while (span < -Math.PI) span += 2 * Math.PI;
  const order = ns.slice().sort((p, q) => ((p.x - a.x) * dx + (p.y - a.y) * dy) - ((q.x - a.x) * dx + (q.y - a.y) * dy));
  order.forEach((n, i) => { const ang = t0 + span * i / (order.length - 1); n.x = R1(cx + Math.cos(ang) * r); n.y = R1(cy + Math.sin(ang) * r); });
  curveSelected(cx, cy, true);
  commit();
}
/* ---------- shapes: square / diamond (the same square turned 45°) / star ----------
   Nodes keep their order around the centre and are spread evenly along the outline, starting at
   the first corner (the top one for the diamond and the star). A diamond of 8 nodes is a node that
   splits into two paths of 3 that meet again. */
let lastSquare = null;   // pressing "Square" again on the same selection turns it into a diamond, and back
function shapeOutline(kind, cx, cy, R) {
  const pts = [];
  if (kind === 'star') for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 ? R * 0.48 : R; pts.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]); }
  else { const a0 = kind === 'diamond' ? -Math.PI / 2 : -Math.PI * 3 / 4; for (let i = 0; i < 4; i++) { const a = a0 + i * Math.PI / 2; pts.push([cx + Math.cos(a) * R, cy + Math.sin(a) * R]); } }
  return pts;
}
function arrangeShape(kind) {
  const ns = needSel(4); if (!ns) return;
  armMirror(ns);
  const [cx, cy] = centroid(ns), k = ns.length;
  const unit = shapeOutline(kind, 0, 0, 1);
  let per1 = 0; for (let i = 0; i < unit.length; i++) per1 += Math.hypot(unit[(i + 1) % unit.length][0] - unit[i][0], unit[(i + 1) % unit.length][1] - unit[i][1]);
  // size: keep the current spread (mean distance to the centre, compared with the same outline at size 1), so pressing again does not shrink it
  const unitPts = (() => { const segs1 = unit.map((p, i) => [p, unit[(i + 1) % unit.length]]), l1 = segs1.map(([a, b]) => Math.hypot(b[0] - a[0], b[1] - a[1])); const out = [];
    for (let i = 0; i < k; i++) { let d = per1 * i / k, si = 0; while (d > l1[si] && si < l1.length - 1) { d -= l1[si]; si++; } const [a, b] = segs1[si], f = d / l1[si]; out.push(Math.hypot(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)); } return out.reduce((x, y) => x + y, 0) / k; })();
  const R = Math.max(step() * k / per1, ns.reduce((a, n) => a + Math.hypot(n.x - cx, n.y - cy), 0) / k / unitPts);
  const pts = shapeOutline(kind, cx, cy, R), segs = pts.map((p, i) => [p, pts[(i + 1) % pts.length]]);
  const lens = segs.map(([a, b]) => Math.hypot(b[0] - a[0], b[1] - a[1])), per = lens.reduce((a, b) => a + b, 0);
  const start = Math.atan2(pts[0][1] - cy, pts[0][0] - cx), ang = n => { let a = Math.atan2(n.y - cy, n.x - cx) - start; while (a < 0) a += 2 * Math.PI; return a; };
  ns.sort((a, b) => ang(a) - ang(b));
  ns.forEach((n, i) => {
    let d = per * i / k, si = 0;
    while (d > lens[si] && si < lens.length - 1) { d -= lens[si]; si++; }
    const [a, b] = segs[si], f = lens[si] ? d / lens[si] : 0;
    n.x = R1(a[0] + (b[0] - a[0]) * f); n.y = R1(a[1] + (b[1] - a[1]) * f);
  });
  straightenSelected(false);
  commit();
}
function squareOrDiamond() {
  const key = [...S.sel].sort().join(',');
  const kind = lastSquare && lastSquare.key === key && lastSquare.kind === 'square' ? 'diamond' : 'square';
  arrangeShape(kind);
  lastSquare = { key, kind };
  const b = $('#arrSquare span'); if (b) b.textContent = kind === 'square' ? _('◇ Diamond') : _('□ Square');
}
function farthestPair(ns) {
  let best = [ns[0], ns[1]], bd = -1;
  for (let i = 0; i < ns.length; i++) for (let j = i + 1; j < ns.length; j++) {
    const d = Math.hypot(ns[i].x - ns[j].x, ns[i].y - ns[j].y); if (d > bd) { bd = d; best = [ns[i], ns[j]]; }
  }
  return best;
}
/** Straight, evenly spaced line between the two farthest selected nodes. */
function arrangeLine() {
  const ns = needSel(3); if (!ns) return;
  armMirror(ns);
  const [a, b] = farthestPair(ns), dx = b.x - a.x, dy = b.y - a.y;
  const order = ns.slice().sort((p, q) => ((p.x - a.x) * dx + (p.y - a.y) * dy) - ((q.x - a.x) * dx + (q.y - a.y) * dy));
  order.forEach((n, i) => { const s = i / (order.length - 1); n.x = R1(a.x + dx * s); n.y = R1(a.y + dy * s); });
  straightenSelected(false);
  commit();
}
/** Grid with the spacing of "Step": nodes at a similar height share a row (rows kept), each row evenly spaced and centred. */
function arrangeGrid() {
  const ns = needSel(2); if (!ns) return;
  armMirror(ns);
  const s = step(), [cx, cy] = centroid(ns);
  const sorted = ns.slice().sort((p, q) => p.y - q.y), rows = [];
  for (const n of sorted) {
    const row = rows[rows.length - 1];
    if (row && Math.abs(n.y - row.y) < s * 0.5) { row.items.push(n); row.y = row.items.reduce((a, m) => a + m.y, 0) / row.items.length; }
    else rows.push({ y: n.y, items: [n] });
  }
  const y0 = cy - (rows.length - 1) / 2 * s;
  rows.forEach((row, i) => {
    row.items.sort((p, q) => p.x - q.x);
    const k = row.items.length, rx = row.items.reduce((a, m) => a + m.x, 0) / k;   // each row stays where it was horizontally
    row.items.forEach((n, j) => { n.x = R1(rx + (j - (k - 1) / 2) * s); n.y = R1(y0 + i * s); });
  });
  straightenSelected(false);
  commit();
}
/** Distances from the start nodes, following links (one-way links only forwards). */
function depthFromStarts(t) {
  const d = new Map(), q = [];
  for (const n of t.nodes.values()) if (n.start) { d.set(n.id, 0); q.push(n.id); }
  while (q.length) {
    const id = q.shift();
    for (const l of t.links.values()) {
      let nb = null;
      if (l.from === id) nb = l.to; else if (l.to === id && !isDirected(l)) nb = l.from;
      if (nb && !d.has(nb)) { d.set(nb, d.get(id) + 1); q.push(nb); }
    }
  }
  return d;
}
/** Untangle: pushes overlapping nodes apart and pulls linked ones to "Step" (only the selection moves; nothing selected = all). */
function relax() {
  armMirror(selNodes());
  const t = S.cur; if (!t) return;
  const all = [...t.nodes.values()], mov = S.sel.size ? new Set(S.sel) : new Set(t.nodes.keys());
  const s = step(), minD = s * 0.8, cell = minD;
  for (let it = 0; it < 160; it++) {
    const fx = new Map(), fy = new Map(), add = (id, x, y) => { fx.set(id, (fx.get(id) || 0) + x); fy.set(id, (fy.get(id) || 0) + y); };
    const grid = new Map();
    for (const n of all) { const k = Math.floor(n.x / cell) + ',' + Math.floor(n.y / cell); (grid.get(k) || grid.set(k, []).get(k)).push(n); }
    for (const n of all) {
      const gx = Math.floor(n.x / cell), gy = Math.floor(n.y / cell);
      for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) for (const m of grid.get((gx + i) + ',' + (gy + j)) || []) {
        if (m === n) continue;
        const dx = n.x - m.x, dy = n.y - m.y, d = Math.hypot(dx, dy) || 0.01;
        if (d < minD) { const f = (minD - d) / 2; add(n.id, dx / d * f, dy / d * f); }
      }
    }
    for (const l of t.links.values()) {
      if (linkLook(t, l) === 'portal') continue;
      const a = t.nodes.get(l.from), b = t.nodes.get(l.to), dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy) || 0.01;
      const f = (d - s) * 0.08;
      add(a.id, dx / d * f, dy / d * f); add(b.id, -dx / d * f, -dy / d * f);
    }
    for (const n of all) if (mov.has(n.id)) { n.x += Math.max(-8, Math.min(8, fx.get(n.id) || 0)); n.y += Math.max(-8, Math.min(8, fy.get(n.id) || 0)); }
  }
  for (const n of all) if (mov.has(n.id)) { n.x = R1(n.x); n.y = R1(n.y); }
  commit();
}
function snapSelected() {
  armMirror(selNodes());
  const ns = selNodes().length ? selNodes() : [...(S.cur?.nodes.values() || [])], g = gridSize();
  for (const n of ns) { n.x = Math.round(n.x / g) * g; n.y = Math.round(n.y / g) * g; }
  commit();
}
/** Curves every link between two selected nodes that sit at a similar distance from (cx, cy) along that circle. */
function curveSelected(cx, cy, quiet) {
  if (!S.mirrorJob) armMirror(selNodes());
  const t = S.cur; let k = 0;
  for (const l of t.links.values()) {
    if (!S.sel.has(l.from) || !S.sel.has(l.to)) continue;
    const a = t.nodes.get(l.from), b = t.nodes.get(l.to), ra = Math.hypot(a.x - cx, a.y - cy), rb = Math.hypot(b.x - cx, b.y - cy);
    if (Math.abs(ra - rb) > 0.15 * Math.max(ra, rb)) continue;
    l.curve = arcCurve(a, b, cx, cy); k++;
  }
  if (!quiet) { commit(); toast(_('Curved {n} links.', { n: k })); }
  return k;
}
function straightenSelected(doCommit = true) {
  if (!S.mirrorJob) armMirror(selNodes());
  const t = S.cur; let k = 0;
  for (const l of t.links.values()) if (S.sel.has(l.from) && S.sel.has(l.to) && l.curve) { l.curve = 0; k++; }
  if (doCommit) { commit(); toast(_('Straightened {n} links.', { n: k })); }
}

/* ---------- wiring ---------- */
const on = (id, fn) => { const el = document.getElementById(id); if (el) el.onclick = () => { if (S.cur) fn(); }; };
on('arrCircle', arrangeCircle);
on('arrArc', arrangeArc);
on('arrLine', arrangeLine);
on('arrGrid', arrangeGrid);
on('arrSquare', squareOrDiamond);
on('arrMirrorFix', repairMirror);
on('arrRelax', relax);
on('arrSnap', snapSelected);
on('arrCurveC', () => { const ns = selNodes(); if (ns.length < 2) return toast(_('Select at least {n} nodes first (Select tool, drag a box or Shift+click).', { n: 2 })); const [cx, cy] = centroid(ns); curveSelected(cx, cy); });
on('arrCurveO', () => curveSelected(0, 0));
on('arrStraight', () => straightenSelected(true));
for (const id of ['colorMode', 'showRegions', 'showValues', 'showMinimap']) {
  const el = document.getElementById(id);
  if (!el) continue;
  try { const v = localStorage.getItem('wf.view.' + id); if (v !== null) { if (el.type === 'checkbox') el.checked = v === '1'; else el.value = v; } } catch (e) { /* storage blocked */ }
  el.addEventListener('change', () => {
    try { localStorage.setItem('wf.view.' + id, el.type === 'checkbox' ? (el.checked ? '1' : '0') : el.value); } catch (e) { /* storage blocked */ }
    updateLegend(); requestDraw();
  });
}
updateLegend();

/* ---------- Add tool: right-click menu ----------
   Left click places the current size + type + colour, so many nodes can be placed in a row
   (the ghost under the cursor shows what will be placed). Right-click on empty space opens
   the menu to change it; picking only changes the choice, it never places a node.
   Right-click on a node selects it, middle-click on a node deletes it. */
const ADD_SIZES = [['small', 'Small'], ['medium', 'Notable'], ['large', 'Large']];
const ADD_TYPES = [['normal', 'Normal', '#8d93a1'], ['start', 'Start', '#ffd34a'], ['and', 'AND', '#ffe97a']];
const SIZE_COL = { small: '#8d93a1', medium: '#6aa9ff', large: '#e0604f' };
const addState = { shape: 'small', type: 'normal', copy: false, color: '', form: 'circle' };
try { Object.assign(addState, JSON.parse(localStorage.getItem('wf.add3') || '{}')); } catch (e) { /* storage unavailable */ }
if (!RADIUS[addState.shape]) addState.shape = 'small';
if (!ADD_TYPES.some(t => t[0] === addState.type)) addState.type = 'normal';
function saveAddState() { try { localStorage.setItem('wf.add3', JSON.stringify(addState)); } catch (e) { /* ignore */ } }
/** Colour the ghost / new node is drawn with. */
function addColor() { return addState.color || (addState.type === 'normal' ? SIZE_COL[addState.shape] : ADD_TYPES.find(t => t[0] === addState.type)[2]); }

/** Draws a size (with the current type's marks and colour) as a tiny node picture into a 36×36 css-px canvas. */
function drawPresetIcon(cnv, shape, type) {
  const d = 2, c = cnv.getContext('2d'); cnv.width = 36 * d; cnv.height = 36 * d;
  const kind = type === 'and' ? type : 'normal';
  const fake = { shape, kind, group: '', form: addState.form }, r = RADIUS[shape], s = 14 / (frameRadius(fake) * ({ diamond: 1.25, square: 1.0, star: 1.4 }[addState.form] || 1));
  const col = addState.color || (type === 'normal' ? SIZE_COL[shape] : ADD_TYPES.find(t => t[0] === type)[2]);
  c.setTransform(d * s, 0, 0, d * s, 18 * d, 18 * d);
  drawFrame(fake, r, col, c);
  c.textAlign = 'center'; c.textBaseline = 'middle';
  if (kind === 'and') { c.fillStyle = '#2a2000'; c.font = `bold ${r}px system-ui,sans-serif`; c.fillText('&', 0, 1); }
  if (type === 'start') {
    c.fillStyle = '#ffd34a'; c.strokeStyle = '#3a2c00'; c.lineWidth = 1; c.beginPath();
    for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * 0.28 : r * 0.62; c.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
    c.closePath(); c.fill(); c.stroke();
  }
}
function syncAddUI() {
  $('#newShape').value = addState.shape;
  $('#newKind').value = addState.type === 'and' ? 'and' : 'normal';
  $('#newStart').checked = addState.type === 'start';
  $('#copySel').checked = addState.copy;
  $('#newColor').value = addState.color;
  $$('#addMenu canvas[data-icon]').forEach(cv2 => drawPresetIcon(cv2, cv2.dataset.icon, addState.type));
  $$('#addMenuSizes .pchip').forEach(b => b.classList.toggle('on', !addState.copy && b.dataset.size === addState.shape));
  $$('#addMenuTypes button').forEach(b => b.classList.toggle('on', b.dataset.type === addState.type));
  $$('#addMenuForms button').forEach(b => b.classList.toggle('on', b.dataset.form === addState.form));
  $$('#addMenuColors [data-col]').forEach(b => b.classList.toggle('on', b.dataset.col === addState.color));
  $('#addMenuPick').value = addState.color || '#c9a24a';
  $('#addMenuCopy').classList.toggle('on', addState.copy);
}
function setAdd(patch) { Object.assign(addState, patch); syncAddUI(); saveAddState(); requestDraw(); }
function buildAddUI() {
  $('#addMenuSizes').innerHTML = ADD_SIZES.map(([k, l]) => `<button type="button" class="pchip" data-size="${k}"><canvas data-icon="${k}"></canvas><span>${esc(_(l))}</span></button>`).join('');
  $('#addMenuTypes').innerHTML = ADD_TYPES.map(([k, l]) => `<button type="button" data-type="${k}">${esc(_(l))}</button>`).join('');
  $('#addMenuColors').innerHTML = `<button type="button" class="swatch nocol" data-col="" title="${esc(_('No color'))}">∅</button>` +
    PALETTE.map(c => `<button type="button" class="swatch" data-col="${c}" style="background:${c}"></button>`).join('');
  syncAddUI();
}
function updateAddBar() { if (S.tool !== 'add') closeAddMenu(); if (S.tool !== 'link' && typeof closeLinkMenu === 'function') closeLinkMenu(); }

var addMenuAt = null;
function openAddMenu(cx, cy) {
  addMenuAt = true;
  const m = $('#addMenu'); m.hidden = false;
  $('#addMenuCopy').disabled = !(S.cur && S.sel.size === 1);
  syncAddUI();
  const w = m.offsetWidth, h = m.offsetHeight;
  m.style.left = Math.min(cx, innerWidth - w - 8) + 'px'; m.style.top = Math.min(cy, innerHeight - h - 8) + 'px';
}
function closeAddMenu() { $('#addMenu').hidden = true; addMenuAt = null; }
$('#addMenu').onclick = e => {
  if (e.target.closest('#addMenuCopy')) { setAdd({ copy: !addState.copy }); closeAddMenu(); return; }
  const ty = e.target.closest('#addMenuTypes button'); if (ty) { setAdd({ type: ty.dataset.type, copy: false }); return; }
  const fo = e.target.closest('#addMenuForms button'); if (fo) { setAdd({ form: fo.dataset.form, copy: false }); return; }
  const co = e.target.closest('#addMenuColors [data-col]'); if (co) { setAdd({ color: co.dataset.col, copy: false }); return; }
  const b = e.target.closest('#addMenuSizes .pchip'); if (!b) return;
  setAdd({ shape: b.dataset.size, copy: false }); closeAddMenu();   // choose only: the next left click places it
};
$('#addMenuPick').addEventListener('input', e => setAdd({ color: e.target.value, copy: false }));
$('#addMenu').addEventListener('contextmenu', e => e.preventDefault());
window.addEventListener('pointerdown', e => { if (!$('#addMenu').hidden && !e.target.closest('#addMenu')) closeAddMenu(); }, true);
window.addEventListener('keydown', e => { if (e.key === 'Escape') closeAddMenu(); });
$('#lang').addEventListener('change', () => setTimeout(buildAddUI, 0));
buildAddUI();

/* ---------- spread / pull together ----------
   Scales the selection (nothing selected = whole tree). With Mirror on, or with the whole
   tree, it scales around (0,0) and moves the mirrored copies too, so symmetry is kept;
   otherwise around the selection's centre. Shift = bigger step. */
function scaleSelection(f) {
  const t = S.cur; if (!t) return;
  let ns = selNodes(); const whole = !ns.length;
  if (whole) ns = [...t.nodes.values()];
  if (ns.length < 2 && !(mirrorCount() > 1 && ns.length)) return toast(_('Select at least {n} nodes first (Select tool, drag a box or Shift+click).', { n: 2 }));
  const origin = whole || mirrorCount() > 1;
  const all = new Set(ns);
  if (origin && !whole) for (const n of ns) for (const c of counterparts(t, n)) all.add(c.node);
  const [cx, cy] = origin ? [0, 0] : centroid(ns);
  for (const n of all) { n.x = R1(cx + (n.x - cx) * f); n.y = R1(cy + (n.y - cy) * f); }
  commit();
  toast(origin ? _('Scaled around (0,0) — symmetry kept.') : _('Scaled around the selection centre.'));
}
/** Turns the selection by `deg` degrees (positive = clockwise on screen) around its centre, keeping its shape;
 *  nothing selected = whole tree around (0,0). With Mirror on, the mirrored copies follow so the symmetry is kept.
 *  Curved links keep their bend (it is relative to the link). */
const R10 = v => Math.round(v * 10) / 10;
function rotateBy(deg, quiet) {
  const t = S.cur; if (!t || !deg) return;
  let ns = selNodes(); const whole = !ns.length;
  if (whole) ns = [...t.nodes.values()];
  if (ns.length < 2) return toast(_('Select at least {n} nodes first (Select tool, drag a box or Shift+click).', { n: 2 }));
  let [cx, cy] = whole ? [0, 0] : centroid(ns);
  if (!whole && snapOn() && Math.abs(deg % 90) < 1e-9) { const s = gridSize(); cx = Math.round(cx / s) * s; cy = Math.round(cy / s) * s; }   // quarter turns stay on the grid
  const sel = new Set(ns), a = deg * Math.PI / 180, c = Math.cos(a), sn = Math.sin(a);
  const mirrors = whole ? ns.map(() => []) : ns.map(n => counterparts(t, n).filter(m => !sel.has(m.node)));
  ns.forEach((n, i) => {
    const dx = n.x - cx, dy = n.y - cy;
    n.x = R10(cx + dx * c - dy * sn); n.y = R10(cy + dx * sn + dy * c);
    mirrors[i].forEach(m => { const [qx, qy] = mirrorApply(m.k, n.x, n.y); m.node.x = R10(qx); m.node.y = R10(qy); });
  });
  commit();
  const shown = +deg.toFixed(2);
  if (!quiet) toast(whole ? _('Whole tree turned {a}° around (0,0).', { a: shown }) : _('Selection turned {a}°.', { a: shown }));
}
const rotAngle = () => Math.abs(parseFloat($('#rotAngle')?.value)) || 90;
function rotateSelection(dir) { rotateBy(dir * rotAngle()); }
/** Main direction of a set of points (principal axis), in degrees on screen (0 = pointing right). */
function mainAxis(ns) {
  const [cx, cy] = centroid(ns);
  let sxx = 0, syy = 0, sxy = 0;
  for (const n of ns) { const dx = n.x - cx, dy = n.y - cy; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
  return 0.5 * Math.atan2(2 * sxy, sxx - syy) * 180 / Math.PI;
}
/** Smallest turn that brings an axis (direction without sense) from `from` to `to` degrees: -90..90. */
const axisTurn = (from, to) => { let d = (to - from) % 180; if (d > 90) d -= 180; if (d <= -90) d += 180; return d; };
function straighten(toCentre) {
  const ns = needSel(2); if (!ns) return;
  const ax = mainAxis(ns);
  let target;
  if (toCentre) { const [cx, cy] = centroid(ns); target = Math.atan2(cy, cx) * 180 / Math.PI; }
  else target = Math.round(ax / 15) * 15;
  const d = axisTurn(ax, target);
  if (Math.abs(d) < 0.05) return toast(_('Aligned: turned {a}°.', { a: 0 }));
  rotateBy(d, true);
  toast(_('Aligned: turned {a}°.', { a: +d.toFixed(1) }));
}
/** Mirror axis: a line through the selection centre or (0,0); angle 0 = vertical, 90 = horizontal (clockwise). */
function mirrorAxis(ns) {
  const deg = parseFloat($('#mirAngle')?.value) || 0, a = deg * Math.PI / 180;
  const [px, py] = $('#mirPivot')?.value === 'origin' ? [0, 0] : centroid(ns);
  return { px, py, dx: -Math.sin(a), dy: Math.cos(a) };
}
function reflectPt(ax, x, y) {
  const rx = x - ax.px, ry = y - ax.py, k = rx * ax.dx + ry * ax.dy;
  return [R10(ax.px + 2 * k * ax.dx - rx), R10(ax.py + 2 * k * ax.dy - ry)];
}
function flipSelection() {
  const t = S.cur, ns = needSel(1); if (!ns) return;
  armMirror(ns);
  const ax = mirrorAxis(ns), sel = new Set(ns.map(n => n.id));
  for (const n of ns) [n.x, n.y] = reflectPt(ax, n.x, n.y);
  for (const l of t.links.values()) if (sel.has(l.from) && sel.has(l.to) && l.curve) l.curve = -l.curve;   // a mirror turns bends the other way
  commit();
  toast(_('Flipped {n} nodes.', { n: ns.length }));
}
function mirrorCopy() {
  const t = S.cur, ns = needSel(1); if (!ns) return;
  const ax = mirrorAxis(ns), map = new Map();
  for (const n of ns) {
    const [x, y] = reflectPt(ax, n.x, n.y);
    const c = newNode(freshId(t), x, y, cloneProps(n));
    c.start = n.start; t.nodes.set(c.id, c); map.set(n.id, c.id);
  }
  const near = (x, y) => { for (const m of t.nodes.values()) if (!map.has(m.id) && ![...map.values()].includes(m.id) && Math.hypot(m.x - x, m.y - y) < 2) return m.id; return null; };
  for (const l of [...t.links.values()]) {
    const fa = map.has(l.from), fb = map.has(l.to);
    if (!fa && !fb) continue;
    let a = fa ? map.get(l.from) : null, b = fb ? map.get(l.to) : null;
    if (!a) { const o = t.nodes.get(l.from), r = reflectPt(ax, o.x, o.y); a = near(r[0], r[1]); }   // link to a node outside: copy it if that node's mirror exists
    if (!b) { const o = t.nodes.get(l.to), r = reflectPt(ax, o.x, o.y); b = near(r[0], r[1]); }
    if (!a || !b || a === b || t.links.has(linkKey(a, b))) continue;
    const nl = Object.assign({}, l, { from: a, to: b }); if (nl.curve) nl.curve = -nl.curve;
    t.links.set(linkKey(a, b), nl);
  }
  replicateMirrored(t, [...map.values()]);
  S.sel = new Set(map.values());
  commit();
  toast(_('Mirrored copy: {n} nodes.', { n: map.size }));
}
on('arrRotR', () => rotateSelection(1));
on('arrRotL', () => rotateSelection(-1));
on('arrStraighten', () => straighten(false));
on('arrAim', () => straighten(true));
on('arrFlip', flipSelection);
on('arrMirCopy', mirrorCopy);
document.addEventListener('click', e => { const b = e.target.closest('[data-ra]'); if (b) { $('#rotAngle').value = b.dataset.ra; } });
on('arrSpread', () => scaleSelection(window.event?.shiftKey ? 1.25 : 1.1));
on('arrShrink', () => scaleSelection(window.event?.shiftKey ? 0.8 : 1 / 1.1));
window.addEventListener('keydown', e => {
  if (/INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || '') || e.ctrlKey || e.metaKey || !S.cur) return;
  if (e.key === ']') scaleSelection(1.1); else if (e.key === '[') scaleSelection(1 / 1.1);
  else if (e.key.toLowerCase() === 'r' && !e.altKey) rotateSelection(e.shiftKey ? -1 : 1);
});

/* ---------- Connect tool: right-click menu with the link types ---------- */
const LINK_TYPES = [['normal', 'Normal (both ways)', 'Can be taken from either side'],
  ['oneway', 'One way (A → B)', 'Only from A towards B'], ['choice', 'Choice (Node → A or B)', 'One way; of all the choice links leaving A the player can follow only one (the others close)']];
/** Arrow in the middle of a directed link: one head for one way, two (->>) for a choice. */
function typeArrow(l, mx, my, dx, dy, color, scale) {
  const len = Math.hypot(dx, dy) || 1;
  ctx.save(); ctx.translate(mx, my); ctx.scale(scale, scale); arrowAt(0, 0, dx, dy, color);
  if (l.type === 'choice') arrowAt(-dx / len * 8, -dy / len * 8, dx, dy, color);
  ctx.restore();
}
function linkIcon(type) {
  const c = document.createElement('canvas'), d = 2; c.width = 44 * d; c.height = 16 * d; c.className = 'lkic';
  const g = c.getContext('2d'); g.scale(d, d); g.lineWidth = 3; g.lineCap = 'round'; g.lineJoin = 'round';
  g.strokeStyle = type === 'oneway' ? '#ffb347' : type === 'choice' ? '#55c8ee' : '#c9a24a';
  g.fillStyle = g.strokeStyle;
  const head = (x, y, ang, s = 5) => { g.beginPath(); g.moveTo(x, y); g.lineTo(x - Math.cos(ang - 0.5) * s, y - Math.sin(ang - 0.5) * s); g.lineTo(x - Math.cos(ang + 0.5) * s, y - Math.sin(ang + 0.5) * s); g.closePath(); g.fill(); };
  if (type === 'choice') {   // symmetric fork: one way in, two branches out (up and down), each with its small arrow
    g.lineWidth = 2.5; g.beginPath(); g.moveTo(5, 8); g.lineTo(18, 8); g.lineTo(38, 2); g.moveTo(18, 8); g.lineTo(38, 14); g.stroke();
    const a1 = Math.atan2(-6, 20), a2 = Math.atan2(6, 20);
    head(41, 1.1, a1); head(41, 14.9, a2);
    g.beginPath(); g.arc(5, 8, 3.5, 0, 7); g.fill();
    return c;
  }
  g.beginPath(); g.moveTo(5, 8); g.lineTo(39, 8); g.stroke();
  if (type === 'oneway') { g.beginPath(); g.moveTo(40, 8); g.lineTo(31, 3); g.lineTo(31, 13); g.closePath(); g.fill(); }
  for (const x of type === 'oneway' ? [5] : [5, 39]) { g.beginPath(); g.arc(x, 8, 3.5, 0, 7); g.fill(); }
  return c;
}
function buildLinkMenu() {
  const box = $('#linkMenuItems'); box.innerHTML = '';
  for (const [k, l, d] of LINK_TYPES) {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'lkitem'; b.dataset.lt = k; b.title = _(d);
    const sp = document.createElement('span'); sp.textContent = _(l);
    b.append(linkIcon(k), sp); box.append(b);
  }
  syncLinkMenu();
}
var editLink = null;   // link being edited from the menu (right-click on a link), or null = type for new links
function syncLinkMenu() {
  const cur = editLink ? editLink.type : $('#linkType').value;
  $$('#linkMenuItems .lkitem').forEach(b => b.classList.toggle('on', b.dataset.lt === cur));
  $('#linkMenu .am-title').textContent = editLink ? _('This link') : _('Link type');
  $('#lkLevelRow').hidden = !editLink;
  $('#lkModeBox').hidden = !!editLink;
  $$('#lkModes button').forEach(b => b.classList.toggle('on', b.dataset.lm === (S.linkMode || 'normal')));
  if (editLink) $('#lkLevel').value = editLink.min_level || 0;
  }
function openLinkMenu(cx, cy, link) {
  editLink = link || null;
  const m = $('#linkMenu'); m.hidden = false; syncLinkMenu();
  const w = m.offsetWidth, h = m.offsetHeight;
  m.style.left = Math.min(cx, innerWidth - w - 8) + 'px'; m.style.top = Math.min(cy, innerHeight - h - 8) + 'px';
}
function closeLinkMenu() { $('#linkMenu').hidden = true; editLink = null; }
$('#linkMenu').onclick = e => {
  const mb = e.target.closest('[data-lm]');
  if (mb) {   // connect mode: normal / continuous (chain) / static (same origin)
    S.linkMode = mb.dataset.lm; syncLinkMenu(); if (typeof setHint === 'function') setHint();
    toast(_('Connect mode: {m}', { m: mb.textContent })); return;
  }
  const b = e.target.closest('.lkitem'); if (!b) return;
  if (editLink) {   // change the type of this link (and of its mirrored copies)
    for (const l of mirroredLinks(S.cur, editLink)) l.type = b.dataset.lt;
    commit(); closeLinkMenu(); toast(_('Link changed: {t}', { t: _(LINK_TYPES.find(x => x[0] === b.dataset.lt)[1]) })); return;
  }
  $('#linkType').value = b.dataset.lt; $('#linkType').dispatchEvent(new Event('change')); closeLinkMenu();
  toast(_('Link type: {t}', { t: _(LINK_TYPES.find(x => x[0] === b.dataset.lt)[1]) }));
};
$('#lkLevel').addEventListener('change', () => {
  if (!editLink) return;
  const v = Math.max(0, parseInt($('#lkLevel').value, 10) || 0);
  for (const l of mirroredLinks(S.cur, editLink)) l.min_level = v;
  commit();
});
/** The link plus its counterparts under the active mirror (same as editing nodes). */
function mirroredLinks(t, l) {
  const out = [l], a = t.nodes.get(l.from), b = t.nodes.get(l.to);
  if (!a || !b || typeof counterparts !== 'function') return out;
  const ca = counterparts(t, a), cb = counterparts(t, b);
  for (let i = 0; i < ca.length && i < cb.length; i++) {
    const m = t.links.get(linkKey(ca[i].node.id, cb[i].node.id));
    if (m && !out.includes(m)) out.push(m);
  }
  return out;
}
$('#linkType').addEventListener('change', syncLinkMenu);
$('#linkMenu').addEventListener('contextmenu', e => e.preventDefault());
window.addEventListener('pointerdown', e => { if (!$('#linkMenu').hidden && !e.target.closest('#linkMenu')) closeLinkMenu(); }, true);
window.addEventListener('keydown', e => { if (e.key === 'Escape') closeLinkMenu(); });
$('#lang').addEventListener('change', () => setTimeout(buildLinkMenu, 0));
buildLinkMenu();


/* ---------- collapsible panels (remembered per browser) ---------- */
(function panels() {
  const app = $('#app'), KEY = 'wf.panels';
  let st = {};
  try { st = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { st = {}; }
  const ARROWS = { left: ['◀', '▶'], right: ['▶', '◀'], top: ['▲', '▼'] };
  const apply = () => {
    for (const k of ['left', 'right', 'top']) {
      app.classList.toggle('hide-' + k, !!st[k]);
      const b = $(`[data-pt=${k}]`); if (b) b.textContent = ARROWS[k][st[k] ? 1 : 0];
    }
    resizeCanvas(); requestDraw();
  };
  for (const b of $$('[data-pt]')) b.onclick = () => {
    st[b.dataset.pt] = !st[b.dataset.pt];
    try { localStorage.setItem(KEY, JSON.stringify(st)); } catch (e) { /* storage unavailable */ }
    apply();
  };
  apply();
})();

/* ---------- lock positions: no tool can move nodes by accident (remembered per browser) ---------- */
(function lockMove() {
  const box = $('#lockMove'); if (!box) return;
  try { S.lockMove = localStorage.getItem('wf.lockMove') === '1'; } catch (e) { S.lockMove = false; }
  box.checked = S.lockMove;
  box.addEventListener('change', () => {
    S.lockMove = box.checked;
    try { localStorage.setItem('wf.lockMove', S.lockMove ? '1' : '0'); } catch (e) { /* storage unavailable */ }
    toast(S.lockMove ? _('Positions locked: nodes cannot be moved') : _('Positions unlocked'));
  });
})();
