/* ============================================================
   Waifing Skilltree Editor — core: model, libraries, files, zip
   ============================================================ */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const RADIUS = { small: 13, medium: 19, large: 27 };
const HAS_FS = typeof window.showDirectoryPicker === 'function';

/* ---------- global state ---------- */
const S = {
  searchQ: '', srch: new Set(),
  trees: [],            // [{id, ns, name, title, icon, background, nodes:Map, links:Map, dirty, history:[], future:[]}]
  cur: null,            // active tree
  root: null,           // FileSystemDirectoryHandle of the datapack (folder mode)
  rootHasMeta: false,
  deleted: [],          // deleted trees waiting to be removed from disk [{ns,name}]
  sel: new Set(),       // selected ids
  tool: 'select',
  view: { x: 0, y: 0, z: 1 },
  libs: { attrs: [], items: [] },
  tex: new Map(),       // "ns:name" -> HTMLImageElement
  hover: null,
};

/* ---------- model helpers ---------- */
const linkKey = (a, b) => (a < b ? a + '|' + b : b + '|' + a);
const sanitize = s => (s || '').toLowerCase().replace(/[^a-z0-9_]/g, '_') || 'tree';
/** namespace: lowercase letters, digits, _ . - */
const sanitizeNs = s => (s || '').toLowerCase().replace(/[^a-z0-9_.-]/g, '_') || 'my_pack';
/** file name inside waifingskilltree/trees; may contain sub-folders ("a/b") */
const sanitizePath = s => (s || '').toLowerCase().replace(/\\/g, '/').replace(/[^a-z0-9_.\/-]/g, '_').replace(/\/+/g, '/').replace(/^\/|\/$/g, '') || 'tree';
/** id the mod gives the tree in game (must match TreeReloadListener / TreeStorage.sanitizeId) */
const gameId = (ns, name) => (ns + '_' + name).toLowerCase().replace(/[^a-z0-9_]/g, '_');

function newNode(id, x, y, props = {}) {
  return Object.assign({
    id, x, y, title: 'Node', description: [], icon: 'minecraft:paper', shape: 'small', kind: 'normal',
    require: 2, cost: 1, color: '', requires_advancements: [], attributes: [], commands: [], refund_commands: [],
    start: false, max_level: 1, cost_step: 0, requires_prev_level: 1, requires_items: [], requires_attributes: [], recipes: [], unlock_commands: [],
    click_recipes: [], unlock_trees: [], group: '', group_bonus: {}, requires_nodes: [], requires_count: 0, free_choice_if: [], cap_level: 0, cap_nodes: [], cap_count: 0,
  }, JSON.parse(JSON.stringify(props)), { id, x, y });
}
const cloneProps = n => JSON.parse(JSON.stringify({
  title: n.title, description: n.description, icon: n.icon, shape: n.shape, kind: n.kind, require: n.require,
  cost: n.cost, color: n.color, requires_advancements: n.requires_advancements, attributes: n.attributes,
  commands: n.commands, refund_commands: n.refund_commands, start: n.start, max_level: n.max_level, cost_step: n.cost_step,
  requires_prev_level: n.requires_prev_level, requires_items: n.requires_items, requires_attributes: n.requires_attributes, recipes: n.recipes, unlock_commands: n.unlock_commands,
  click_recipes: n.click_recipes, unlock_trees: n.unlock_trees, group: n.group, group_bonus: n.group_bonus,
  requires_nodes: n.requires_nodes, requires_count: n.requires_count, free_choice_if: n.free_choice_if, cap_level: n.cap_level, cap_nodes: n.cap_nodes, cap_count: n.cap_count,
}));

function makeTree(ns, name, title) {
  return { id: gameId(ns, name), ns, name, title: title || name, icon: 'minecraft:nether_star',
    background: 'minecraft:textures/gui/advancements/backgrounds/stone.png', exclusive_group: '', group_limits: {}, choice_groups: {}, choice_limits: {}, first_start_only: false,
    nodes: new Map(), links: new Map(), dirty: false, history: [], future: [], handle: null, dirHandle: null };
}

