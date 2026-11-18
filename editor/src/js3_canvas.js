/* ============================================================
   Canvas: drawing and interaction
   ============================================================ */
const cv = $('#cv'), ctx = cv.getContext('2d');
let W = 0, H = 0, dpr = 1, drag = null, spaceDown = false, rafId = 0, cursorWorld = [0, 0], linkFrom = null;

function resizeCanvas() {
  dpr = window.devicePixelRatio || 1;
  const r = cv.getBoundingClientRect();
  W = r.width; H = r.height;
  cv.width = Math.max(1, Math.round(W * dpr)); cv.height = Math.max(1, Math.round(H * dpr));
  requestDraw();
}
const toScreen = (x, y) => [W / 2 + S.view.x + x * S.view.z, H / 2 + S.view.y + y * S.view.z];
const toWorld = (sx, sy) => [(sx - W / 2 - S.view.x) / S.view.z, (sy - H / 2 - S.view.y) / S.view.z];
function requestDraw() { if (!rafId) rafId = requestAnimationFrame(() => { rafId = 0; draw(); }); }

function fitView() {
  const t = S.cur;
  if (!t || !t.nodes.size) { S.view = { x: 0, y: 0, z: 1 }; requestDraw(); return; }
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (const n of t.nodes.values()) { x0 = Math.min(x0, n.x); y0 = Math.min(y0, n.y); x1 = Math.max(x1, n.x); y1 = Math.max(y1, n.y); }
  const z = clamp(Math.min(W / (x1 - x0 + 120), H / (y1 - y0 + 120)), 0.1, 2);
  S.view = { z, x: -((x0 + x1) / 2) * z, y: -((y0 + y1) / 2) * z };
  requestDraw();
}
function centerOn(id) {
  const n = S.cur?.nodes.get(id);
  if (!n) return;
  S.view.x = -n.x * S.view.z; S.view.y = -n.y * S.view.z;
  requestDraw();
}

/* ---------- drawing ---------- */
const FRAME = '#c9a24a';
function iconImage(id) { return S.tex.get(id) || null; }

