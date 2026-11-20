/* ============================================================
   History, mirror, edit actions, validation, colors
   ============================================================ */

/* ---------- history (undo / redo) ---------- */
const snapshot = t => JSON.stringify(treeToJSON(t));

function resetHistory(t) { t.history = [snapshot(t)]; t.future = []; }

/** Call after every user action has been applied. */
function commit() {
  const t = S.cur;
  if (!t) return;
  if (S.mirrorJob) { const job = S.mirrorJob; S.mirrorJob = null; try { job(); } catch (e) { console.error(e); } }
  t.history.push(snapshot(t));
  if (t.history.length > 250) t.history.shift();
  t.future.length = 0;
  t.dirty = true;
  UI.refreshAll();
}

function restore(t, text) {
  const n = treeFromJSON(JSON.parse(text), t.ns, t.name);
  t.title = n.title; t.icon = n.icon; t.background = n.background; t.nodes = n.nodes; t.links = n.links;
  t.exclusive_group = n.exclusive_group; t.group_limits = n.group_limits; t.choice_groups = n.choice_groups; t.choice_limits = n.choice_limits; t.first_start_only = n.first_start_only; t.requires_unlock = n.requires_unlock;
  for (const id of [...S.sel]) if (!t.nodes.has(id)) S.sel.delete(id);
  t.dirty = true;
  UI.refreshAll();
}
function undo() {
  const t = S.cur;
  if (!t || t.history.length < 2) return;
  t.future.push(t.history.pop());
  restore(t, t.history[t.history.length - 1]);
}
function redo() {
  const t = S.cur;
  if (!t || !t.future.length) return;
  const s = t.future.pop();
  t.history.push(s);
  restore(t, s);
}

/* ---------- mirror ---------- */
function mirrorCount() {
  const m = $('#mirror').value;
  if (m === 'none') return 1;
  if (m === 'x' || m === 'y') return 2;
  if (m === 'xy') return 4;
  return parseInt(m.slice(1), 10) || 1;
}
function mirrorApply(k, x, y) {
  const m = $('#mirror').value;
  if (k === 0 || m === 'none') return [x, y];
  if (m === 'x') return [-x, y];
  if (m === 'y') return [x, -y];
  if (m === 'xy') return [[x, y], [-x, y], [x, -y], [-x, -y]][k];
  const a = 2 * Math.PI * k / mirrorCount(), c = Math.cos(a), s = Math.sin(a);
  return [x * c - y * s, x * s + y * c];
}
function nodeAt(t, x, y, exclude) {
  for (const n of t.nodes.values()) {
    if (n === exclude) continue;
    const dx = n.x - x, dy = n.y - y;
    if (dx * dx + dy * dy < 0.6) return n;
  }
  return null;
}
/** Node within `tol` of (x,y). Mirrored copies are looked up with some tolerance, because positions are rounded when saved. */
const MIRROR_TOL = 3;
function nodeNear(t, x, y, exclude, tol = MIRROR_TOL) {
  let best = null, bd = tol * tol;
  for (const n of t.nodes.values()) {
    if (n === exclude) continue;
    const dx = n.x - x, dy = n.y - y, d = dx * dx + dy * dy;
    if (d <= bd) { bd = d; best = n; }
  }
  return best;
}
/** Is mirror copy k a reflection (bends of curved links turn the other way)? */
function mirrorIsReflection(k) {
  const m = $('#mirror').value;
  return m === 'x' || m === 'y' || (m === 'xy' && k !== 3);
}
/** Symmetric counterparts of n: [{k, node}] (excluding n, no repeats). */
function counterparts(t, n) {
  const out = [], seen = new Set();
  for (let k = 1; k < mirrorCount(); k++) {
    const [x, y] = mirrorApply(k, n.x, n.y);
    const m = nodeNear(t, x, y, n);
    if (m && !seen.has(m)) { seen.add(m); out.push({ k, node: m }); }
  }
  return out;
}