function treeFromJSON(json, ns, name) {
  const t = makeTree(ns, name, json.title);
  t.icon = json.icon || t.icon;
  t.background = json.background || t.background;
  t.exclusive_group = (json.exclusive_group || '').trim();
  t.first_start_only = !!json.first_start_only;
  t.group_limits = Object.assign({}, json.group_limits || {});
  t.choice_groups = Object.assign({}, json.choice_groups || {});
  t.choice_limits = Object.assign({}, json.choice_limits || {});
  for (const raw0 of json.nodes || []) {
    const raw = { ...raw0 };
    // list fields saved as one text (an older editor bug): split them back into lines
    for (const k of ['description', 'commands', 'refund_commands', 'requires_advancements', 'requires_nodes', 'free_choice_if', 'recipes', 'click_recipes', 'unlock_commands', 'unlock_trees'])
      if (typeof raw[k] === 'string') raw[k] = raw[k].split('\n').map(x => x.trim()).filter(Boolean);
    const n = newNode(raw.id, raw.x, raw.y);
    n.title = raw.title ?? n.title;
    n.description = raw.description || [];
    n.icon = raw.icon || n.icon;
    n.shape = { notable: 'medium', keystone: 'large' }[raw.shape] || raw.shape || (raw.start ? 'large' : 'small');
    n.kind = raw.kind === 'and' ? 'and' : 'normal';   // old "wormhole" nodes are normal: a long link already looks like a portal
    n.require = raw.require ?? 2;
    n.cost = raw.cost ?? 1;
    n.color = raw.color || '';
    n.requires_advancements = raw.requires_advancements || [];
    n.requires_nodes = raw.requires_nodes || [];
    n.requires_count = raw.requires_count || 0;
    n.free_choice_if = raw.free_choice_if || [];
    const cap = raw.level_cap || {};
    n.cap_level = cap.level || 0;
    n.cap_nodes = typeof cap.requires_nodes === 'string' ? cap.requires_nodes.split('\n').map(x => x.trim()).filter(Boolean) : (cap.requires_nodes || []);
    n.cap_count = cap.requires_count || 0;
    n.attributes = (raw.attributes || []).map(a => ({ attribute: a.attribute, amount: Number(a.amount), operation: a.operation ?? 'add', ...(a.condition ? { condition: a.condition } : {}) }));
    n.commands = raw.commands || [];
    n.refund_commands = raw.refund_commands || [];
    // legacy trees have no "start": large nodes were the starts
    n.start = raw.start !== undefined ? !!raw.start : n.shape === 'large';
    n.max_level = Math.max(1, raw.max_level ?? 1);
    n.cost_step = raw.cost_step ?? 0;
    n.requires_prev_level = Math.max(1, raw.requires_prev_level ?? 1);
    n.requires_items = (raw.requires_items || []).map(i => (typeof i === 'string' ? { item: i, count: 1 } : { item: i.item, count: Math.max(1, Number(i.count) || 1) }));
    n.requires_attributes = (raw.requires_attributes || []).map(a => ({ attribute: a.attribute, amount: Number(a.amount) || 0 }));
    n.recipes = raw.recipes || [];
    n.unlock_commands = raw.unlock_commands || [];
    n.click_recipes = raw.click_recipes || [];
    n.unlock_trees = raw.unlock_trees || [];
    n.group = (raw.group || '').trim();
    n.group_bonus = Object.assign({}, raw.group_bonus || {});
    t.nodes.set(n.id, n);
  }
  for (const l of json.links || []) {
    if (t.nodes.has(l[0]) && t.nodes.has(l[1])) t.links.set(linkKey(l[0], l[1]), { from: l[0], to: l[1], type: l[2] === 'oneway' || l[2] === 'choice' ? l[2] : 'normal', curve: typeof l[3] === 'number' ? l[3] : 0, min_level: typeof l[4] === 'number' ? l[4] : 0 });
  }
  return t;
}