function drawNode(n, z, selected, hovered) {
  const r = RADIUS[n.shape], col = nodeColor(n);
  ctx.save();
  ctx.translate(n.x, n.y);
  ctx.lineJoin = 'round';
  if (S.cur && hasPortal(S.cur, n.id)) { // outer violet ring: this node has a portal (a link of PORTAL_LEN or more)
    ctx.strokeStyle = '#a070ff'; ctx.lineWidth = 2; ctx.setLineDash([3, 3]);
    ctx.beginPath(); ctx.arc(0, 0, frameRadius(n) + 4, 0, 7); ctx.stroke(); ctx.setLineDash([]);
  }
  drawFrame(n, r, col, null, connTypes.get(n.id));
  const img = iconImage(n.icon), big = n.shape !== 'small' || isKeystone(n);
  const is = n.shape === 'small' ? 0.78 : n.shape === 'medium' ? 0.95 : 1.05;
  if (img) { ctx.imageSmoothingEnabled = false; ctx.drawImage(img, -r * is / 2 * 1.2, -r * is / 2 * 1.2, r * is * 1.2, r * is * 1.2); }
  else if (big) {   // abbreviation only on notables / large nodes; small nodes stay clean like PoE
    ctx.fillStyle = '#f4ead0'; ctx.font = `600 ${Math.round(r * 0.66)}px system-ui,sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.shadowColor = '#000'; ctx.shadowBlur = 3;
    ctx.fillText((n.icon.split(':')[1] || n.icon).slice(0, 2), 0, 1); ctx.shadowBlur = 0;
  }
  if (n.kind === 'and') {
    ctx.fillStyle = '#ffe97a'; ctx.font = `bold ${Math.round(r * 0.7)}px system-ui,sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
    ctx.fillText('&' + n.require, r * 0.35, r * 0.95);
  }
  if (S.searchQ && S.srch.has(n.id)) {   // search hit: bright ring
    ctx.strokeStyle = '#ffe97a'; ctx.lineWidth = 3.5; ctx.beginPath(); ctx.arc(0, 0, frameRadius(n) + 7, 0, 7); ctx.stroke();
    ctx.strokeStyle = '#ffe97a55'; ctx.lineWidth = 6; ctx.beginPath(); ctx.arc(0, 0, frameRadius(n) + 12, 0, 7); ctx.stroke();
  }
  if (n.start) {                        // start badge: gold star, independent of the size
    ctx.fillStyle = '#ffd34a'; ctx.strokeStyle = '#3a2c00'; ctx.lineWidth = 1; ctx.beginPath();
    const cx = -r * 0.75, cy = -r * 0.75, sr = Math.max(5, r * 0.42);
    for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? sr * 0.45 : sr; ctx.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr); }
    ctx.closePath(); ctx.fill(); ctx.stroke();
  }
  if (n.max_level > 1) {                // level badge: small dark pill at the lower right, outside the frame
    const fr = frameRadius(n), t = '×' + n.max_level, fs = Math.max(8, Math.round(RADIUS.small * 0.7));
    ctx.font = `bold ${fs}px system-ui,sans-serif`; const w = ctx.measureText(t).width + 6, bx = fr * 0.62, by = fr * 0.62;
    ctx.fillStyle = '#0d1a12ee'; ctx.strokeStyle = '#7fe3a1'; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.roundRect(bx - w / 2, by - fs * 0.65, w, fs * 1.3, fs * 0.65); ctx.fill(); ctx.stroke();
    ctx.fillStyle = '#7fe3a1'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(t, bx, by + 0.5);
  }
  if (selected || hovered) {
    ctx.strokeStyle = selected ? '#6aa9ff' : '#ffffff88'; ctx.lineWidth = 2.5 / 1;
    ctx.beginPath(); ctx.arc(0, 0, frameRadius(n) + 4, 0, 7); ctx.stroke();
  }
  const showVal = $('#showValues')?.checked && z >= 1.0;
  if (z >= 1.4 && n.title) {
    ctx.fillStyle = '#cfd3de'; ctx.font = '9px system-ui,sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'top';
    ctx.fillText(n.title, 0, frameRadius(n) + 5);
  }
  if (showVal) {
    const v = shortValue(n);
    if (v) { ctx.fillStyle = '#9fe0b4'; ctx.font = `${z >= 1.4 ? 8 : 9}px system-ui,sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'top'; ctx.fillText(v, 0, frameRadius(n) + (z >= 1.4 ? 16 : 3)); }
  }
  ctx.restore();
}

function arrowHead(a, b, color) {
  const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy);
  if (len < 20) return;
  const ux = dx / len, uy = dy / len, mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
  ctx.fillStyle = color; ctx.beginPath();
  ctx.moveTo(mx + ux * 7, my + uy * 7);
  ctx.lineTo(mx - ux * 5 - uy * 6, my - uy * 5 + ux * 6);
  ctx.lineTo(mx - ux * 2, my - uy * 2);
  ctx.lineTo(mx - ux * 5 + uy * 6, my - uy * 5 - ux * 6);
  ctx.closePath(); ctx.fill();
}

function draw() {
  S.srch = S.searchQ && S.cur ? searchMatches(S.cur, S.searchQ) : new Set();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = '#101114'; ctx.fillRect(0, 0, cv.width, cv.height);
  const t = S.cur, z = S.view.z;
  ctx.setTransform(dpr * z, 0, 0, dpr * z, dpr * (W / 2 + S.view.x), dpr * (H / 2 + S.view.y));
  const [wx0, wy0] = toWorld(0, 0), [wx1, wy1] = toWorld(W, H);

  // grid
  if ($('#showGrid').checked && $('#snap').checked) {   // snap grid (only drawn while snapping)
    const s = gridSize();
    if (s * z >= 5) {
      ctx.lineWidth = 1 / z; ctx.strokeStyle = '#23262f'; ctx.beginPath();
      for (let x = Math.floor(wx0 / s) * s; x <= wx1; x += s) { ctx.moveTo(x, wy0); ctx.lineTo(x, wy1); }
      for (let y = Math.floor(wy0 / s) * s; y <= wy1; y += s) { ctx.moveTo(wx0, y); ctx.lineTo(wx1, y); }
      ctx.stroke();
    }
  }
  const gs = z < 0.35 ? 200 : 50;
  ctx.lineWidth = 1 / z; ctx.strokeStyle = '#181a20'; ctx.beginPath();
  for (let x = Math.floor(wx0 / gs) * gs; x <= wx1; x += gs) { ctx.moveTo(x, wy0); ctx.lineTo(x, wy1); }
  for (let y = Math.floor(wy0 / gs) * gs; y <= wy1; y += gs) { ctx.moveTo(wx0, y); ctx.lineTo(wx1, y); }
  ctx.stroke();
  ctx.strokeStyle = '#2d3140'; ctx.lineWidth = 1.5 / z; ctx.beginPath();
  ctx.moveTo(0, wy0); ctx.lineTo(0, wy1); ctx.moveTo(wx0, 0); ctx.lineTo(wx1, 0); ctx.stroke();

  // mirror axes
  const mm = mirrorCount();
  if (mm > 1) {
    ctx.strokeStyle = '#5ad1a088'; ctx.lineWidth = 1.5 / z; ctx.setLineDash([8 / z, 6 / z]); ctx.beginPath();
    const big = 5000, v = $('#mirror').value;
    if (v === 'x' || v === 'xy') { ctx.moveTo(0, -big); ctx.lineTo(0, big); }
    if (v === 'y' || v === 'xy') { ctx.moveTo(-big, 0); ctx.lineTo(big, 0); }
    if (v[0] === 'r') for (let k = 0; k < mm; k++) { const [px, py] = mirrorApply(k, big, 0); ctx.moveTo(0, 0); ctx.lineTo(px, py); }
    ctx.stroke(); ctx.setLineDash([]);
  }

  if (t) {
    const links = [...t.links.values()];
    if ($('#showRegions')?.checked) drawRegions(t, z);
    // long links: a silver jump (BRIDGE_LEN+) or a violet portal (PORTAL_LEN+), drawn as a band with marching dashes, below the rest.
    // Portals are faint while editing and hidden in the preview until one end is selected or hovered; jumps are always shown.
    let animate = false;
    const march = (performance.now() / 1000 * 14) % 15;
    const badges = [];
    const TYPE_COL = { oneway: '#ffb347', choice: '#55c8ee', normal: '#8d7a45' };
    for (const l of links) {
      const look = linkLook(t, l); if (look === 'line') continue;
      const a = t.nodes.get(l.from), b = t.nodes.get(l.to);
      if (!a || !b) continue;
      const hot = S.sel.has(a.id) || S.sel.has(b.id) || S.hover === a.id || S.hover === b.id || S.bendHover === l;
      const portal = look === 'portal';
      if (portal && !hot && S.tool === 'view') continue;
      ctx.globalAlpha = hot ? 1 : portal ? 0.3 : 0.75;
      const glow = portal ? 'rgba(176,122,255,0.22)' : 'rgba(220,230,255,0.18)', bed = portal ? 'rgba(42,20,80,0.85)' : 'rgba(30,38,54,0.85)', dash = portal ? '#b07aff' : '#dce6ff';
      if (hot) { ctx.strokeStyle = glow; ctx.lineWidth = 14; ctx.beginPath(); linkPath(a, b, l); ctx.stroke(); }
      ctx.strokeStyle = bed; ctx.lineWidth = 6.5; ctx.beginPath(); linkPath(a, b, l); ctx.stroke();
      ctx.setLineDash([9, 6]); ctx.lineDashOffset = hot && isDirected(l) ? -march : 0;   // the dashes walk towards the destination
      ctx.strokeStyle = dash; ctx.lineWidth = 3.6; ctx.beginPath(); linkPath(a, b, l); ctx.stroke();
      ctx.setLineDash([]); ctx.lineDashOffset = 0;
      if (isDirected(l)) for (const u of [0.15, 0.85]) {   // arrows in the band's own colour near both ends
        const cv0 = l.curve || 0, cx0 = (a.x + b.x) / 2 - (b.y - a.y) * cv0, cy0 = (a.y + b.y) / 2 + (b.x - a.x) * cv0, w = 1 - u;
        const x = w * w * a.x + 2 * w * u * cx0 + u * u * b.x, y = w * w * a.y + 2 * w * u * cy0 + u * u * b.y;
        const dx = 2 * w * (cx0 - a.x) + 2 * u * (b.x - cx0), dy = 2 * w * (cy0 - a.y) + 2 * u * (b.y - cy0);
        typeArrow(l, x, y, dx, dy, portal ? '#d2b4ff' : '#ffffff', 1.25);
      }
      ctx.globalAlpha = 1;
      if (l.min_level > 0) badges.push([a, b, l]);
      if (hot && isDirected(l)) animate = true;
    }
    if (animate) requestDraw();   // keep the dashes marching while a long link is highlighted
    // short links: normal (gold-brown), one way (amber, ->), choice (cyan, ->>)
    for (const l of links) {
      if (linkLook(t, l) !== 'line') continue;
      const a = t.nodes.get(l.from), b = t.nodes.get(l.to);
      if (!a || !b) continue;
      const hot = S.sel.has(a.id) || S.sel.has(b.id) || S.bendHover === l;
      const color = hot ? '#e8ecf5' : TYPE_COL[l.type] || TYPE_COL.normal;
      ctx.strokeStyle = color; ctx.lineWidth = (hot ? 3 : l.type === 'choice' ? 2.6 : 2) / 1;
      ctx.beginPath(); linkPath(a, b, l); ctx.stroke();
      if (isDirected(l) && Math.hypot(b.x - a.x, b.y - a.y) >= 20) { const [mx, my] = linkMid(a, b, l); typeArrow(l, mx, my, b.x - a.x, b.y - a.y, color, 1); }
      if (l.min_level > 0) badges.push([a, b, l]);
    }
    // "needs level N of the origin" badges, on top of the lines
    for (const [a, b, l] of badges) {
      const t3 = 0.3, cx0 = (a.x + b.x) / 2 - (b.y - a.y) * (l.curve || 0), cy0 = (a.y + b.y) / 2 + (b.x - a.x) * (l.curve || 0), u = 1 - t3;
      const x = u * u * a.x + 2 * u * t3 * cx0 + t3 * t3 * b.x, y = u * u * a.y + 2 * u * t3 * cy0 + t3 * t3 * b.y, txt = '≥' + l.min_level;
      ctx.font = 'bold 9px system-ui'; const w = ctx.measureText(txt).width + 8;
      ctx.fillStyle = '#141008f0'; ctx.strokeStyle = '#ffb347'; ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.roundRect(x - w / 2, y - 7, w, 14, 7); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#ffd08a'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(txt, x, y + 0.5);
    }
    // link preview
    if (S.tool === 'link' && linkFrom && t.nodes.has(linkFrom)) {
      const a = t.nodes.get(linkFrom);
      ctx.setLineDash([6 / z, 4 / z]); ctx.strokeStyle = '#6aa9ffcc'; ctx.lineWidth = 2 / z;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(cursorWorld[0], cursorWorld[1]); ctx.stroke(); ctx.setLineDash([]);
    }
    // nodes (with culling)
    const m = 40;
    connTypes = connTypeMap(t);
    for (const n of t.nodes.values()) {
      if (n.x < wx0 - m || n.x > wx1 + m || n.y < wy0 - m || n.y > wy1 + m) continue;
      ctx.globalAlpha = S.searchQ && !S.srch.has(n.id) ? 0.2 : 1;
      drawNode(n, z, S.sel.has(n.id), S.hover === n.id);
      ctx.globalAlpha = 1;
    }
    // ghosts while adding
    if (S.tool === 'add' && !drag) {
      const [gx, gy] = snapPoint(cursorWorld[0], cursorWorld[1], ctrlNow);
      ctx.globalAlpha = 0.5;
      for (let k = 0; k < mm; k++) {
        const [qx, qy] = mirrorApply(k, gx, gy);
        const tpl = $('#copySel').checked && S.sel.size === 1 ? S.cur.nodes.get([...S.sel][0]) : null;
        const g = tpl ? { shape: tpl.shape, kind: tpl.kind, group: tpl.group } : { shape: $('#newShape').value, kind: $('#newKind').value, group: '' };
        ctx.save(); ctx.translate(qx, qy); drawFrame(g, RADIUS[g.shape], k === 0 ? (tpl ? nodeColor(tpl) : addColor()) : '#6aa9ff'); ctx.restore();
      }
      ctx.globalAlpha = 1;
    }
  }
  // selection rectangle (screen space)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  if (drag?.type === 'box') {
    const x = Math.min(drag.x0, drag.x1), y = Math.min(drag.y0, drag.y1), w = Math.abs(drag.x1 - drag.x0), h = Math.abs(drag.y1 - drag.y0);
    ctx.fillStyle = '#6aa9ff22'; ctx.strokeStyle = '#6aa9ff'; ctx.lineWidth = 1; ctx.fillRect(x, y, w, h); ctx.strokeRect(x, y, w, h);
  }
  drawMinimap();
}

/* ---------- interaction ---------- */
function nodeAtScreen(sx, sy) {
  const t = S.cur; if (!t) return null;
  const [wx, wy] = toWorld(sx, sy);
  let best = null, bd = 1e12;
  for (const n of t.nodes.values()) {
    const r = RADIUS[n.shape] + 2, d = (n.x - wx) ** 2 + (n.y - wy) ** 2;
    if (d <= r * r && d < bd) { bd = d; best = n; }
  }
  return best;
}
const evPos = e => { const r = cv.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };

cv.addEventListener('contextmenu', e => e.preventDefault());
cv.addEventListener('pointerdown', e => {
  const t = S.cur; if (!t) return;
  cv.setPointerCapture(e.pointerId);
  const [sx, sy] = evPos(e), [wx, wy] = toWorld(sx, sy), hit = nodeAtScreen(sx, sy);
  if (S.tool === 'link' && e.button === 2 && !spaceDown) {   // right-click: on a link edits that link, elsewhere picks the type for new links
    const l = hit ? null : linkAt(t, wx, wy, 10 / S.view.z);
    openLinkMenu(e.clientX, e.clientY, l); return;
  }
  if (S.tool === 'add' && !spaceDown) {
    if (e.button === 2) {   // right-click: select a node, or open the type menu on empty space
      if (hit) { if (e.shiftKey) { S.sel.has(hit.id) ? S.sel.delete(hit.id) : S.sel.add(hit.id); } else S.sel = new Set([hit.id]); UI.refreshInspector(); requestDraw(); }
      else openAddMenu(e.clientX, e.clientY);
      return;
    }
    if (e.button === 1 && hit) { e.preventDefault(); deleteNodeMirrored(t, hit.id); S.sel.delete(hit.id); commit(); return; }   // middle-click: delete
  }
  const panNow = e.button === 1 || e.button === 2 || spaceDown;
  if (panNow || (S.tool === 'view' && e.button === 0)) {
    drag = { type: 'pan', sx, sy, vx: S.view.x, vy: S.view.y, click: S.tool === 'view' && e.button === 0 ? (hit ? hit.id : '') : null, shift: e.shiftKey, moved: false };
    return;
  }
  if (e.button !== 0) return;

  if (S.tool === 'select') {
    if (hit) {
      if (e.shiftKey) { S.sel.has(hit.id) ? S.sel.delete(hit.id) : S.sel.add(hit.id); UI.refreshInspector(); requestDraw(); return; }
      if (!S.sel.has(hit.id)) { S.sel = new Set([hit.id]); UI.refreshInspector(); }
      if (S.lockMove) { drag = { type: 'box', x0: sx, y0: sy, x1: sx, y1: sy, add: true, base: new Set(S.sel) }; requestDraw(); return; }   // positions locked
      const items = [...S.sel].map(id => t.nodes.get(id)).filter(Boolean).map(n => ({
        n, ox: n.x, oy: n.y, mirrors: counterparts(t, n).filter(c => !S.sel.has(c.node.id)),
      }));
      drag = { type: 'move', wx, wy, items, ref: items.find(i => i.n === hit) || items[0], moved: false };
    } else {
      if (!e.shiftKey) { S.sel.clear(); UI.refreshInspector(); }
      drag = { type: 'box', x0: sx, y0: sy, x1: sx, y1: sy, add: e.shiftKey, base: new Set(S.sel) };
    }
  } else if (S.tool === 'add') {
    if (hit) { S.sel = new Set([hit.id]); UI.refreshInspector(); requestDraw(); return; }
    const [x, y] = snapPoint(wx, wy, e.ctrlKey);
    const chainFrom = e.shiftKey && S.sel.size === 1 ? t.nodes.get([...S.sel][0]) : null;
    if (addNodeMirrored(t, x, y, chainFrom)) commit();
  } else if (S.tool === 'link') {
    if (!hit) { linkFrom = null; drag = { type: 'pan', sx, sy, vx: S.view.x, vy: S.view.y }; requestDraw(); return; }
    if (linkFrom && linkFrom !== hit.id && t.nodes.has(linkFrom)) {
      if (toggleLinkMirrored(t, t.nodes.get(linkFrom), hit)) commit();
      // the chain only continues with Shift held: B becomes the next A; otherwise start over
      linkFrom = e.shiftKey ? hit.id : null; S.sel = new Set([hit.id]); UI.refreshInspector(); requestDraw(); return;
    }
    linkFrom = linkFrom === hit.id ? null : hit.id; S.sel = new Set([hit.id]); UI.refreshInspector(); requestDraw();
  } else if (S.tool === 'delete') {
    if (hit) { deleteNodeMirrored(t, hit.id); commit(); }
  } else if (S.tool === 'bend') {
    const l = linkAt(t, wx, wy, 12 / S.view.z);
    if (!l) { drag = { type: 'pan', sx, sy, vx: S.view.x, vy: S.view.y }; return; }
    if (e.shiftKey) { l.curve = 0; commit(); return; }   // Shift+click: straight again
    drag = { type: 'bend', link: l, moved: false };
  }
});

let ctrlNow = false;
cv.addEventListener('pointermove', e => {
  ctrlNow = e.ctrlKey;
  const [sx, sy] = evPos(e), t = S.cur;
  cursorWorld = toWorld(sx, sy);
  $('#stCursor').textContent = `x ${cursorWorld[0].toFixed(0)}  y ${cursorWorld[1].toFixed(0)}  ·  zoom ${(S.view.z * 100).toFixed(0)}%`;
  if (drag?.type === 'pan') {
    S.view.x = drag.vx + (sx - drag.sx); S.view.y = drag.vy + (sy - drag.sy);
    if (Math.abs(sx - drag.sx) + Math.abs(sy - drag.sy) > 4) drag.moved = true;
    hideNodeTip();
  } else if (drag?.type === 'move' && t) {
    const [wx, wy] = cursorWorld, ref = drag.ref;
    const [nx, ny] = snapPoint(ref.ox + (wx - drag.wx), ref.oy + (wy - drag.wy), e.ctrlKey);
    const ddx = nx - ref.ox, ddy = ny - ref.oy;
    for (const it of drag.items) { it.n.x = it.ox + ddx; it.n.y = it.oy + ddy; }
    for (const it of drag.items) for (const m of it.mirrors) { const [qx, qy] = mirrorApply(m.k, it.n.x, it.n.y); m.node.x = qx; m.node.y = qy; }
    drag.moved = true;
  } else if (drag?.type === 'box') {
    drag.x1 = sx; drag.y1 = sy;
  } else if (drag?.type === 'bend' && t) {
    bendTo(t, drag.link, cursorWorld[0], cursorWorld[1]); drag.moved = true;
  } else {
    if (S.tool === 'bend' && t) S.bendHover = linkAt(t, cursorWorld[0], cursorWorld[1], 12 / S.view.z);
    const h = nodeAtScreen(sx, sy);
    const id = h ? h.id : null;
    if (id !== S.hover) {
      S.hover = id;
      $('#hint').textContent = h ? `${h.title}  ·  ${h.id}  ·  ${h.shape}/${h.kind}  ·  ${_('cost')} ${h.cost}` : toolHint(S.tool);
    }
    if (h && (S.tool === 'view' || e.altKey)) showNodeTip(h, sx, sy); else hideNodeTip();
  }
  requestDraw();
});

function endDrag() {
  const t = S.cur;
  if (drag?.type === 'bend') { if (drag.moved) commit(); drag = null; requestDraw(); return; }
  if (drag?.type === 'pan' && drag.click !== null && !drag.moved) {   // preview: a click (not a drag) selects or clears
    if (drag.shift) { if (drag.click) S.sel.has(drag.click) ? S.sel.delete(drag.click) : S.sel.add(drag.click); }   // Shift: add / remove
    else S.sel = drag.click ? new Set([drag.click]) : new Set();
    UI.refreshInspector(); requestDraw();
  }
  if (drag?.type === 'move' && drag.moved) commit();
  if (drag?.type === 'box' && t) {
    const x0 = Math.min(drag.x0, drag.x1), x1 = Math.max(drag.x0, drag.x1), y0 = Math.min(drag.y0, drag.y1), y1 = Math.max(drag.y0, drag.y1);
    if (x1 - x0 > 3 || y1 - y0 > 3) {
      const set = new Set(drag.add ? drag.base : []);
      for (const n of t.nodes.values()) { const [px, py] = toScreen(n.x, n.y); if (px >= x0 && px <= x1 && py >= y0 && py <= y1) set.add(n.id); }
      S.sel = set; UI.refreshInspector();
    }
  }
  drag = null; requestDraw();
}
cv.addEventListener('pointerup', endDrag);
cv.addEventListener('pointercancel', endDrag);
cv.addEventListener('pointerleave', () => hideNodeTip());

cv.addEventListener('wheel', e => {
  e.preventDefault();
  const [sx, sy] = evPos(e), [wx, wy] = toWorld(sx, sy);
  const z = clamp(S.view.z * Math.exp(-e.deltaY * 0.0015), 0.08, 4);
  S.view.z = z; S.view.x = sx - W / 2 - wx * z; S.view.y = sy - H / 2 - wy * z;
  $('#stCursor').textContent = `zoom ${(z * 100).toFixed(0)}%`;
  requestDraw();
}, { passive: false });

const toolHint = name => ({
  select: _('Click: select · Drag: move (with mirror) · Shift: extend selection · Drag on empty space: box select'),
  add: _('Click: add node · Right-click node: select · Right-click empty: choose type and color · Middle-click node: delete · Shift+click: add and link to the selected node · Ctrl: flip snapping'),
  link: _('Click A then B: create/remove a link · Shift+click B: keep chaining from B · Right-click: link type (on a link: edit it) · Esc: cancel'),
  delete: _('Click a node to delete it (and its mirrored copies when mirror is on)'),
  bend: _('Curve links: drag a link to bend it · Shift+click: straight again'),
  view: _('Preview: nothing can be moved by accident · Hover a node to see what it gives · Click: inspect · Shift+click: select several · Drag: move the view'),
}[name]);
function setTool(name) {
  S.tool = name; linkFrom = null;
  $$('#toolBtns button').forEach(b => b.classList.toggle('on', b.dataset.tool === name));
  hideNodeTip();
  cv.style.cursor = { bend: 'pointer', view: 'grab', select: 'default', add: 'crosshair', link: 'cell', delete: 'not-allowed' }[name];
  $('#hint').textContent = toolHint(name);
  if (typeof updateAddBar === 'function') updateAddBar();
  requestDraw();
}

window.addEventListener('keydown', e => {
  const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName || '');
  if (e.key === ' ' && !typing) { spaceDown = true; cv.style.cursor = 'grab'; e.preventDefault(); }
  if (typing || document.querySelector('dialog[open]')) return;
  const ctrl = e.ctrlKey || e.metaKey, k = e.key.toLowerCase();
  if (ctrl && k === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
  if (ctrl && k === 'y') { e.preventDefault(); redo(); return; }
  if (ctrl && k === 's') { e.preventDefault(); UI.save(); return; }
  if (ctrl && k === 'a' && S.cur) { e.preventDefault(); S.sel = new Set(S.cur.nodes.keys()); UI.refreshInspector(); requestDraw(); return; }
  if (ctrl) return;
  const tool = { b: 'bend', p: 'view', v: 'select', a: 'add', l: 'link', x: 'delete' }[k];
  if (tool) { setTool(tool); return; }
  const t = S.cur;
  if (!t) return;
  if ((e.key === 'Delete' || e.key === 'Backspace') && S.sel.size) {
    for (const id of [...S.sel]) if (t.nodes.has(id)) deleteNodeMirrored(t, id);
    S.sel.clear(); commit(); return;
  }
  if (e.key === 'Escape') { S.sel.clear(); linkFrom = null; UI.refreshInspector(); requestDraw(); return; }
  const step = e.shiftKey ? 1 : (+$('#snapSize').value || 5), dir = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key];
  if (dir && S.sel.size && !S.lockMove) {
    e.preventDefault();
    const moving = [...S.sel].map(id => t.nodes.get(id)).filter(Boolean);
    const mirrors = moving.map(n => counterparts(t, n).filter(c => !S.sel.has(c.node.id)));
    moving.forEach((n, i) => { n.x += dir[0] * step; n.y += dir[1] * step; mirrors[i].forEach(m => { const [qx, qy] = mirrorApply(m.k, n.x, n.y); m.node.x = qx; m.node.y = qy; }); });
    commit();
  }
});
window.addEventListener('keyup', e => { if (e.key === ' ') { spaceDown = false; setTool(S.tool); } });
new ResizeObserver(resizeCanvas).observe(cv);


/* ---------- preview tooltip: what a node gives and needs ---------- */
function fmtAttr(a) {
  const info = attrInfo(a.attribute), name = a.attribute.split(':').pop().replace(/^generic\./, '').replace(/_/g, ' ');
  const desc = info?.name && info.name.toLowerCase() !== name ? `<div class="tipx">${esc(info.name)}</div>` : '';
  const v = Number(a.amount) || 0, sign = v >= 0 ? '+' : '';
  const amt = a.operation === 'add' ? sign + (+v.toFixed(4)) : sign + (+(v * 100).toFixed(2)) + '%' + (a.operation === 'mult_total' ? ' ' + _('(total)') : '');
  return `<b class="${v < 0 ? 'neg' : 'pos'}">${esc(amt)}</b> ${esc(name)}` + desc + (a.condition ? `<div class="tipc">${esc(condText(a.condition))}</div>` : '');
}
function condText(c) {
  if (!c) return '';
  if (c.type === 'all' || c.type === 'any') {
    const inner = (c.conditions || []).map(condText).join(c.type === 'all' ? _(' and ') : _(' or '));
    return (c.not ? _('not') + ' ' : '') + '(' + inner + ')';
  }
  const def = COND_TYPES.find(d => d[0] === c.type);
  const label = def ? _(def[1]) : c.type;
  return (c.not ? _('not') + ' ' : '') + label + (c.value !== undefined && c.value !== '' ? ': ' + c.value : '');
}
function nodeTipHTML(n) {
  const row = (title, items) => items.length ? `<div class="tips"><span>${esc(_(title))}</span>${items.map(x => `<div>${x}</div>`).join('')}</div>` : '';
  const lv = Math.max(1, n.max_level || 1);
  const cost = lv > 1 && n.cost_step ? `${n.cost} (+${n.cost_step}/${_('level')})` : String(n.cost);
  const head = [`${_('cost')} ${esc(cost)}`, lv > 1 ? `${_('Max level')} ${lv}` : '', n.start ? '★ ' + _('Start node') : '',
    n.kind !== 'normal' ? esc(n.kind) : ''].filter(Boolean).join(' · ');
  return `<div class="tipt" style="${n.color ? 'border-color:' + esc(n.color) : ''}">${esc(n.title)}</div>
    <div class="tiph">${esc(n.id)} · ${head}</div>
    ${(n.description || []).length ? `<div class="tipd">${n.description.map(esc).join('<br>')}</div>` : ''}
    ${row('Attributes' + (lv > 1 ? ' (per level)' : ''), n.attributes.map(fmtAttr))}
    ${row('Recipes', n.recipes.map(esc))}
    ${n.group ? row('Limited group', [esc(n.group) + ((S.cur?.group_limits || {})[n.group] !== undefined ? ' · ' + esc(_('max {n} of this group', { n: S.cur.group_limits[n.group] })) : '')]) : ''}
    ${row('Right-click recipes', (n.click_recipes || []).map(esc))}
    ${row('Opens trees', (n.unlock_trees || []).map(esc))}
    ${row('Commands', [...n.commands.map(esc), ...n.unlock_commands.map(c => esc(_('unlocks') + ' /' + c.replace(/^\//, '')))])}
    ${row('Requires', [
      ...(n.requires_prev_level > 1 ? [esc(_('previous node at level {lv}', { lv: n.requires_prev_level }))] : []),
      ...n.requires_advancements.map(a => esc(_('advancement') + ' ' + a)),
      ...n.requires_items.map(i => esc(i.count + '× ' + i.item)),
      ...n.requires_attributes.map(a => esc((attrInfo(a.attribute)?.name || a.attribute) + ' ≥ ' + a.amount)),
    ])}
    ${!n.attributes.length && !n.recipes.length && !n.commands.length && !n.unlock_commands.length && !(n.click_recipes || []).length && !(n.unlock_trees || []).length && !hasPortal(S.cur, n.id) && !n.start ? `<div class="tipe">${esc(_('This node gives nothing yet.'))}</div>` : ''}`;
}
let tipFor = null;
function showNodeTip(n, sx, sy) {
  const tip = $('#nodeTip');
  if (tipFor !== n) { tip.innerHTML = nodeTipHTML(n); tipFor = n; }
  tip.hidden = false;
  const box = cv.getBoundingClientRect(), w = tip.offsetWidth, h = tip.offsetHeight;
  let x = sx + 18, y = sy + 14;
  if (x + w > box.width - 6) x = sx - w - 18;
  if (y + h > box.height - 6) y = Math.max(6, box.height - h - 6);
  tip.style.left = Math.max(6, x) + 'px'; tip.style.top = y + 'px';
}
function hideNodeTip() { const tip = document.getElementById('nodeTip'); if (tip) tip.hidden = true; tipFor = null; }