/* ---------- edit actions (the caller commits) ---------- */
/** Grid snapping. `flip` (Ctrl held) temporarily inverts the current mode: free <-> snap. */
const snapOn = flip => $('#snap').checked !== !!flip;
const gridSize = () => Math.max(1, +$('#snapSize').value || 5);
function snapPoint(x, y, flip) {
  if (!snapOn(flip)) return [x, y];
  const s = gridSize();
  return [Math.round(x / s) * s, Math.round(y / s) * s];
}

function createNode(t, x, y, template) {
  // size is purely visual; what a node does (attributes, commands, recipes...) is chosen in the inspector
  const props = template ? cloneProps(template) : { shape: $('#newShape').value, kind: $('#newKind').value, start: $('#newStart').checked, form: (typeof addState !== 'undefined' && addState.form) || 'circle' };
  if (!template) {
    props.cost = 1;
    props.title = props.start ? _('Start') : _('Node');
    props.icon = props.start ? 'minecraft:nether_star' : 'minecraft:paper';
    if (props.kind === 'and') { props.icon = 'minecraft:redstone_torch'; props.title = _('Merge'); }
    const nc = $('#newColor')?.value; if (nc) props.color = nc;
  }
  const n = newNode(freshId(t), x, y, props);
  t.nodes.set(n.id, n);
  return n;
}

/** Adds a node (and its mirrors). With chainFrom, links it to that node (and the mirrors to each other). */
function addNodeMirrored(t, x, y, chainFrom) {
  if (nodeAt(t, x, y)) return null;
  const template = ($('#copySel').checked && S.sel.size === 1) ? t.nodes.get([...S.sel][0]) : null;
  const primary = createNode(t, x, y, template);
  if (chainFrom) t.links.set(linkKey(chainFrom.id, primary.id), { from: chainFrom.id, to: primary.id, type: 'normal' });
  for (let k = 1; k < mirrorCount(); k++) {
    const [qx, qy] = mirrorApply(k, x, y);
    if (nodeAt(t, qx, qy)) continue;
    const m = createNode(t, qx, qy, template || primary);
    if (chainFrom) {
      const [fx, fy] = mirrorApply(k, chainFrom.x, chainFrom.y);
      const fm = nodeNear(t, fx, fy, m);
      if (fm) t.links.set(linkKey(fm.id, m.id), { from: fm.id, to: m.id, type: 'normal' });
    }
  }
  S.sel = new Set([primary.id]);
  return primary;
}

function linkAllowed(t, a, b, type) {
  return null;
}

/** Toggles the link a→b (and its mirrors). Returns false if it was not allowed. */
function toggleLinkMirrored(t, a, b) {
  let type = $('#linkType').value;
  if (type === 'auto' || type === 'wormhole') type = 'normal';
  const exists = t.links.has(linkKey(a.id, b.id));
  if (!exists) {
    const err = linkAllowed(t, a, b, type);
    if (err) { toast(err); return false; }
  }
  const apply = (x, y) => {
    const key = linkKey(x.id, y.id);
    if (exists) t.links.delete(key); else t.links.set(key, { from: x.id, to: y.id, type });
  };
  apply(a, b);
  for (let k = 1; k < mirrorCount(); k++) {
    const [ax, ay] = mirrorApply(k, a.x, a.y), [bx, by] = mirrorApply(k, b.x, b.y);
    const ma = nodeNear(t, ax, ay), mb = nodeNear(t, bx, by);
    if (ma && mb && ma !== mb) apply(ma, mb);
  }
  return true;
}

function deleteNodeMirrored(t, id) {
  const n = t.nodes.get(id);
  if (!n) return;
  for (const c of counterparts(t, n)) removeNode(t, c.node.id);
  removeNode(t, id);
}