function treeToJSON(t) {
  const nodes = [...t.nodes.values()].map(n => {
    const o = { id: n.id, x: round1(n.x), y: round1(n.y), title: n.title, description: n.description, icon: n.icon, shape: n.shape, start: !!n.start };
    if (n.kind !== 'normal') o.kind = n.kind;
    if (n.kind === 'and') o.require = n.require;
    o.cost = n.cost;
    if (n.max_level > 1) o.max_level = n.max_level;
    if (n.cost_step) o.cost_step = n.cost_step;
    if (n.requires_prev_level > 1) o.requires_prev_level = n.requires_prev_level;
    if (n.requires_items.length) o.requires_items = n.requires_items.map(i => ({ item: i.item, count: i.count }));
    if (n.requires_attributes.length) o.requires_attributes = n.requires_attributes.map(a => ({ attribute: a.attribute, amount: a.amount }));
    if (n.recipes.length) o.recipes = n.recipes;
    if (n.unlock_commands.length) o.unlock_commands = n.unlock_commands;
    if (n.click_recipes.length) o.click_recipes = n.click_recipes;
    if (n.unlock_trees.length) o.unlock_trees = n.unlock_trees;
    if (n.group) o.group = n.group;
    if (Object.keys(n.group_bonus || {}).length) o.group_bonus = n.group_bonus;
    if (n.color) o.color = n.color;
    if (n.requires_advancements.length) o.requires_advancements = n.requires_advancements;
    if ((n.requires_nodes || []).length) { o.requires_nodes = n.requires_nodes; if (n.requires_count > 0) o.requires_count = n.requires_count; }
    if ((n.free_choice_if || []).length) o.free_choice_if = n.free_choice_if;
    if (n.cap_level > 0 && (n.cap_nodes || []).length) o.level_cap = { level: n.cap_level, requires_nodes: n.cap_nodes, ...(n.cap_count > 0 ? { requires_count: n.cap_count } : {}) };
    o.attributes = n.attributes.map(a => ({ attribute: a.attribute, amount: a.amount, operation: a.operation, ...(a.condition ? { condition: a.condition } : {}) }));
    o.commands = n.commands;
    o.refund_commands = n.refund_commands;
    return o;
  });
  const links = [...t.links.values()].map(l => (l.min_level > 0 ? [l.from, l.to, l.type, l.curve || 0, l.min_level] : l.curve ? [l.from, l.to, l.type, l.curve] : l.type === 'normal' ? [l.from, l.to] : [l.from, l.to, l.type]));
  return { title: t.title, icon: t.icon, background: t.background, ...(t.exclusive_group ? { exclusive_group: t.exclusive_group } : {}), ...(Object.keys(t.group_limits || {}).length ? { group_limits: t.group_limits } : {}), ...(choiceGroupsOut(t)), ...(t.first_start_only ? { first_start_only: true } : {}), nodes, links };
}
const round1 = v => Math.round(v * 10) / 10;
const treeJSONText = t => JSON.stringify(treeToJSON(t), null, 2) + '\n';

function freshId(t) {
  let i = t.nodes.size;
  while (t.nodes.has('n' + i)) i++;
  return 'n' + i;
}

/* ---------- graph (same rules as the mod) ---------- */
/** One way and choice links are followed only from A to B. */
const isDirected = l => l.type === 'oneway' || l.type === 'choice';
function incoming(t, id) {
  const out = new Set();
  for (const l of t.links.values()) {
    if (l.from !== id && l.to !== id) continue;
    if (isDirected(l) && l.to !== id) continue;
    out.add(l.from === id ? l.to : l.from);
  }
  return out;
}
function neighbors(t, id) {
  const out = new Set();
  for (const l of t.links.values()) if (l.from === id) out.add(l.to); else if (l.to === id) out.add(l.from);
  return out;
}
function outgoing(t, id) {
  const out = new Set();
  for (const l of t.links.values()) {
    if (l.from !== id && l.to !== id) continue;
    if (isDirected(l) && l.from !== id) continue;
    out.add(l.from === id ? l.to : l.from);
  }
  return out;
}
/** How a link looks depends on its length (same as the game): from BRIDGE_LEN a silver jump, from PORTAL_LEN a violet portal. */
const BRIDGE_LEN = 250, PORTAL_LEN = 500;
function linkLen(t, l) { const a = t.nodes.get(l.from), b = t.nodes.get(l.to); return a && b ? Math.hypot(b.x - a.x, b.y - a.y) : 0; }
const linkLook = (t, l) => { const d = linkLen(t, l) + 0.5; return d >= PORTAL_LEN ? 'portal' : d >= BRIDGE_LEN ? 'bridge' : 'line'; };
/** Groups a node can raise: the limited groups, plus every choice ("choice:<origin id>", 1 pick, +1 per bonus). */
function bonusTargets(t) {
  const out = Object.keys(t.group_limits || {}).map(g => [g, g + ' (max ' + t.group_limits[g] + ')', 'node']);
  for (const f of choiceForks(t)) out.push(['choice:' + f.id, _('Choice: {t}', { t: f.title }), 'link']);
  for (const g of choiceGroupIds(t)) out.push(['choices:' + g, _('Choice group: {g} (shared)', { g }), 'link']);
  return out;
}
/** Choice groups in use: ids of choice_groups whose origin still has choice links. */
function choiceGroupIds(t) {
  const forks = new Set(choiceForks(t).map(f => f.id));
  return [...new Set(Object.entries(t.choice_groups || {}).filter(([o, g]) => g && forks.has(o)).map(([, g]) => g))];
}
/** choice_groups for saving, without entries of origins that no longer have choices. */
function choiceGroupsOut(t) {
  const forks = new Set(choiceForks(t).map(f => f.id)), o = {};
  for (const [k, g] of Object.entries(t.choice_groups || {})) if (g && forks.has(k)) o[k] = g;
  const lim = {};
  for (const [k, n] of Object.entries(t.choice_limits || {})) if (forks.has(k) && n !== 1) lim[k] = n;
  return { ...(Object.keys(o).length ? { choice_groups: o } : {}), ...(Object.keys(lim).length ? { choice_limits: lim } : {}) };
}
/** Every choice in the tree: origin node, its options and the nodes that give one more pick. */
function choiceForks(t) {
  const m = new Map();
  for (const l of t.links.values()) if (l.type === 'choice') (m.get(l.from) || m.set(l.from, []).get(l.from)).push(l.to);
  return [...m].map(([id, opts]) => ({ id, title: t.nodes.get(id)?.title || id, options: opts, group: (t.choice_groups || {})[id] || '', limit: (t.choice_limits || {})[id] ?? 1,
    bonus: [...t.nodes.values()].filter(n => (n.group_bonus || {})['choice:' + id]).map(n => [n.title, n.group_bonus['choice:' + id]]) }));
}
const hasPortal = (t, id) => [...t.links.values()].some(l => (l.from === id || l.to === id) && linkLook(t, l) === 'portal');
function removeNode(t, id) {
  t.nodes.delete(id);
  for (const [k, l] of [...t.links]) if (l.from === id || l.to === id) t.links.delete(k);
  S.sel.delete(id);
}