/* ---------- branch colors ---------- */
const PALETTE = ['#e6194b', '#3cb44b', '#ffe119', '#4363d8', '#f58231', '#911eb4', '#46f0f0', '#f032e6', '#bcf60c', '#fabebe', '#008080', '#e6beff'];
const hexToRgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const rgbToHex = c => '#' + c.map(v => clamp(Math.round(v), 0, 255).toString(16).padStart(2, '0')).join('');

function colorizeBranches(t) {
  const starts = [...t.nodes.values()].filter(n => n.start).sort((a, b) => a.id.localeCompare(b.id));
  if (!starts.length) { toast(_('There are no start nodes to color from.')); return false; }
  const step = id => { // walk without crossing portals (very long links)
    const out = new Set();
    for (const l of t.links.values()) {
      if (linkLook(t, l) === 'portal' || (l.from !== id && l.to !== id)) continue;
      if (isDirected(l) && l.from !== id) continue;
      out.add(l.from === id ? l.to : l.from);
    }
    return out;
  };
  const owner = new Map();
  let frontier = [];
  starts.forEach((s, i) => { owner.set(s.id, new Set([i])); frontier.push(s.id); });
  while (frontier.length) {
    const reach = new Map();
    for (const id of frontier) {
      for (const nb of step(id)) {
        if (owner.has(nb)) continue;
        if (!reach.has(nb)) reach.set(nb, new Set());
        owner.get(id).forEach(s => reach.get(nb).add(s));
      }
    }
    frontier = [];
    for (const [id, set] of reach) { owner.set(id, set); frontier.push(id); }
  }
  for (const [id, set] of owner) {
    const cols = [...set].map(i => hexToRgb(PALETTE[i % PALETTE.length]));
    t.nodes.get(id).color = rgbToHex([0, 1, 2].map(c => cols.reduce((s, x) => s + x[c], 0) / cols.length));
  }
  return true;
}

/* ---------- validation ---------- */
/** lv: Map node id -> level. A node needs its connected previous node(s) at level >= requires_prev_level. */
function canTake(t, n, lv) {
  if (n.start) return true;
  const min = Math.max(1, n.requires_prev_level || 1);
  let c = 0;
  for (const l of t.links.values()) {   // every link that can bring the player here; a link may ask for more levels (min_level)
    if (l.from !== n.id && l.to !== n.id) continue;
    if (isDirected(l) && l.to !== n.id) continue;
    const o = l.from === n.id ? l.to : l.from;
    if ((lv.get(o) || 0) >= Math.max(min, l.min_level || 0)) c++;
  }
  return c >= (n.kind === 'and' ? (n.require > 0 ? n.require : Math.max(1, incoming(t, n.id).size)) : 1);
}
function validSet(t, ids) {
  const ok = new Map();   // assumes every node is levelled to its max_level
  let changed = true;
  while (changed) {
    changed = false;
    for (const id of ids) {
      if (ok.has(id)) continue;
      const n = t.nodes.get(id);
      if (n && canTake(t, n, ok)) { ok.set(id, Math.max(1, n.max_level || 1)); changed = true; }
    }
  }
  return ok;
}