/* ---------- libraries (attributes / items / advancements) ---------- */
const VANILLA_ATTRS = [
  ['minecraft:generic.max_health', 'Max health', 2, 'add'], ['minecraft:generic.attack_damage', 'Attack damage', 1, 'add'],
  ['minecraft:generic.attack_speed', 'Attack speed', 0.1, 'mult_base'], ['minecraft:generic.attack_knockback', 'Attack knockback', 0.5, 'add'],
  ['minecraft:generic.movement_speed', 'Movement speed', 0.05, 'mult_base'], ['minecraft:generic.armor', 'Armor', 1, 'add'],
  ['minecraft:generic.armor_toughness', 'Armor toughness', 1, 'add'], ['minecraft:generic.knockback_resistance', 'Knockback resistance', 0.1, 'add'],
  ['minecraft:generic.luck', 'Luck', 1, 'add'], ['minecraft:generic.flying_speed', 'Flying speed', 0.05, 'mult_base'],
  ['minecraft:generic.follow_range', 'Follow range', 4, 'add'],
  ['forge:swim_speed', 'Swim speed (Forge)', 0.1, 'mult_base'], ['forge:entity_gravity', 'Gravity (Forge)', -0.01, 'add'],
  ['forge:step_height_addition', 'Step height (Forge)', 0.5, 'add'], ['forge:block_reach', 'Block reach (Forge)', 1, 'add'],
  ['forge:entity_reach', 'Entity reach (Forge)', 1, 'add'], ['forge:nametag_distance', 'Name tag distance (Forge)', 8, 'add'],
].map(([id, name, amount, operation]) => ({ id, name, amount, operation }));

const VANILLA_ITEMS = ('paper stone iron_sword diamond_sword netherite_sword wooden_sword bow crossbow trident shield book enchanted_book golden_apple ' +
  'enchanted_golden_apple apple bread cooked_beef feather rabbit_foot nether_star ender_pearl ender_eye amethyst_shard emerald diamond gold_ingot iron_ingot ' +
  'netherite_ingot redstone redstone_torch comparator repeater lapis_lazuli experience_bottle potion totem_of_undying elytra firework_rocket compass clock ' +
  'map spyglass heart_of_the_sea nautilus_shell blaze_rod ghast_tear dragon_breath shulker_shell phantom_membrane iron_chestplate diamond_chestplate ' +
  'iron_helmet iron_boots iron_pickaxe diamond_pickaxe iron_axe fishing_rod bone skeleton_skull wither_skeleton_skull creeper_head gunpowder sugar ' +
  'glow_berries sweet_berries honey_bottle spider_eye fermented_spider_eye magma_cream slime_ball echo_shard disc_fragment_5 recovery_compass bundle ' +
  'name_tag saddle lead barrier torch lantern campfire anvil enchanting_table brewing_stand cauldron').split(' ').map(s => 'minecraft:' + s);