/** Problems of a condition tree (strings); used by validateTree. */
function condProblems(c, out = []) {
  if (!c || typeof c !== 'object') { out.push(_('Invalid condition.')); return out; }
  if (c.type === 'all' || c.type === 'any') {
    if (!(c.conditions || []).length) out.push(_('Empty "{t}" condition.', { t: c.type }));
    (c.conditions || []).forEach(k => condProblems(k, out));
    return out;
  }
  const kind = COND_KIND[c.type];
  if (!kind) { out.push(_('Unknown condition type "{t}".', { t: c.type })); return out; }
  if (kind === 'num') {
    const v = Number(c.value);
    if (/_effects$/.test(c.type) && (c.value === undefined || c.value === '')) { /* optional minimum: empty = at least 1 effect (same as the game) */ }
    else if (c.value === undefined || c.value === '' || !Number.isFinite(v)) out.push(_('Condition "{t}" needs a number.', { t: c.type }));
    else if (/^health/.test(c.type) && (v < 0 || v > 100)) out.push(_('Health conditions are percentages (0-100).'));
    else if (/^light/.test(c.type) && (v < 0 || v > 15)) out.push(_('Light level must be between 0 and 15.'));
    else if (/^food/.test(c.type) && (v < 0 || v > 20)) out.push(_('Hunger bar must be between 0 and 20.'));
  } else if (['dimension', 'biome', 'effect'].includes(c.type) && !String(c.value || '').trim()) out.push(_('Condition "{t}" needs a value.', { t: c.type }));
  return out;
}
function validateTree(t) {
  const P = [];
  const add = (sev, msg, node) => P.push({ sev, msg, node });
  const nodes = [...t.nodes.values()];
  if (!nodes.length) add('warn', _('The tree has no nodes.'));
  if (t.requires_unlock) {
    const id = gameId(t.ns, t.name);
    const opens = S.trees.some(x => [...x.nodes.values()].some(n => (n.unlock_trees || []).some(u => u === id || (t.exclusive_group && u === '#' + t.exclusive_group))));
    if (!opens) add('info', _('This tree is closed until a node opens it, but no loaded tree has a node with "{id}" in "Opens trees".', { id }));
  }
  if (nodes.length && !nodes.some(n => n.start)) add('error', _('There is no start node: nobody will be able to take the first node. Tick "Start node" on at least one node.'));

  const reach = validSet(t, new Set(t.nodes.keys()));
  for (const n of nodes) {
    if (!reach.has(n.id)) add('warn', _('"{title}" ({id}) cannot be reached from the starts.', { title: n.title, id: n.id }), n.id);
    if (n.max_level < 1 || n.cost_step < -n.cost) add('error', _('"{title}" ({id}) has an invalid max level or cost step.', { title: n.title, id: n.id }), n.id);
    if (!n.start && n.requires_prev_level > 1 && ![...incoming(t, n.id)].some(i => (t.nodes.get(i)?.max_level || 1) >= n.requires_prev_level)) add('error', _('"{title}" ({id}) needs a previous node at level {lv}, but none of its neighbors can reach that level.', { title: n.title, id: n.id, lv: n.requires_prev_level }), n.id);
    for (const id of n.requires_nodes || []) if (!t.nodes.has(id)) add('warn', _('Required node "{id}" does not exist.', { id }), n.id);
    for (const id of n.free_choice_if || []) if (!t.nodes.has(id)) add('warn', _('Node "{id}" in "Free choice if you have" does not exist.', { id }), n.id);
    for (const id of n.cap_nodes || []) if (!t.nodes.has(id)) add('warn', _('Node "{id}" in "Higher levels need" does not exist.', { id }), n.id);
    if (n.cap_level > 0 && (n.cap_nodes || []).length && n.cap_level >= (n.max_level || 1)) add('info', _('"{id}": the level cap ({c}) is not below the max level, so it does nothing.', { id: n.id, c: n.cap_level }), n.id);
    for (const r of n.recipes) if (!/^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/.test(r)) add('warn', _('Recipe id "{r}" ({id}) should look like mod:recipe_name.', { r, id: n.id }), n.id);
    for (const r of n.click_recipes || []) if (!/^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/.test(r)) add('warn', _('Right-click recipe id "{r}" ({id}) should look like namespace:file_name.', { r, id: n.id }), n.id);
    { const fine = n.attributes.filter(a => Math.abs(Number(a.amount) * 100 - Math.round(Number(a.amount) * 100)) > 1e-6);
      if (fine.length) add('info', _('"{id}" has amounts with more than 2 decimals ({v}). Values like 0.01 are easier to read.', { id: n.id, v: fine.map(a => a.amount).join(', ') }), n.id); }
    for (const l of t.links.values()) if (l.from === n.id && l.min_level > (n.max_level || 1)) add('error', _('A link from "{id}" needs level {lv}, but that node only goes up to level {max}.', { id: n.id, lv: l.min_level, max: n.max_level || 1 }), n.id);
    { const ch = [...t.links.values()].filter(l => l.type === 'choice' && l.from === n.id).length; if (ch && ((t.choice_limits || {})[n.id] ?? 1) === 0) { const g = (t.choice_groups || {})[n.id];
      if (![...t.nodes.values()].some(o => (o.group_bonus || {})['choice:' + n.id] > 0 || (g && (o.group_bonus || {})['choices:' + g] > 0))) add('warn', _('The choice of "{id}" allows 0 picks and no node opens it: its options can never be taken.', { id: n.id }), n.id); }
    if (ch === 1) add('info', _('"{id}" has only one choice link: a choice needs at least two options.', { id: n.id }), n.id); }
    if (n.group && !((n.group) in (t.group_limits || {}))) add('warn', _('"{id}" is in the group "{g}", but the tree has no such limited group (Tree > Limited groups).', { id: n.id, g: n.group }), n.id);
    for (const g of Object.keys(n.group_bonus || {})) if (!(g in (t.group_limits || {})) && !bonusTargets(t).some(x => x[0] === g)) add('warn', _('"{id}" raises the limit of "{g}", but the tree has no such limited group.', { id: n.id, g }), n.id);
    for (const u of n.unlock_trees || []) {
      if (u.startsWith('#')) { if (!S.trees.some(x => x.exclusive_group === u.slice(1))) add('info', _('"{id}" opens the group {u}, but no loaded tree uses that exclusive group.', { id: n.id, u }), n.id); }
      else if (!S.trees.some(x => gameId(x.ns, x.name) === u)) add('info', _('"{id}" opens the tree {u}, which is not loaded in the editor.', { id: n.id, u }), n.id);
    }
    for (const r of n.requires_items) if (!/^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/.test(r.item)) add('warn', _('Required item "{r}" ({id}) should look like mod:item.', { r: r.item, id: n.id }), n.id);
    for (const r of n.requires_attributes) {
      if (!attrInfo(r.attribute)) add('info', _('Attribute requirement "{attr}" ({id}) is not in the library: make sure its mod is installed.', { attr: r.attribute, id: n.id }), n.id);
      if (!/^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/.test(r.attribute)) add('warn', _('Attribute requirement "{attr}" ({id}) should look like mod:attribute.', { attr: r.attribute, id: n.id }), n.id);
    }
    for (const c of n.unlock_commands) if (c.startsWith('/')) add('info', _('Unlocked command with a leading "/" in {id}: the slash is ignored.', { id: n.id }), n.id);
    if (n.cost < 0) add('error', _('"{title}" ({id}) has a negative cost.', { title: n.title, id: n.id }), n.id);
    if (n.kind === 'and' && n.require > 0 && incoming(t, n.id).size < n.require) add('error', _('"{title}" ({id}) requires {req} neighbors but only has {have} incoming links.', { title: n.title, id: n.id, req: n.require, have: incoming(t, n.id).size }), n.id);
    if (!/^[a-z0-9_.:\/-]+$/.test(n.icon) || !n.icon.includes(':')) add('warn', _('Doubtful icon format in {id}: "{icon}" (use mod:item).', { id: n.id, icon: n.icon }), n.id);
    if (S.tex.size && !S.tex.has(n.icon) && !n.icon.startsWith('minecraft:')) add('info', _('Icon "{icon}" ({id}) is not in the loaded textures.', { icon: n.icon, id: n.id }), n.id);
    for (const a of n.attributes) {
      if (a.condition) condProblems(a.condition).forEach(m => add('error', _('Attribute condition in {id}: {m}', { id: n.id, m }), n.id));
      if (!attrInfo(a.attribute)) add('info', _('Attribute "{attr}" ({id}) is not in the library: make sure its mod is installed.', { attr: a.attribute, id: n.id }), n.id);
      if (!Number.isFinite(a.amount)) add('error', _('Invalid amount in {id} ({attr}).', { id: n.id, attr: a.attribute }), n.id);
    }
    for (const c of n.commands) if (c.startsWith('/')) add('info', _('Command with a leading "/" in {id}: the slash is ignored.', { id: n.id }), n.id);
  }
  // nodes too close together
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i], b = nodes[j];
      if (Math.hypot(a.x - b.x, a.y - b.y) < 24) { add('warn', _('"{a}" and "{b}" overlap.', { a: a.id, b: b.id }), a.id); break; }
    }
  }
  for (const l of t.links.values()) {
  }
  return P;
}