const VANILLA_ADVS = ['minecraft:story/mine_stone', 'minecraft:story/upgrade_tools', 'minecraft:story/smelt_iron', 'minecraft:story/obtain_armor',
  'minecraft:story/enter_the_nether', 'minecraft:story/follow_ender_eye', 'minecraft:story/enter_the_end', 'minecraft:nether/find_fortress',
  'minecraft:nether/obtain_blaze_rod', 'minecraft:nether/create_beacon', 'minecraft:end/kill_dragon', 'minecraft:end/elytra',
  'minecraft:adventure/kill_a_mob', 'minecraft:adventure/trade', 'minecraft:adventure/totem_of_undying', 'minecraft:adventure/hero_of_the_village',
  'minecraft:husbandry/breed_an_animal', 'minecraft:husbandry/balanced_diet', 'minecraft:husbandry/tame_an_animal', 'minecraft:nether/all_effects'];

function loadLibs() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem('wse_libs') || 'null'); } catch (e) { /* storage unavailable */ }
  S.libs.attrs = saved?.attrs?.length ? saved.attrs : JSON.parse(JSON.stringify(VANILLA_ATTRS));
  S.libs.items = saved?.items?.length ? saved.items : [...VANILLA_ITEMS];
  refreshDatalists();
}
function saveLibs() {
  try { localStorage.setItem('wse_libs', JSON.stringify({ attrs: S.libs.attrs, items: S.libs.items })); } catch (e) { /* ignore */ }
}
function refreshDatalists() {
  $('#attrList').innerHTML = S.libs.attrs.map(a => `<option value="${esc(a.id)}">${esc(_(a.name || ''))}</option>`).join('');
  $('#itemList').innerHTML = S.libs.items.slice(0, 4000).map(i => `<option value="${esc(i)}"></option>`).join('');
  $('#advList').innerHTML = VANILLA_ADVS.map(a => `<option value="${a}"></option>`).join('');
}
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const OPS = { add: 'add', mult_base: 'mult_base', mult_total: 'mult_total', multiply_base: 'mult_base', multiply_total: 'mult_total', addition: 'add', 0: 'add', 1: 'mult_base', 2: 'mult_total' };
const normId = s => { s = String(s).trim(); return s.includes(':') ? s : 'minecraft:' + s; };

/** Accepts the attribute import formats and returns [{id,name,amount,operation}] */
function parseAttrLib(text) {
  let data = JSON.parse(text);
  // grouped by mod: {"mod": [{id, description, kind, max...}], ...} (keys starting with "_" are notes)
  if (!Array.isArray(data) && !data.attributes && Object.entries(data).some(([k, v]) => !k.startsWith('_') && Array.isArray(v))) {
    const DEF = { switch: [1, 'add'], percent: [0.05, 'add'], amount: [1, 'add'], tier_chance: [1, 'add'] };
    data = Object.entries(data).filter(([k, v]) => !k.startsWith('_') && Array.isArray(v)).flatMap(([, v]) => v).filter(e => e && e.id).map(e => {
      const d = DEF[e.kind] || [1, 'add'];
      return { id: e.id, name: e.name || e.description || '', amount: e.amount ?? d[0], operation: e.operation || d[1] };
    });
  }
  const list = Array.isArray(data) ? data : (data.attributes || Object.entries(data).map(([id, v]) => (typeof v === 'string' ? { id, name: v } : { id, ...v })));
  return list.map(e => (typeof e === 'string' ? { id: normId(e) } : { id: normId(e.id), name: e.name || '', amount: e.amount ?? 1, operation: OPS[e.operation] || 'add' }))
    .map(e => ({ id: e.id, name: e.name || '', amount: e.amount ?? 1, operation: e.operation || 'add' }));
}
function parseItemLib(text) {
  text = text.trim();
  if (!text) return [];
  let list;
  if (text[0] === '[' || text[0] === '{') {
    const d = JSON.parse(text);
    list = Array.isArray(d) ? d.map(x => (typeof x === 'string' ? x : x.id)) : (d.items || Object.keys(d));
  } else list = text.split(/\r?\n/);
  return [...new Set(list.map(s => s.trim()).filter(Boolean).map(normId))];
}
/** Condition types: [id, label, parameter kind (none|num|item|text), placeholder] */
const COND_TYPES = [
  ['main_hand', 'Main hand holds', 'item', 'minecraft:diamond_sword or #minecraft:swords (empty = any item)'],
  ['off_hand', 'Off hand holds', 'item', 'minecraft:shield (empty = any item)'],
  ['helmet', 'Helmet', 'item', 'minecraft:iron_helmet (empty = any helmet)'],
  ['chestplate', 'Chestplate', 'item', 'minecraft:iron_chestplate (empty = any)'],
  ['leggings', 'Leggings', 'item', 'minecraft:iron_leggings (empty = any)'],
  ['boots', 'Boots', 'item', 'minecraft:iron_boots (empty = any)'],
  ['full_armor', 'Full armor set', 'text', 'material, e.g. diamond (empty = any 4 pieces)'],
  ['day', 'Daytime', 'none'], ['night', 'Night', 'none'],
  ['health_at_most', 'Health at most (%)', 'num', '30'], ['health_at_least', 'Health at least (%)', 'num', '90'],
  ['food_at_most', 'Hunger bar at most (0-20)', 'num', '6'], ['food_at_least', 'Hunger bar at least (0-20)', 'num', '18'],
  ['light_at_most', 'Light level at most (0-15)', 'num', '4'], ['light_at_least', 'Light level at least (0-15)', 'num', '12'],
  ['y_at_most', 'Height Y at most', 'num', '0'], ['y_at_least', 'Height Y at least', 'num', '100'],
  ['in_water', 'In water', 'none'], ['sneaking', 'Sneaking', 'none'], ['sprinting', 'Sprinting', 'none'],
  ['on_fire', 'On fire', 'none'], ['raining', 'Raining on you', 'none'], ['underground', 'Underground (no sky)', 'none'],
  ['dimension', 'Dimension', 'text', 'minecraft:the_nether'],
  ['biome', 'Biome', 'text', 'minecraft:desert or #minecraft:is_forest'],
  ['effect', 'Has effect', 'text', 'minecraft:speed'],
  ['beneficial_effects', 'Beneficial effects active (min., empty = 1)', 'num', '1'],
  ['harmful_effects', 'Harmful effects active (min., empty = 1)', 'num', '1'],
  ['two_handed', 'Two-handed weapon (Better Combat)', 'none'],
  ['weapon_category', 'Weapon category in main hand (Better Combat)', 'text', 'claymore, hammer, double_axe'],
  ['off_hand_category', 'Weapon category in off hand (Better Combat)', 'text', 'dagger'],
  ['dual_wield', 'Dual wielding (empty = any one-handed weapons)', 'text', 'dagger, sword'],
  ['curio', 'Curio equipped (Curios)', 'text', 'spellbook  or  ring=minecraft:gold_ingot  (empty = any curio)'],
  ['enchanted_main_hand', 'Enchanted weapon (min. enchantments; Tinkers modifiers count)', 'num', '1'],
  ['enchanted_armor', 'Enchanted armor pieces (min.; Tinkers modifiers count)', 'num', '1'],
];
const COND_KIND = Object.fromEntries(COND_TYPES.map(c => [c[0], c[2]]));
const attrInfo = id => S.libs.attrs.find(a => a.id === id);