/* ---------- mirror: keeping the copies in step ----------
   Any tool that moves or bends the selection calls armMirror(nodes) first. It remembers which
   node is the mirrored copy of which (while they still line up) and, when the tool commits,
   moves the copies to the mirrored new positions and copies the bends of the links. */
function armMirror(nodes) {
  const t = S.cur; S.mirrorJob = null;
  if (!t || mirrorCount() < 2 || !nodes || !nodes.length) return;
  const sel = new Set(nodes), pairs = [], lostIds = new Set();
  for (const n of nodes) for (let k = 1; k < mirrorCount(); k++) {
    const [x, y] = mirrorApply(k, n.x, n.y), m = nodeNear(t, x, y, n);
    if (!m) lostIds.add(n.id); else if (!sel.has(m)) pairs.push({ n, k, m });
  }
  const lpairs = [];
  for (const l of t.links.values()) {
    const a = t.nodes.get(l.from), b = t.nodes.get(l.to);
    if (!a || !b || (!sel.has(a) && !sel.has(b))) continue;
    for (let k = 1; k < mirrorCount(); k++) {
      const pa = mirrorApply(k, a.x, a.y), pb = mirrorApply(k, b.x, b.y);
      const ma = nodeNear(t, pa[0], pa[1]), mb = nodeNear(t, pb[0], pb[1]);
      if (!ma || !mb || ma === mb || sel.has(ma) && sel.has(mb)) continue;
      const ml = t.links.get(linkKey(ma.id, mb.id));
      if (ml && ml !== l) lpairs.push({ l, ml, sign: (mirrorIsReflection(k) ? -1 : 1) * (ml.from === ma.id ? 1 : -1) });
    }
  }
  S.mirrorJob = () => {
    for (const { n, k, m } of pairs) { const [x, y] = mirrorApply(k, n.x, n.y); m.x = Math.round(x * 10) / 10; m.y = Math.round(y * 10) / 10; }
    for (const { l, ml, sign } of lpairs) { if (l.curve) ml.curve = l.curve * sign; else delete ml.curve; }
    if (lostIds.size) toast(_('Mirror: {n} mirrored copies were not found, so they did not move. Use "Repair mirror" or check the status bar.', { n: lostIds.size }));
  };
}
/** Nodes that have no mirrored copy at one of the mirror positions (fast: spatial hash). */
function mirrorOrphans(t) {
  if (!t || mirrorCount() < 2) return [];
  const cell = 8, grid = new Map(), key = (x, y) => Math.floor(x / cell) + ',' + Math.floor(y / cell);
  for (const n of t.nodes.values()) { const k = key(n.x, n.y); (grid.get(k) || grid.set(k, []).get(k)).push(n); }
  const near = (x, y, self) => {
    const cx = Math.floor(x / cell), cy = Math.floor(y / cell);
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) for (const m of grid.get((cx + i) + ',' + (cy + j)) || [])
      if (m !== self && Math.hypot(m.x - x, m.y - y) <= MIRROR_TOL) return true;
    return false;
  };
  const out = [];
  for (const n of t.nodes.values()) {
    if (Math.hypot(n.x, n.y) < 0.5) continue;   // the centre is its own copy
    for (let k = 1; k < mirrorCount(); k++) { const [x, y] = mirrorApply(k, n.x, n.y); if (Math.hypot(x - n.x, y - n.y) > MIRROR_TOL && !near(x, y, n)) { out.push(n); break; } }
  }
  return out;
}
/** Id shape shared by mirrored copies made by tools/scripts: "soldier_b0t" ~ "mage_b0t". Tries shorter tails. */
function idTails(id) { const p = id.split('_'), out = []; for (let i = 1; i < Math.min(p.length, 4); i++) out.push(p.slice(i).join('_')); return out; }
/** Moves the mirrored copies of the selected nodes back to the mirrored positions.
 *  Copies are found by position, or by id (same name after the class prefix) when they drifted away. */