/* ---------- minimal ZIP writer ("store" method, no dependencies) ---------- */
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xFFFFFFFF; for (let i = 0; i < b.length; i++) c = CRC_TABLE[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
function makeZip(files) { // files: [{name, text}]
  const enc = new TextEncoder(), parts = [], central = [];
  const d = new Date();
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
  const date = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  let offset = 0;
  for (const f of files) {
    const name = enc.encode(f.name), data = enc.encode(f.text), crc = crc32(data);
    const lh = new DataView(new ArrayBuffer(30));
    lh.setUint32(0, 0x04034b50, true); lh.setUint16(4, 20, true); lh.setUint16(6, 0x0800, true); lh.setUint16(8, 0, true);
    lh.setUint16(10, time, true); lh.setUint16(12, date, true); lh.setUint32(14, crc, true);
    lh.setUint32(18, data.length, true); lh.setUint32(22, data.length, true); lh.setUint16(26, name.length, true); lh.setUint16(28, 0, true);
    parts.push(new Uint8Array(lh.buffer), name, data);
    const ch = new DataView(new ArrayBuffer(46));
    ch.setUint32(0, 0x02014b50, true); ch.setUint16(4, 20, true); ch.setUint16(6, 20, true); ch.setUint16(8, 0x0800, true);
    ch.setUint16(12, time, true); ch.setUint16(14, date, true); ch.setUint32(16, crc, true);
    ch.setUint32(20, data.length, true); ch.setUint32(24, data.length, true); ch.setUint16(28, name.length, true); ch.setUint32(42, offset, true);
    central.push(new Uint8Array(ch.buffer), name);
    offset += 30 + name.length + data.length;
  }
  const csize = central.reduce((s, p) => s + p.length, 0);
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
  end.setUint32(12, csize, true); end.setUint32(16, offset, true);
  return new Blob([...parts, ...central, new Uint8Array(end.buffer)], { type: 'application/zip' });
}
function download(name, blobOrText, type = 'application/json') {
  const blob = blobOrText instanceof Blob ? blobOrText : new Blob([blobOrText], { type });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
}
const PACK_MCMETA = JSON.stringify({ pack: { description: 'Skill trees', pack_format: 15 } }, null, 2) + '\n';
const treePath = t => `data/${t.ns}/waifingskilltree/trees/${t.name}.json`;
/** data/<ns>/waifingskilltree/trees as a folder handle (create = make it when missing). */
async function treesDir(ns, create) {
  const mod = await ns.getDirectoryHandle('waifingskilltree', create ? { create: true } : undefined);
  return mod.getDirectoryHandle('trees', create ? { create: true } : undefined);
}

/* ---------- reading / writing datapacks ---------- */
async function readDatapackDir(root) {
  const found = [];
  let data = null;
  try { data = await root.getDirectoryHandle('data'); } catch (e) { if (root.name === 'data') data = root; }
  if (!data) throw new Error(_('No "data" folder found inside the chosen folder.'));
  for await (const [nsName, nsHandle] of data.entries()) {
    if (nsHandle.kind !== 'directory') continue;
    let base;
    try { base = await treesDir(nsHandle, false); } catch (e) { continue; }
    const walk = async (dir, prefix) => {
      for await (const [fname, fh] of dir.entries()) {
        if (fh.kind === 'directory') { await walk(fh, prefix + fname + '/'); continue; }
        if (!fname.endsWith('.json')) continue;
        try {
          const text = await (await fh.getFile()).text();
          const t = treeFromJSON(JSON.parse(text), nsName, prefix + fname.slice(0, -5));
          t.handle = fh; t.dirHandle = dir;
          found.push(t);
        } catch (e) { toast(_('Could not read {file}: {err}', { file: nsName + '/' + prefix + fname, err: e.message })); }
      }
    };
    await walk(base, '');
  }
  return { data, found, dataIsRoot: data === root };
}

async function writeFile(dir, name, text) {
  const fh = await dir.getFileHandle(name, { create: true });
  const w = await fh.createWritable();
  await w.write(text);
  await w.close();
  return fh;
}

async function saveToFolder() {
  const root = S.root;
  if (!root) return false;
  const dataIsRoot = root.name === 'data' && !S.rootHasMeta;
  let packDir = root;
  if (!S.rootHasMeta && !dataIsRoot) {
    try { await root.getFileHandle('pack.mcmeta'); } catch (e) { await writeFile(root, 'pack.mcmeta', PACK_MCMETA); }
    S.rootHasMeta = true;
  }
  const dataDir = dataIsRoot ? root : await packDir.getDirectoryHandle('data', { create: true });
  for (const t of S.trees) {
    if (!t.dirty && t.handle) continue;
    const ns = await dataDir.getDirectoryHandle(t.ns, { create: true });
    let dir = await treesDir(ns, true);
    const parts = t.name.split('/'), fileName = parts.pop() + '.json';
    for (const part of parts) dir = await dir.getDirectoryHandle(part, { create: true });
    t.handle = await writeFile(dir, fileName, treeJSONText(t));
    t.dirHandle = dir;
    t.dirty = false;
  }
  for (const d of S.deleted) {
    try {
      const ns = await dataDir.getDirectoryHandle(d.ns);
      let dir = await treesDir(ns, false);
      const parts = d.name.split('/'), fileName = parts.pop() + '.json';
      for (const part of parts) dir = await dir.getDirectoryHandle(part);
      await dir.removeEntry(fileName);
    } catch (e) { /* it no longer existed */ }
  }
  S.deleted = [];
  return true;
}

function exportZip() {
  const files = [{ name: 'pack.mcmeta', text: PACK_MCMETA }];
  for (const t of S.trees) files.push({ name: treePath(t), text: treeJSONText(t) });
  download('waifing_datapack.zip', makeZip(files), 'application/zip');
}

/* ---------- ZIP reader (stored + deflate) and tree discovery ---------- */
const TREE_RE = /(?:^|\/)data\/([^\/]+)\/waifingskilltree\/trees\/(.+)\.json$/i;
const MAX_ZIP = 300 * 1024 * 1024, MAX_ENTRY = 32 * 1024 * 1024;

async function inflateRaw(bytes) {
  if (typeof DecompressionStream !== 'function') {
    throw new Error(_('This browser cannot unzip compressed files. Update it, or extract the zip first.'));
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

/** Lists the .json entries of a .zip/.jar: [{path, read(): Promise<string>}]. Uses the central directory, so it
 *  works with zips made by Explorer, 7-Zip, Java (data descriptors) and with a top-level folder prefix. */
async function readZip(file) {
  if (file.size > MAX_ZIP) throw new Error(_('{name} is too big to open here.', { name: file.name }));
  const buf = new Uint8Array(await file.arrayBuffer()), dv = new DataView(buf.buffer);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error(_('{name} is not a valid zip file.', { name: file.name }));
  const count = dv.getUint16(eocd + 10, true);
  let off = dv.getUint32(eocd + 16, true);
  if (count === 0xFFFF || off === 0xFFFFFFFF) throw new Error(_('{name}: zip64 archives are not supported.', { name: file.name }));
  const dec = new TextDecoder(), out = [];
  for (let n = 0; n < count; n++) {
    if (off + 46 > buf.length || dv.getUint32(off, true) !== 0x02014b50) break;
    const flags = dv.getUint16(off + 8, true), method = dv.getUint16(off + 10, true);
    const csize = dv.getUint32(off + 20, true), usize = dv.getUint32(off + 24, true);
    const nlen = dv.getUint16(off + 28, true), elen = dv.getUint16(off + 30, true), clen = dv.getUint16(off + 32, true);
    const lho = dv.getUint32(off + 42, true);
    const path = dec.decode(buf.subarray(off + 46, off + 46 + nlen)).replace(/\\/g, '/');
    off += 46 + nlen + elen + clen;
    if (path.endsWith('/') || !/\.json$/i.test(path) || usize > MAX_ENTRY || (flags & 1)) continue;
    out.push({ path, read: async () => {
      if (dv.getUint32(lho, true) !== 0x04034b50) throw new Error(_('Corrupt zip entry.'));
      const start = lho + 30 + dv.getUint16(lho + 26, true) + dv.getUint16(lho + 28, true);
      const raw = buf.subarray(start, start + csize);
      if (method === 0) return new TextDecoder().decode(raw);
      if (method === 8) return new TextDecoder().decode(await inflateRaw(raw));
      throw new Error(_('Unsupported zip compression method.'));
    } });
  }
  return out;
}

/** items: [{path, file, loose}] (from file pickers, folders, drops). Finds every skill tree inside:
 *  data/<ns>/waifingskilltree/trees/*.json anywhere in a path (so zips with a top-level folder work), or loose .json trees. */
async function gatherTrees(items) {
  const found = [], notes = [];
  for (const it of items) {
    const isZip = /\.(zip|jar)$/i.test(it.path);
    if (isZip && !it.loose && (/\.jar$/i.test(it.path) || it.file.size > 20 * 1024 * 1024)) continue; // big folder scans: skip jars/huge zips
    let entries = [];
    try {
      if (isZip) entries = await readZip(it.file);
      else if (/\.json$/i.test(it.path)) entries = [{ path: it.path, read: () => it.file.text() }];
    } catch (e) { notes.push(it.file.name + ': ' + e.message); continue; }
    for (const e of entries) {
      const m = TREE_RE.exec(e.path);
      if (!m && (isZip || !it.loose)) continue;      // zips/folders: only real datapack paths
      try {
        const json = JSON.parse(await e.read());
        if (!json || !Array.isArray(json.nodes)) { if (m || it.loose) notes.push(_('{file}: not a skill tree (no "nodes" list).', { file: e.path })); continue; }
        let ns, name;
        if (m) { ns = sanitizeNs(m[1]); name = sanitizePath(m[2]); }
        else { ns = 'my_pack'; name = sanitizePath(e.path.split('/').pop().replace(/\.json$/i, '')); }
        found.push({ ns, name, json, nodes: json.nodes.length, source: isZip ? it.file.name : it.path });
      } catch (err) { notes.push(e.path + ': ' + err.message); }
    }
  }
  return { found, notes };
}