function repairMirror() {
  const t = S.cur;
  if (!t || mirrorCount() < 2) return toast(_('Turn on Mirror first.'));
  const ns = selNodes();
  if (!ns.length) return toast(_('Select the nodes whose position is correct (e.g. one whole class) first.'));
  const sel = new Set(ns), used = new Set(), byTail = new Map();
  for (const m of t.nodes.values()) if (!sel.has(m)) for (const tl of idTails(m.id)) (byTail.get(tl) || byTail.set(tl, []).get(tl)).push(m);
  let moved = 0;
  for (const n of ns) for (let k = 1; k < mirrorCount(); k++) {
    const [x, y] = mirrorApply(k, n.x, n.y);
    let m = nodeNear(t, x, y, n);
    if (!m) {
      const lim = Math.max(300, 0.35 * Math.hypot(n.x, n.y));
      for (const tl of idTails(n.id)) {
        let best = null, bd = lim;
        for (const c of byTail.get(tl) || []) { if (used.has(c)) continue; const d = Math.hypot(c.x - x, c.y - y); if (d < bd) { bd = d; best = c; } }
        if (best) { m = best; break; }
      }
    }
    if (!m || sel.has(m)) continue;
    used.add(m);
    if (Math.hypot(m.x - x, m.y - y) > 0.05) { m.x = Math.round(x * 10) / 10; m.y = Math.round(y * 10) / 10; moved++; }
  }
  armMirror(ns);   // bends of the links follow too
  commit();
  toast(_('Mirror repaired: {n} copies moved.', { n: moved }));
}
/** With Mirror on: gives freshly created nodes (ids) their mirrored copies, with the links between them
 *  and to existing nodes whose mirror exists. Returns the new copies. */
function replicateMirrored(t, ids) {
  if (mirrorCount() < 2 || !ids.length) return [];
  const made = [], fresh = new Set(ids);
  for (let k = 1; k < mirrorCount(); k++) {
    const map = new Map();
    for (const id of ids) {
      const n = t.nodes.get(id), [x, y] = mirrorApply(k, n.x, n.y);
      if (nodeNear(t, x, y, n)) continue;
      const c = newNode(freshId(t), Math.round(x * 10) / 10, Math.round(y * 10) / 10, cloneProps(n));
      c.start = n.start; t.nodes.set(c.id, c); map.set(id, c.id); made.push(c.id);
    }
    for (const l of [...t.links.values()]) {
      if (!fresh.has(l.from) && !fresh.has(l.to)) continue;
      const pick = id => {
        if (map.has(id)) return map.get(id);
        const o = t.nodes.get(id), [x, y] = mirrorApply(k, o.x, o.y), m = nodeNear(t, x, y);
        return m ? m.id : null;
      };
      const a = pick(l.from), b = pick(l.to);
      if (!a || !b || a === b || t.links.has(linkKey(a, b))) continue;
      const nl = Object.assign({}, l, { from: a, to: b }); if (nl.curve && mirrorIsReflection(k)) nl.curve = -nl.curve;
      t.links.set(linkKey(a, b), nl);
    }
  }
  return made;
}
