/* ============================================================
   UI: tabs, inspector, files (zip / folder / drop), dialogs, startup
   ============================================================ */
function toast(msg, ms = 4800) {
  const d = document.createElement('div');
  d.className = 'toast'; d.textContent = msg; document.body.appendChild(d);
  setTimeout(() => d.remove(), ms);
}

const UI = {
  refreshAll() {   // one failing panel must not break the canvas (a crash here used to leave the view stuck dragging)
    for (const f of [UI.refreshTabs, UI.refreshInspector, UI.refreshStatus]) try { f(); } catch (e) { console.error(e); }
    requestDraw();
  },
  refreshStatus() {
    const t = S.cur;
    $('#stCount').textContent = t ? _('{n} nodes · {l} links · {s} starts', { n: t.nodes.size, l: t.links.size, s: [...t.nodes.values()].filter(n => n.start).length }) : '';
    $('#btnUndo').disabled = !t || t.history.length < 2;
    $('#btnRedo').disabled = !t || !t.future.length;
    const dirty = S.trees.some(x => x.dirty) || S.deleted.length;
    $('#stSave').textContent = dirty ? _('● unsaved changes') : _('✓ all saved');
    $('#stSave').style.color = dirty ? 'var(--warn)' : 'var(--ok)';
    const orph = mirrorOrphans(t), mb = $('#stMirror');
    mb.hidden = !orph.length;
    if (orph.length) { mb.textContent = '⚠ ' + _('Mirror: {n} nodes have no mirrored copy', { n: orph.length }); mb.title = _('Click to select them'); mb.onclick = () => { S.sel = new Set(orph.map(n => n.id)); UI.refreshInspector(); requestDraw(); }; }
    $('#stMode').textContent = S.root ? _('Folder: {name}', { name: S.root.name }) : (HAS_FS ? _('No folder open') : _('File mode (this browser cannot write to folders)'));
    $('#fileInfo').textContent = t ? _('in-game id: {id}', { id: gameId(t.ns, t.name) }) : '';
    $('#welcome').innerHTML = t && t.nodes.size ? '' : `<div style="font-size:18px;color:#c8ccd8">${esc(_('Empty tree'))}</div>${esc(_('Pick Add (A) and click to place nodes, or open your datapack.'))}`;
  },

  /* ----- tabs ----- */
  refreshTabs() {
    const el = $('#tabs');
    el.innerHTML = S.trees.map((t, i) => `<div class="tab ${t === S.cur ? 'active' : ''}" data-idx="${i}">${esc(t.title)} <span class="muted small">${esc(gameId(t.ns, t.name))}</span>${t.dirty ? '<span class="dot"></span>' : ''}</div>`).join('') +
      `<div class="tab add" id="tabAdd">${esc(_('＋ Tree'))}</div>`;
  },
  selectTree(t) {
    if (S.cur) S.cur.view = { ...S.view };
    S.cur = t; S.sel.clear(); linkFrom = null;
    if (t.view) S.view = { ...t.view }; else { resizeCanvas(); fitView(); }
    UI.refreshAll();
  },
  refreshInspector() {
    if (S.noInspRefresh) return;
    renderInspector();
  },
  save: saveAction,
};

/* ---------- inspector ---------- */
const selNodes = () => (S.cur ? [...S.sel].map(id => S.cur.nodes.get(id)).filter(Boolean) : []);
const common = (nodes, f) => { const v = nodes.map(n => JSON.stringify(n[f])); return v.every(x => x === v[0]) ? nodes[0][f] : undefined; };
const lab = s => `<label>${esc(_(s))}</label>`;

function renderInspector() {
  const el = $('#inspector'), t = S.cur;
  if (!t) { el.innerHTML = ''; return; }
  const nodes = selNodes();
  if (!nodes.length) {
    el.innerHTML = `
      <h3>${esc(_('Skilltree'))}</h3>
      ${lab('Title (tab)')}<input data-tf="title" value="${esc(t.title)}">
      <div class="row"><div>${lab('Namespace')}<input data-tf="ns" value="${esc(t.ns)}" ${t.handle ? 'disabled' : ''}></div>
      <div>${lab('File')}<input data-tf="name" value="${esc(t.name)}" ${t.handle ? 'disabled' : ''}></div></div>
      <div class="muted small">${esc(_('in-game id:'))} <b>${esc(gameId(t.ns, t.name))}</b>${t.handle ? esc(' · ' + _('renaming = creating a new tree')) : ''}</div>
      ${lab('Tab icon')}
      <div class="row">${iconPrev(t.icon)}<input data-tf="icon" list="itemList" value="${esc(t.icon)}"></div>
      ${lab('Background (texture)')}<input data-tf="background" value="${esc(t.background)}">
      <div class="muted small" style="margin-top:3px">${esc(_('This default value draws the dark night background in game; any other texture is tiled and darkened behind the tree.'))}</div>
      ${lab('Exclusive group (empty = normal tree)')}<input data-tf="exclusive_group" placeholder="class" value="${esc(t.exclusive_group || '')}">
      <div class="muted small" style="margin-top:3px">${esc(_('Trees with the same group exclude each other: the first one a player uses locks the rest, until a node with "Opens trees" unlocks them.'))}</div>
      <div class="gl-head">${esc(_('Limited groups'))}</div>
      <details class="sec" data-sk="gl-nodes" ${secOpen.has('gl-nodes') ? 'open' : ''}><summary>${esc(_('Limited nodes'))} · ${Object.keys(t.group_limits || {}).length}</summary>
      <div class="muted small" style="margin-bottom:4px">${esc(_('Groups of nodes with a limit: create a group and say how many of its nodes a player can take ("pick 2 of 4").'))}<br>${esc(_('Then choose that group on each node that belongs to it.'))}</div>
      <div id="glRows">${Object.entries(t.group_limits || {}).map(([g, lim]) => {
        const used = [...t.nodes.values()].filter(n => n.group === g).length;
        return `<div class="row gl-row" data-g="${esc(g)}"><input data-gl="id" value="${esc(g)}" placeholder="${esc(_('group id'))}"><input class="fixed" style="width:64px" type="number" min="0" data-gl="lim" value="${lim}" title="${esc(_('Limit'))}"><span class="muted small fixed" style="width:58px">${esc(_('{n} nodes', { n: used }))}</span><button class="fixed" data-gl="del" title="${esc(_('Delete group'))}">×</button></div>`;
      }).join('')}</div>
      <button id="glAdd" style="margin-top:4px">${esc(_('＋ Add group'))}</button>
      </details>
      <details class="sec" data-sk="gl-links" ${secOpen.has('gl-links') ? 'open' : ''}><summary>${esc(_('Limited connections (choices)'))} · ${listedForks(t).length}/${choiceForks(t).length}</summary>
      <div class="muted small" style="margin-bottom:4px">${esc(_('Additional settings for "Choice Nodes": here you can see all the "Choice Nodes" you have in total and group them.'))}<br>${esc(_('This makes it easier to add an extra "decision" to several "Choice Nodes" at once.'))}</div>
      <div class="muted small" style="margin-bottom:4px">${esc(_('Only choices with a changed limit, a group or extra picks are listed. To list another one, select its origin node and use "Customize this choice".'))}</div>
      ${(() => { const fs = listedForks(t); return fs.length ? fs.map(f => `<div class="cg-fork"><div class="row small"><span class="choice-dot"></span><span style="flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(f.title)}</span><span class="muted" style="white-space:nowrap;flex:none">${esc(_('{n} options', { n: f.options.length }))} · ${esc(_('pick'))}</span><input class="fixed cl-num" type="number" min="0" data-cl="${esc(f.id)}" value="${f.limit}" title="${esc(_('How many options can be taken (0 = locked until a node opens it)'))}"><span class="muted">${f.limit === 0 ? ' 🔒' : ''}${f.bonus.length ? ' ' + esc(_('(+{b} with {who})', { b: f.bonus.reduce((s, x) => s + x[1], 0), who: f.bonus.map(x => x[0]).join(', ') })) : ''}</span></div>
          <div class="row small cg-row"><span class="muted fixed">${esc(_('Choice group'))}</span><input data-cg="${esc(f.id)}" list="cgList" placeholder="${esc(_('none'))}" value="${esc(f.group)}"></div></div>`).join('')
          + `<datalist id="cgList">${choiceGroupIds(t).map(g => `<option value="${esc(g)}">`).join('')}</datalist>`
          + (() => { const gs = choiceGroupIds(t); return gs.length ? `<div class="gl-sub">${esc(_('Choice groups'))}</div>` + gs.map(g => { const fk = fs.filter(f => f.group === g), who = [...t.nodes.values()].filter(n => (n.group_bonus || {})['choices:' + g]);
              return `<div class="muted small">◆ <b>${esc(g)}</b>: ${esc(fk.map(f => f.title).join(', '))} · ${esc(who.length ? _('+{b} shared with {who}', { b: who.reduce((s, n) => s + n.group_bonus['choices:' + g], 0), who: who.map(n => n.title).join(', ') }) : _('no node gives extra picks yet'))}</div>`; }).join('') : ''; })()
        : `<div class="muted small">${esc(_('No choices yet.'))}</div>`; })()}
      </details>
      <label class="chk" style="margin-top:10px"><input type="checkbox" data-tf="requires_unlock" ${t.requires_unlock ? 'checked' : ''}> <span>${esc(_('Closed until a node opens it'))}</span></label>
      <div class="muted small" style="margin-top:3px">${esc(_('Nobody can use this tree until they own a node that lists it in "Opens trees" (in this or another tree).'))}</div>
      <label class="chk" style="margin-top:10px"><input type="checkbox" data-tf="first_start_only" ${t.first_start_only ? 'checked' : ''}> <span>${esc(_('Only the first start node is free'))}</span></label>
      <div class="muted small" style="margin-top:3px">${esc(_('Later start nodes must be reached through links (walking or portals), not picked directly.'))}</div>
      <div class="group" style="margin-top:16px"><h3>${esc(_('Summary'))}</h3>
        <div class="muted">${esc(_('{n} nodes · {l} links', { n: t.nodes.size, l: t.links.size }))}</div>
        <div class="row" style="margin-top:8px"><button id="inspValidate">${esc(_('✔ Validate'))}</button><button id="inspDelTree" class="danger">${esc(_('Delete tree'))}</button></div></div>
      <div class="muted small">${esc(_('Select nodes with the Select tool (V) to edit them. Drag on empty space to select several.'))}</div>`;
    return;
  }
  const one = nodes.length === 1, n0 = nodes[0];
  const v = f => common(nodes, f);
  const val = f => (v(f) === undefined ? '' : esc(v(f)));
  const ph = f => (v(f) === undefined ? `placeholder="${esc(_('(several)'))}"` : '');
  const kind = v('kind'), shape = v('shape');
  el.innerHTML = `
    <h3>${one ? esc(_('ID: {id}', { id: n0.id })) : esc(_('{n} nodes selected', { n: nodes.length }))}</h3>
    <label style="text-transform:none;display:flex;gap:6px;align-items:center;margin:2px 0 8px;color:var(--text)"><input type="checkbox" id="propMirror" ${S.propMirror === false ? '' : 'checked'}> ${esc(_('Apply changes to mirrored nodes too'))}</label>
    ${lab('Title')}<input data-f="title" value="${val('title')}" ${ph('title')}>
    ${lab('Icon (item id, other mods too)')}
    <div class="row">${iconPrev(v('icon'))}<input data-f="icon" list="itemList" value="${val('icon')}" ${ph('icon')}></div>
    <div class="row"><div>${lab('Size (visual only)')}<select data-f="shape">${opts({ small: _('Small'), medium: _('Medium'), large: _('Large') }, shape)}</select></div>
    <div>${lab('Cost (skill points)')}<input data-f="cost" type="number" min="0" value="${val('cost')}" ${ph('cost')}></div></div>
    <div class="row"><div>${lab('Shape')}<select data-f="form">${opts({ circle: _('Circle'), square: _('Square'), diamond: _('Diamond'), star: _('Star') }, v('form') || 'circle')}</select></div></div>
    <label style="text-transform:none;display:flex;gap:6px;align-items:center;margin:10px 0 0;color:var(--text)"><input type="checkbox" data-f="start" ${v('start') === true ? 'checked' : ''}> ${esc(_('Start node (the player may begin here, always available)'))}</label>
    <div class="row"><div>${lab('Max level')}<input data-f="max_level" type="number" min="1" value="${val('max_level')}" ${ph('max_level')}></div>
    <div>${lab('Extra cost per level')}<input data-f="cost_step" type="number" value="${val('cost_step')}" ${ph('cost_step')}></div></div>
    <div class="muted small" style="margin-top:3px">${esc(_('Max level above 1: the player can buy this node several times. Attributes are multiplied by the level; level k costs cost + (k-1) × extra.'))}</div>
    <div class="row"><div>${lab('Type')}<select data-f="kind">${opts({ normal: _('Normal'), and: _('AND') }, kind)}</select></div>
    <div ${kind === 'and' ? '' : 'style="visibility:hidden"'}>${lab('Requires (neighbors, 0 = all)')}<input data-f="require" type="number" min="0" value="${val('require')}"></div></div>
    <div class="row"><div>${lab('Previous node must be level')}<input data-f="requires_prev_level" type="number" min="1" value="${val('requires_prev_level')}" ${ph('requires_prev_level')}></div></div>
    ${lab('Color')}
    <div class="row"><input type="color" class="fixed" data-f="color" value="${v('color') || '#c9a24a'}"><button class="fixed" id="clearColor">${esc(_('No color'))}</button><span class="muted small">${v('color') ? esc(v('color')) : esc(_('no tint'))}</span></div>
    <div class="swatches">${PALETTE.map(c => `<div class="swatch" data-sw="${c}" style="background:${c}"></div>`).join('')}</div>
    ${lab('Description (one line per row)')}<textarea data-f="description" data-lines ${ph('description')}>${esc((v('description') || []).join('\n'))}</textarea>
    <label>${esc(_('Attributes'))}${one ? '' : esc(' ' + _('(shared by the selected nodes: a change applies to all of them)'))}</label>
    <div id="attrRows">${one ? n0.attributes.map((a, i) => attrRow(a, i)).join('') : multiAttrRows(selNodes())}</div>
    <div class="row"><select id="addAttr"><option value="">${esc(_('＋ Add from library…'))}</option>${S.libs.attrs.map(a => `<option value="${esc(a.id)}">${esc(_(a.name || a.id))}  (${esc(a.id)})</option>`).join('')}</select>
    <button class="fixed" id="addAttrBlank">${esc(_('＋ Blank'))}</button></div>
    <div class="muted small">${esc(_('Negative amount = penalty. Bonuses and penalties can be mixed in one node.'))}</div>
    <details class="sec" data-sk="group" ${secOpen.has('group') ? 'open' : ''}><summary>${esc(_('Limited groups'))}${v('group') ? ' · ' + esc(v('group')) : ''}</summary>
    <div class="muted small" style="margin-top:2px">${esc(_('Optional. The group counts how many of its nodes the player takes ("pick 2 of 4"). For a whole path, put the group only on its first node. Groups are created in the tree settings (click empty space).'))}</div>
    <div class="gl-sub">${esc(_('Limited nodes'))}</div>
    ${lab('Group of this node')}<select data-f="group">${(() => { const gs = Object.keys(S.cur.group_limits || {}); const cur = v('group');
      return `<option value="" ${!cur ? 'selected' : ''}>${esc(_('— none —'))}</option>` + gs.map(g => `<option value="${esc(g)}" ${cur === g ? 'selected' : ''}>${esc(g)} (max ${S.cur.group_limits[g]})</option>`).join('')
        + (cur && !gs.includes(cur) ? `<option value="${esc(cur)}" selected>${esc(cur)} (?)</option>` : ''); })()}</select>
    ${lab('Taking this node raises the limit of a node group')}
    <div id="gbRowsNode" class="gb-list">${gbRows(one && n0, 'node')}</div>
    ${one && Object.keys(S.cur.group_limits || {}).length ? `<button data-gbadd="node" style="margin-top:4px">${esc(_('＋ Raise a node group'))}</button>` : ''}
    <div class="gl-sub">${esc(_('Limited connections (choices)'))}</div>
    ${one ? (() => { const f = choiceForks(S.cur).find(x => x.id === n0.id); if (!f) return '';
      const listed = listedForks(S.cur).some(x => x.id === f.id);
      return `<div class="row small" style="align-items:center"><span class="choice-dot"></span><span style="flex:1">${esc(_('This node opens a choice: {n} options · pick', { n: f.options.length }))}</span><input class="fixed cl-num" type="number" min="0" data-cl="${esc(f.id)}" value="${f.limit}" title="${esc(_('How many options can be taken (0 = locked until a node opens it)'))}"></div>
        ${listed ? `<div class="muted small">${esc(_('Shown in the tree list of choices.'))}</div>` : `<button id="clPin" style="margin-top:4px">${esc(_('⚙ Customize this choice'))}</button><div class="muted small">${esc(_('Default limit (1): hidden from the tree list. Customize it to list it there (limit, group).'))}</div>`}`; })() : ''}
    ${one ? (() => { const fs = choiceForks(S.cur).filter(f => f.options.includes(n0.id)); return fs.length ? `<div class="muted small">${esc(_('This node is an option of:'))} ${fs.map(f => esc(f.title) + (f.group ? ' (' + esc(_('group {g}', { g: f.group })) + ')' : '')).join(' · ')}</div>
      ${lab('Free choice if you have (ids, one per line)')}
      <textarea data-f="free_choice_if" data-lines placeholder="mage_mastery">${esc((n0.free_choice_if || []).join('\n'))}</textarea>
      <div class="muted small">${esc(_('If the player has any of these nodes, taking this option does not use a pick of its choice.'))}</div>` : ''; })() : ''}
    ${lab('Taking this node gives one more pick at the choice of')}
    <div id="gbRowsLink" class="gb-list">${gbRows(one && n0, 'link')}</div>
    ${one && choiceForks(S.cur).length ? `<button data-gbadd="link" style="margin-top:4px">${esc(_('＋ One more pick at a choice'))}</button>` : `<div class="muted small">${esc(_('No choices yet.'))}</div>`}
    </details>
    <details class="sec" data-sk="req" ${secOpen.has('req') ? 'open' : ''}><summary>${esc(_('Requirements to unlock'))}</summary>
    ${lab('Required nodes (ids, one per line; no link needed)')}
    <textarea data-f="requires_nodes" data-lines placeholder="merchant_mastery&#10;alchemist_mastery">${esc((v('requires_nodes') || []).join('\n'))}</textarea>
    <div class="row"><div>${lab('How many of them (0 = all)')}<input data-f="requires_count" type="number" min="0" value="${val('requires_count') || 0}"></div></div>
    <div class="gl-sub">${esc(_('Higher levels'))}</div>
    <div class="row"><div>${lab('Free up to level (0 = no cap)')}<input data-f="cap_level" type="number" min="0" value="${val('cap_level') || 0}"></div>
      <div>${lab('How many of them (0 = all)')}<input data-f="cap_count" type="number" min="0" value="${val('cap_count') || 0}"></div></div>
    ${lab('Higher levels need (ids, one per line)')}
    <textarea data-f="cap_nodes" data-lines placeholder="soldier_mastery&#10;mage_mastery">${esc((v('cap_nodes') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('E.g. free up to level 5; levels 6+ need 2 of these nodes.'))}</div>
    ${lab('Required advancements (ids, one per line)')}
    <textarea data-f="requires_advancements" data-lines placeholder="minecraft:story/enter_the_nether">${esc((v('requires_advancements') || []).join('\n'))}</textarea>
    <div class="row"><select id="addAdv"><option value="">${esc(_('＋ Add a vanilla advancement…'))}</option>${VANILLA_ADVS.map(a => `<option>${a}</option>`).join('')}</select></div>
    ${lab('Minimum attribute values (one per line: attribute amount)')}
    <textarea data-f="requires_attributes" data-attrreq list="attrList" placeholder="minecraft:generic.max_health 30">${esc((v('requires_attributes') || []).map(a => a.attribute + ' ' + a.amount).join('\n'))}</textarea>
    <div class="row"><select id="addAttrReq"><option value="">${esc(_('＋ Add from library…'))}</option>${S.libs.attrs.map(a => `<option value="${esc(a.id)}">${esc(_(a.name || a.id))}  (${esc(a.id)})</option>`).join('')}</select></div>
    <div class="muted small">${esc(_('The player\'s current value must reach this to take the first level. It is only checked then: if the value drops later the node is kept.'))}</div>
    ${lab('Items handed to the tree (one per line: item count)')}
    <textarea data-f="requires_items" data-items placeholder="minecraft:diamond 3">${esc((v('requires_items') || []).map(i => i.item + ' ' + i.count).join('\n'))}</textarea>
    <div class="muted small">${esc(_('Consumed when the first level is taken; given back when the node is fully refunded.'))}</div>
    </details>
    <details class="sec" data-sk="rew" ${secOpen.has('rew') ? 'open' : ''}><summary>${esc(_('Rewards (any combination)'))}</summary>
    ${lab('Recipes unlocked (ids, one per line)')}<textarea data-f="recipes" data-lines placeholder="minecraft:diamond_sword">${esc((v('recipes') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('Added to the recipe book while the node is taken. Use the recipe type waifing_skilltree:skill_locked so only players with the node can craft it (no gamerule needed).'))}</div>
    ${lab('Right-click recipes unlocked (ids, one per line)')}<textarea data-f="click_recipes" data-lines placeholder="mymod:sharpen_sword">${esc((v('click_recipes') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('Defined in data/<namespace>/waifingskilltree/click_recipes/<name>.json. Hold the catalyst on the cursor and right click the target in the inventory.'))}</div>
    ${lab('Opens trees (closed or exclusive trees)')}<textarea data-f="unlock_trees" data-lines placeholder="waifing_mage&#10;#class">${esc((v('unlock_trees') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('In-game tree ids, or #group for every tree of that group. Lets the player use them even though they chose another tree of the group.'))}</div>
    ${lab('Commands unlocked for the player (one per line, no "/")')}<textarea data-f="unlock_commands" data-lines placeholder="say">${esc((v('unlock_commands') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('The player may use these commands while the node is taken (e.g. "say" or "gamemode creative"). Everyone else is blocked from them.'))}</div>
    ${lab('Commands on take (no leading "/", %player%, %level%)')}<textarea data-f="commands" data-lines placeholder="give %player% minecraft:apple 1">${esc((v('commands') || []).join('\n'))}</textarea>
    <div class="muted small">${esc(_('Runs every time a level is gained. Start a line with "@1 " to run it only on level 1.'))}</div>
    ${lab('Commands on refund')}<textarea data-f="refund_commands" data-lines>${esc((v('refund_commands') || []).join('\n'))}</textarea>
    </details>
    <div class="row" style="margin-top:14px"><button id="inspDup">${esc(_('Duplicate'))}</button><button id="inspDel" class="danger">${esc(_('Delete'))}</button></div>`;
}
/** Which collapsible inspector sections are open (all closed by default; remembered while editing). */
var secOpen = new Set();
document.getElementById('inspector').addEventListener('toggle', e => {
  const k = e.target?.dataset?.sk; if (!k) return;
  e.target.open ? secOpen.add(k) : secOpen.delete(k);
}, true);
const opts = (map, cur) => Object.entries(map).map(([k, l]) => `<option value="${k}" ${k === cur ? 'selected' : ''}>${esc(l)}</option>`).join('');
const attrKey = a => a.attribute + '|' + a.operation + '|' + JSON.stringify(a.condition || null);
/** Multi-selection: the attributes every selected node has (same attribute, operation and condition) are edited together.
 *  Same amount everywhere -> shown; different amounts -> empty box ("different") that sets all of them when typed in. */
let multiKeys = [];
function multiAttrRows(nodes) {
  const first = nodes[0], rows = [], partial = new Map();
  multiKeys = [];
  for (const a of first.attributes) {
    const k = attrKey(a);
    if (multiKeys.includes(k)) continue;
    const amounts = nodes.map(n => n.attributes.find(b => attrKey(b) === k)?.amount);
    if (amounts.some(x => x === undefined)) continue;
    multiKeys.push(k);
    const same = amounts.every(x => x === amounts[0]);
    rows.push(attrRow({ ...a, amount: same ? a.amount : '' }, rows.length, k, same ? null : [Math.min(...amounts), Math.max(...amounts)]));
  }
  for (const n of nodes) for (const b of n.attributes) { const k = attrKey(b); if (!multiKeys.includes(k)) partial.set(k, (partial.get(k) || new Set()).add(n.id)); }
  const extra = [...partial].map(([k, ids]) => `<div class="muted small">· ${esc(attrInfo(k.split('|')[0])?.name || k.split('|')[0])} — ${esc(_('only in {n} of {m} nodes', { n: ids.size, m: nodes.length }))}</div>`).join('');
  return rows.join('') + (extra ? `<div style="margin:4px 0 2px">${extra}</div>` : '');
}
const attrRow = (a, i, key, range) => `<div class="attr-row" ${key ? `data-key="${esc(key)}"` : ''}><input data-a="attribute" list="attrList" value="${esc(a.attribute)}" placeholder="mod:attribute">
  <input data-a="amount" type="number" step="any" value="${a.amount}" ${range ? `placeholder="${esc(_('different'))} (${range[0]}…${range[1]})"` : ''}>
  <select data-a="operation">${opts({ add: _('add'), mult_base: _('× base'), mult_total: _('× total') }, a.operation)}</select>
  <button data-a-up title="${esc(_('Move up'))}" ${key ? 'disabled' : ''}>▲</button><button data-a-down title="${esc(_('Move down'))}" ${key ? 'disabled' : ''}>▼</button>
  <button data-a-del title="${esc(_('Remove'))}">✕</button>
  ${condEditor(a.condition, i)}</div>`;

/* ---------- attribute conditions ---------- */
const isComposite = c => c && (c.type === 'all' || c.type === 'any');
/** Splits a stored condition into editable leaf rows + advanced (nested) pieces that are kept untouched. */
function condParts(c) {
  if (!c) return { mode: 'all', items: [] };
  if (isComposite(c) && !c.not) return { mode: c.type, items: c.conditions || [] };
  return { mode: 'all', items: [c] };
}
const condLeaf = c => !isComposite(c) && !(c.conditions && c.conditions.length);
function condRow(c) {
  const kind = COND_KIND[c.type] || 'text', spec = COND_TYPES.find(x => x[0] === c.type);
  const label = Object.fromEntries(COND_TYPES.map(x => [x[0], _(x[1])]));
  return `<div class="cond-row"><select data-c="type">${opts(label, c.type)}</select>
    ${kind === 'none' ? '' : `<input data-c="value" ${kind === 'num' ? 'type="number" step="any"' : ''} ${kind === 'item' ? 'list="itemList"' : ''} value="${esc(c.value ?? '')}" placeholder="${esc(_(spec?.[3] || ''))}">`}
    <label class="notbox" title="${esc(_('Invert this condition'))}"><input type="checkbox" data-c="not" ${c.not ? 'checked' : ''}> ${esc(_('not'))}</label>
    <button data-c-del title="${esc(_('Remove'))}">✕</button></div>`;
}
function condEditor(cond, i) {
  const { mode, items } = condParts(cond);
  const leaves = items.filter(condLeaf), adv = items.filter(c => !condLeaf(c));
  const n = items.length;
  return `<details class="cond" data-sk="cond${i}" ${secOpen.has('cond' + i) ? 'open' : ''}><summary>${esc(n ? _('Condition ({n})', { n }) : _('Condition: always active'))}</summary>
    ${n > 1 ? `<select data-c="mode">${opts({ all: _('All of these must hold'), any: _('At least one must hold') }, mode)}</select>` : '<input type="hidden" data-c="mode" value="' + mode + '">'}
    <div class="cond-list">${leaves.map(condRow).join('')}</div>
    ${adv.map(c => `<div class="cond-adv" data-json="${esc(JSON.stringify(c))}">${esc(_('Advanced condition (edit the JSON by hand)'))} <button data-c-del title="${esc(_('Remove'))}">✕</button></div>`).join('')}
    <button data-c-add class="small">${esc(_('＋ Add condition'))}</button>
  </details>`;
}
function readCond(row) {
  const d = $('details.cond', row);
  if (!d) return null;
  const leaves = $$('.cond-row', d).map(cr => {
    const type = $('[data-c=type]', cr).value, o = { type }, v = $('[data-c=value]', cr);
    if (v && v.value.trim() !== '') o.value = COND_KIND[type] === 'num' ? parseFloat(v.value) : v.value.trim();
    if ($('[data-c=not]', cr).checked) o.not = true;
    return o;
  });
  const adv = $$('.cond-adv', d).map(a => { try { return JSON.parse(a.dataset.json); } catch (e) { return null; } }).filter(Boolean);
  const items = [...leaves, ...adv];
  if (!items.length) return null;
  const mode = $('[data-c=mode]', d)?.value || 'all';
  return items.length === 1 ? items[0] : { type: mode, conditions: items };
}
function iconPrev(id) {
  const img = id && S.tex.get(id);
  return `<div class="icon-prev">${img ? `<img src="${img.src}">` : esc(((id || '').split(':')[1] || '?').slice(0, 3))}</div>`;
}

/** Applies fn to the selected nodes and, when enabled, to their mirrored counterparts. */
function forTargets(fn) {
  const t = S.cur, targets = new Set(selNodes());
  if ($('#propMirror')?.checked && mirrorCount() > 1) for (const n of [...targets]) counterparts(t, n).forEach(c => targets.add(c.node));
  targets.forEach(fn);
}
function setField(f, value, finalize) {
  forTargets(n => { n[f] = Array.isArray(value) ? value.map(x => (x && typeof x === 'object' ? { ...x } : x)) : value; });
  if (finalize) { S.noInspRefresh = true; commit(); S.noInspRefresh = false; } else requestDraw();
}
function readAttrRows() {
  return $$('#attrRows .attr-row').map(r => ({
    attribute: $('[data-a=attribute]', r).value.trim(), amount: parseFloat($('[data-a=amount]', r).value) || 0, operation: $('[data-a=operation]', r).value,
    ...(readCond(r) ? { condition: readCond(r) } : {}),
  })).filter(a => a.attribute);
}
function applyAttrs(structural) {
  if (S.sel.size > 1) return applyMultiAttrs(structural);
  const list = readAttrRows();
  forTargets(n => { n.attributes = JSON.parse(JSON.stringify(list)); });
  if (structural) { commit(); renderInspector(); } else { S.noInspRefresh = true; commit(); S.noInspRefresh = false; }
}

/** Multi-selection: update every selected node's matching attribute (by the key it had when the panel was drawn);
 *  an empty amount box (different amounts) keeps each node's own amount. Rows removed here are removed from every node. */
function applyMultiAttrs(structural) {
  const rows = $$('#attrRows .attr-row').map(r => {
    const amt = $('[data-a=amount]', r).value.trim();
    return { key: r.dataset.key, attribute: $('[data-a=attribute]', r).value.trim(), operation: $('[data-a=operation]', r).value,
      amount: amt === '' ? null : (parseFloat(amt) || 0), condition: readCond(r) || null };
  });
  const kept = new Set(rows.map(r => r.key));
  const fresh = structural ? rows.filter(r => !r.key && r.attribute) : [];   // new blank rows: added to every node once typed
  forTargets(n => {
    n.attributes = n.attributes.filter(a => !(multiKeys.includes(attrKey(a)) && !kept.has(attrKey(a))));
    for (const r of rows) {
      const i = n.attributes.findIndex(a => attrKey(a) === r.key);
      if (i < 0 || !r.attribute) continue;
      const old = n.attributes[i];
      n.attributes[i] = { attribute: r.attribute, amount: r.amount === null ? old.amount : r.amount, operation: r.operation, ...(r.condition ? { condition: r.condition } : {}) };
    }
    for (const r of fresh) n.attributes.push({ attribute: r.attribute, amount: r.amount ?? 1, operation: r.operation, ...(r.condition ? { condition: r.condition } : {}) });
  });
  if (structural) { commit(); renderInspector(); } else { S.noInspRefresh = true; commit(); S.noInspRefresh = false; }
}

const insp = $('#inspector');
insp.addEventListener('input', e => {
  const el = e.target;
  if (el.dataset.f && !el.hasAttribute('data-lines') && el.type !== 'color') {
    if (el.type === 'checkbox' || el.hasAttribute('data-items') || el.hasAttribute('data-attrreq')) return;   // handled on change
    let val = el.value;
    if (el.type === 'number') val = val === '' ? 0 : parseFloat(val);
    setField(el.dataset.f, val, false);
  }
  if (el.type === 'color' && el.dataset.f) setField('color', el.value, false);
});
/* ---- limited groups (tree) and limit raises (node) ---- */
/** Rows of the node's group_bonus of one kind: 'node' (limited node groups) or 'link' (choices, "choice:<origin>"). */
function gbRows(n, kind) {
  if (!n) return '';
  const ts = bonusTargets(S.cur).filter(x => x[2] === kind);
  return Object.entries(n.group_bonus || {}).filter(([g]) => (kind === 'link') === /^choices?:/.test(g)).map(([g, x]) =>
    `<div class="row gb-row"><select data-gb="g">${ts.map(([k, lb]) => `<option value="${esc(k)}" ${k === g ? 'selected' : ''}>${esc(lb)}</option>`).join('')}${ts.some(t => t[0] === g) ? '' : `<option selected>${esc(g)}</option>`}</select><input class="fixed" style="width:64px" type="number" data-gb="n" value="${x}"><button class="fixed" data-gb="del">×</button></div>`).join('');
}
function readGroupRows() {
  const t = S.cur, old = t.group_limits || {}, out = {}, renames = {};
  for (const r of $$('#glRows .gl-row')) {
    const id = $('[data-gl=id]', r).value.trim().toLowerCase().replace(/[^a-z0-9_.-]+/g, '_');
    if (!id) continue;
    out[id] = Math.max(0, parseInt($('[data-gl=lim]', r).value, 10) || 0);
    if (r.dataset.g && r.dataset.g !== id) renames[r.dataset.g] = id;
  }
  for (const n of t.nodes.values()) {   // renaming a group renames it on its nodes too
    if (renames[n.group]) n.group = renames[n.group];
    for (const [a, b] of Object.entries(renames)) if (n.group_bonus && a in n.group_bonus) { n.group_bonus[b] = n.group_bonus[a]; delete n.group_bonus[a]; }
  }
  t.group_limits = out; t.dirty = true;
}
function readBonusRows() {
  const o = {};
  for (const r of $$('.gb-list .gb-row')) { const g = $('[data-gb=g]', r).value, x = parseInt($('[data-gb=n]', r).value, 10) || 0; if (g && x) o[g] = (o[g] || 0) + x; }
  setField('group_bonus', o, true);
}
insp.addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b || !S.cur) return;
  if (b.id === 'glAdd') { let i = 1; while (('group_' + i) in (S.cur.group_limits || {})) i++; S.cur.group_limits = { ...(S.cur.group_limits || {}), ['group_' + i]: 2 }; S.cur.dirty = true; UI.refreshAll(); }
  else if (b.dataset.gl === 'del') { const g = b.closest('.gl-row').dataset.g; b.closest('.gl-row').remove(); readGroupRows();
    for (const n of S.cur.nodes.values()) { if (n.group === g) n.group = ''; if (n.group_bonus) delete n.group_bonus[g]; } commit(); UI.refreshAll(); }
  else if (b.dataset.gbadd) {   // add a raise of that kind, on the first target not used yet
    const n = selNodes()[0], ts = bonusTargets(S.cur).filter(x => x[2] === b.dataset.gbadd), cur = n.group_bonus || {};
    const g = (ts.find(x => !(x[0] in cur)) || ts[0])[0];
    setField('group_bonus', { ...cur, [g]: (cur[g] || 0) + 1 }, true); renderInspector();
  }
  else if (b.id === 'clPin') { const id = selNodes()[0].id; (S.cur.cl_pinned ||= new Set()).add(id); renderInspector(); toast(_('Choice listed in the tree settings (click empty space).')); }
  else if (b.dataset.gb === 'del') { b.closest('.gb-row').remove(); readBonusRows(); renderInspector(); }
});
insp.addEventListener('change', e => {
  const el = e.target, t = S.cur;
  if (el.dataset.a && S.sel.size > 1 && !el.closest('.attr-row')?.dataset.key) { applyMultiAttrs(true); return; }
  if (el.dataset.gl) { readGroupRows(); commit(); UI.refreshAll(); return; }
  if (el.dataset.gb) { readBonusRows(); return; }
  if (el.dataset.cl !== undefined) {   // how many options a choice allows (default 1, 0 = locked)
    const n = Math.max(0, parseInt(el.value, 10) || 0), cl = { ...(t.choice_limits || {}) };
    if (n === 1) delete cl[el.dataset.cl]; else cl[el.dataset.cl] = n;
    t.choice_limits = cl; t.dirty = true; commit(); UI.refreshAll(); return;
  }
  if (el.dataset.cg !== undefined) {   // choice group of a fork
    const g = el.value.trim().toLowerCase().replace(/[^a-z0-9_.-]+/g, '_'), cg = { ...(t.choice_groups || {}) };
    if (g) cg[el.dataset.cg] = g; else delete cg[el.dataset.cg];
    t.choice_groups = cg; t.dirty = true; commit(); UI.refreshAll(); return;
  }
  if (el.dataset.tf) { // tree properties
    const f = el.dataset.tf;
    if (f === 'ns') t.ns = sanitizeNs(el.value);
    else if (f === 'name') t.name = sanitizePath(el.value);
    else if (f === 'first_start_only') t.first_start_only = el.checked;
    else if (f === 'requires_unlock') t.requires_unlock = el.checked;
    else t[f] = el.value;
    if (f === 'ns' || f === 'name') t.id = gameId(t.ns, t.name);
    t.dirty = true; UI.refreshAll(); return;
  }
  if (el.dataset.f) {
    const f = el.dataset.f;
    let val;
    if (el.hasAttribute('data-attrreq')) val = el.value.split('\n').map(s => s.trim()).filter(Boolean).map(s => { const m = s.split(/[\s,]+/); return { attribute: m[0], amount: parseFloat(m[1]) || 0 }; });
    else if (el.hasAttribute('data-items')) val = el.value.split('\n').map(s => s.trim()).filter(Boolean).map(s => { const m = s.split(/[\s,]+/); return { item: m[0], count: Math.max(1, parseInt(m[1], 10) || 1) }; });
    else if (el.hasAttribute('data-lines')) val = el.value.split('\n').map(s => s.trim()).filter(Boolean);
    else if (el.type === 'checkbox') val = el.checked;
    else if (el.type === 'number') val = el.value === '' ? 0 : parseFloat(el.value);
    else val = el.value;
    if (f === 'max_level' || f === 'requires_prev_level') val = Math.max(1, Math.round(val) || 1);
    if (f === 'cap_level' || f === 'cap_count') val = Math.max(0, Math.round(val) || 0);
    const structural = f === 'kind' || f === 'shape' || f === 'form' || f === 'icon' || f === 'color' || f === 'start';
    setField(f, val, true);
    if (structural) renderInspector();
    return;
  }
  if (el.dataset.a) { if (S.sel.size > 1 && !el.closest('.attr-row')?.dataset.key) return; applyAttrs(false); return; }   // a new row waits for "change"
  if (el.dataset.c) { applyAttrs(el.dataset.c === 'type' || el.dataset.c === 'mode'); return; }
  if (el.id === 'addAttr' && el.value) {
    const info = attrInfo(el.value);   // added to every selected node, each keeps its own attributes
    forTargets(n => { n.attributes = [...n.attributes, { attribute: el.value, amount: info?.amount ?? 1, operation: info?.operation ?? 'add' }]; });
    commit(); renderInspector(); return;
  }
  if (el.id === 'addAttrReq' && el.value) {
    const info = attrInfo(el.value), cur = selNodes()[0].requires_attributes;
    if (!cur.some(a => a.attribute === el.value)) setField('requires_attributes', [...cur, { attribute: el.value, amount: 1 }], true);
    renderInspector(); return;
  }
  if (el.id === 'addAdv' && el.value) {
    const cur = selNodes()[0].requires_advancements;
    if (!cur.includes(el.value)) setField('requires_advancements', [...cur, el.value], true);
    renderInspector(); return;
  }
  if (el.id === 'propMirror') S.propMirror = el.checked;
});
insp.addEventListener('click', e => {
  const el = e.target;
  if (el.matches('[data-c-add]')) {
    const d = el.closest('details.cond');
    $('.cond-list', d).insertAdjacentHTML('beforeend', condRow({ type: 'main_hand' }));
    applyAttrs(true); return;
  }
  if (el.matches('[data-c-del]')) { el.closest('.cond-row, .cond-adv').remove(); applyAttrs(true); return; }
  if (el.matches('[data-a-del]')) { el.closest('.attr-row').remove(); applyAttrs(true); return; }
  if (el.matches('[data-a-up], [data-a-down]')) {   // reorder: the order is the one shown in game
    const row = el.closest('.attr-row'), other = el.matches('[data-a-up]') ? row.previousElementSibling : row.nextElementSibling;
    if (!other || !other.classList.contains('attr-row')) return;
    if (el.matches('[data-a-up]')) other.before(row); else other.after(row);
    applyAttrs(true); return;
  }
  if (el.id === 'addAttrBlank') {
    if (S.sel.size > 1) { $('#attrRows').insertAdjacentHTML('beforeend', attrRow({ attribute: '', amount: 1, operation: 'add' }, 99)); $('#attrRows .attr-row:last-child [data-a=attribute]').focus(); return; }
    const base = selNodes()[0].attributes.map(a => ({ ...a })); forTargets(n => { n.attributes = [...base.map(a => ({ ...a })), { attribute: '', amount: 1, operation: 'add' }]; }); renderInspector(); return;
  }
  if (el.id === 'clearColor') { setField('color', '', true); renderInspector(); return; }
  if (el.dataset.sw) { setField('color', el.dataset.sw, true); renderInspector(); return; }
  if (el.id === 'inspDel') { for (const id of [...S.sel]) deleteNodeMirrored(S.cur, id); S.sel.clear(); commit(); return; }
  if (el.id === 'inspDup') duplicateSelection();
  if (el.id === 'inspValidate') showProblems();
  if (el.id === 'inspDelTree') deleteCurrentTree();
});

function duplicateSelection() {
  const t = S.cur, src = selNodes();
  if (!src.length) return;
  const map = new Map(), ids = [];
  for (const n of src) { const c = newNode(freshId(t), n.x + 30, n.y + 30, cloneProps(n)); t.nodes.set(c.id, c); map.set(n.id, c.id); ids.push(c.id); }
  for (const l of [...t.links.values()]) if (map.has(l.from) && map.has(l.to)) { const a = map.get(l.from), b = map.get(l.to); t.links.set(linkKey(a, b), Object.assign({}, l, { from: a, to: b })); }
  replicateMirrored(t, ids);   // with Mirror on the copies appear in every mirrored place too
  S.sel = new Set(ids); commit();
}

/* ---------- trees ---------- */
function addTree(ns, name, title) {
  ns = sanitizeNs(ns); name = sanitizePath(name);
  if (S.trees.some(t => t.ns === ns && t.name === name)) { toast(_('That tree already exists.')); return null; }
  const t = makeTree(ns, name, title);
  const start = newNode('n0', 0, 0, { shape: 'large', start: true, title: _('Start'), icon: 'minecraft:nether_star' });
  t.nodes.set(start.id, start);
  resetHistory(t); t.dirty = true;
  S.trees.push(t);
  return t;
}
function deleteCurrentTree() {
  const t = S.cur;
  if (!t || !confirm(_('Delete the tree "{title}"? {note}', { title: t.title, note: t.handle ? _('Its file will be removed when you save.') : '' }))) return;
  if (t.handle) S.deleted.push({ ns: t.ns, name: t.name });
  S.trees = S.trees.filter(x => x !== t);
  if (!S.trees.length) addTree('my_pack', 'skilltree', _('New tree'));
  S.cur = null; UI.selectTree(S.trees[0]);
}
/** A fresh, untouched editor session (the empty tree it starts with). */
const isPristine = () => !S.root && S.trees.length === 1 && S.trees[0].nodes.size === 1 && S.trees[0].history.length === 1 && !S.deleted.length;

/* ---------- opening: folder (direct save), files, zips, folders in any browser ---------- */
async function openFolder(handle) {
  try {
    handle = handle || await window.showDirectoryPicker({ mode: 'readwrite' });
    if (handle.requestPermission && (await handle.requestPermission({ mode: 'readwrite' })) !== 'granted') toast(_('Write permission is needed to save into the folder.'));
    const { found } = await readDatapackDir(handle);
    if (S.trees.some(t => t.dirty) && !confirm(_('There are unsaved changes. Discard them and open the folder?'))) return;
    S.root = handle; S.deleted = [];
    S.rootHasMeta = await handle.getFileHandle('pack.mcmeta').then(() => true, () => false);
    S.trees = found;
    if (!found.length) { toast(_('No trees in that folder; starting with a new one.')); addTree('my_pack', 'skilltree', _('New tree')); }
    S.trees.forEach(t => { resetHistory(t); t.dirty = false; });
    S.cur = null; UI.selectTree(S.trees[0]);
    toast(_('{n} tree(s) loaded from "{name}".', { n: found.length, name: handle.name }));
  } catch (e) { if (e.name !== 'AbortError') toast(_('Could not open the folder: {err}', { err: e.message })); }
}

/** Entry point for everything that is not the direct-save folder: file picker, folder input, drag & drop. */
async function importItems(items) {
  toast(_('Reading {n} file(s)…', { n: items.length }), 1500);
  const { found, notes } = await gatherTrees(items);
  notes.slice(0, 4).forEach(n => toast(n, 7000));
  if (!found.length) { toast(_('No skill trees found. Looking for data/<namespace>/waifingskilltree/trees/*.json (inside a folder, a .zip or a .jar) or a loose tree .json.'), 8000); return; }
  if (isPristine()) { applyImport(found.map(f => ({ f, action: 'new' }))); return; }
  showImportDialog(found);
}

function showImportDialog(found) {
  $('#importIntro').textContent = _('Found {n} tree(s). Choose what to do with each one.', { n: found.length });
  const list = $('#importList');
  list.innerHTML = found.map((f, i) => {
    const exists = S.trees.some(t => t.ns === f.ns && t.name === f.name);
    return `<div class="imp-row" data-idx="${i}">
      <label class="imp-main"><input type="checkbox" class="imp-on" checked>
        <span><b>${esc(f.ns)}:${esc(f.name)}</b> <span class="muted small">${esc(_('{n} nodes', { n: f.nodes }))} · ${esc(f.source)}</span>${exists ? `<br><span class="small" style="color:var(--warn)">${esc(_('A tree with this id is already open.'))}</span>` : ''}</span></label>
      <select class="imp-act">
        <option value="new" selected>${esc(_('Add as a new tree'))}</option>
        ${exists ? `<option value="replace">${esc(_('Replace the open one'))}</option>` : ''}
        <option value="merge">${esc(_('Merge into the current tree'))}</option>
      </select></div>`;
  }).join('');
  $('#impOk').onclick = () => {
    const rows = $$('#importList .imp-row').filter(r => $('.imp-on', r).checked).map(r => ({ f: found[+r.dataset.idx], action: $('.imp-act', r).value }));
    $('#dlgImport').close();
    if (rows.length) applyImport(rows);
  };
  $('#dlgImport').showModal();
}

function uniqueName(ns, name) {
  let n = name, i = 2;
  while (S.trees.some(t => t.ns === ns && t.name === n)) n = name + '_' + i++;
  return n;
}

function applyImport(rows) {
  if (isPristine()) S.trees = [];
  let last = null, added = 0, replaced = 0, merged = 0;
  for (const { f, action } of rows) {
    try {
      const src = treeFromJSON(f.json, f.ns, f.name);
      const exist = S.trees.find(t => t.ns === f.ns && t.name === f.name);
      if (action === 'merge' && S.cur && S.trees.includes(S.cur)) {
        mergeInto(S.cur, src, f.name); merged++; last = S.cur;
      } else if (action === 'replace' && exist) {
        exist.title = src.title; exist.icon = src.icon; exist.background = src.background; exist.nodes = src.nodes; exist.links = src.links;
        exist.history.push(snapshot(exist)); exist.future = []; exist.dirty = true; replaced++; last = exist;
        if (exist === S.cur) S.sel.clear();
      } else {
        const t = treeFromJSON(f.json, f.ns, uniqueName(f.ns, f.name));
        resetHistory(t); t.dirty = true; S.trees.push(t); added++; last = t;
      }
    } catch (e) { toast(f.name + ': ' + e.message); }
  }
  if (!S.trees.length) addTree('my_pack', 'skilltree', _('New tree'));
  if (last && last === S.cur && S.trees.includes(S.cur)) UI.refreshAll();
  else { S.cur = null; UI.selectTree(last || S.trees[0]); }
  const parts = [];
  if (added) parts.push(_('{n} added', { n: added }));
  if (replaced) parts.push(_('{n} replaced', { n: replaced }));
  if (merged) parts.push(_('{n} merged (the merged nodes are selected: drag them into place)', { n: merged }));
  toast(parts.join(' · '));
}

/** Copies every node and link of `src` into `dst` with a unique id prefix, and selects the new nodes. */
function mergeInto(dst, src, label) {
  const prefix = sanitize(label.replace(/\//g, '_')) + '_', map = new Map(), ids = [];
  for (const n of src.nodes.values()) {
    let id = prefix + n.id;
    while (dst.nodes.has(id)) id = prefix + id;
    map.set(n.id, id); ids.push(id);
    dst.nodes.set(id, { ...n, id });
  }
  for (const l of src.links.values()) {
    const a = map.get(l.from), b = map.get(l.to);
    dst.links.set(linkKey(a, b), { from: a, to: b, type: l.type });
  }
  S.sel = new Set(ids);
  dst.history.push(snapshot(dst)); dst.future = []; dst.dirty = true;
}

/* ---------- drag & drop (files, zips, folders) ---------- */
async function entriesToItems(entries) {
  const out = [];
  const walk = async (entry, path) => {
    if (entry.isFile) {
      const file = await new Promise((res, rej) => entry.file(res, rej));
      out.push({ path: path + entry.name, file, loose: false });
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      let batch;
      do {
        batch = await new Promise((res, rej) => reader.readEntries(res, rej));
        for (const e of batch) await walk(e, path + entry.name + '/');
      } while (batch.length);
    }
  };
  for (const e of entries) await walk(e, '');
  return out;
}
let dragDepth = 0;
window.addEventListener('dragenter', e => { e.preventDefault(); dragDepth++; document.body.classList.add('dragging'); });
window.addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; document.body.classList.remove('dragging'); } });
window.addEventListener('dragover', e => e.preventDefault());
window.addEventListener('drop', e => {
  e.preventDefault(); dragDepth = 0; document.body.classList.remove('dragging');
  // everything must be read synchronously from the event, before any await
  const dt = e.dataTransfer, items = [...(dt?.items || [])].filter(i => i.kind === 'file');
  const handlePromises = HAS_FS ? items.map(i => (i.getAsFileSystemHandle ? i.getAsFileSystemHandle().catch(() => null) : Promise.resolve(null))) : [];
  const entries = items.map(i => (i.webkitGetAsEntry ? i.webkitGetAsEntry() : null)).filter(Boolean);
  const files = [...(dt?.files || [])];
  (async () => {
    const handles = await Promise.all(handlePromises);
    const dir = handles.find(h => h && h.kind === 'directory');
    if (dir && isPristine()) { openFolder(dir); return; }       // direct-save mode only for a fresh session
    let list;
    if (entries.some(en => en.isDirectory)) list = await entriesToItems(entries);
    else list = files.map(f => ({ path: f.name, file: f, loose: true }));
    importItems(list);
  })();
});

/* ---------- saving ---------- */
async function saveAction() {
  if (!S.root && HAS_FS && S.trees.length) {      // nothing opened from a folder yet: pick where the unzipped datapack goes
    let dir;
    try { dir = await window.showDirectoryPicker({ mode: 'readwrite' }); } catch (e) { return; }
    S.root = dir; S.rootHasMeta = false;
    try { await dir.getFileHandle('pack.mcmeta'); S.rootHasMeta = true; } catch (e) { /* new folder */ }
    S.trees.forEach(t => { t.handle = null; });
  }
  if (S.root) {
    try { await saveToFolder(); toast(_('Saved into the datapack folder. In game: /reload')); }
    catch (e) { toast(_('Error while saving: {err}', { err: e.message })); }
  } else if (S.trees.length === 1) {
    download(S.cur.name.split('/').pop() + '.json', treeJSONText(S.cur)); S.cur.dirty = false;
    toast(_('Downloaded {file}. Put it at {path}', { file: S.cur.name.split('/').pop() + '.json', path: treePath(S.cur) }));
  } else { exportZip(); S.trees.forEach(t => { t.dirty = false; }); toast(_('Datapack downloaded (.zip). Put it in world/datapacks and run /reload.')); }
  UI.refreshAll();
}

/* ---------- validation ---------- */
function showProblems() {
  const t = S.cur;
  if (!t) return;
  const P = validateTree(t), body = $('#problemsBody');
  const count = s => P.filter(p => p.sev === s).length;
  body.innerHTML = P.length
    ? `<p class="muted">${esc(_('{e} errors · {w} warnings · {i} notes. Click one to jump to the node.', { e: count('error'), w: count('warn'), i: count('info') }))}</p><ul class="problems">${P.map((p, i) => `<li class="sev-${p.sev}" data-idx="${i}">${esc(p.msg)}</li>`).join('')}</ul>`
    : `<p style="color:var(--ok)">${esc(_('✔ No problems. Every node is reachable and all rules are met.'))}</p>`;
  body.onclick = e => {
    const li = e.target.closest('li'); if (!li) return;
    const p = P[+li.dataset.idx];
    if (p.node) { S.sel = new Set([p.node]); centerOn(p.node); UI.refreshInspector(); $('#dlgProblems').close(); }
  };
  $('#dlgProblems').showModal();
}

/* ---------- libraries and textures ---------- */
let libTarget = 'attrs';
function openLibs() {
  $('#libAttrs').value = JSON.stringify(S.libs.attrs, null, 2);
  $('#libItems').value = S.libs.items.join('\n');
  $('#texInfo').textContent = S.tex.size ? _('({n} textures loaded)', { n: S.tex.size }) : '';
  $('#dlgLibs').showModal();
}
/** Reads item textures from a list of {path, file} (folder input or directory handle walk). */
async function ingestTextures(list) {
  const items = [], blocks = [];
  for (const { path, file } of list) {
    if (!/\.png$/i.test(path)) continue;
    const m = /(?:^|\/)([^\/]+)\/textures\/(item|block)\/(.+)\.png$/i.exec(path);
    if (!m) continue;
    (m[2].toLowerCase() === 'item' ? items : blocks).push({ id: `${m[1]}:${m[3]}`, file });
  }
  const seen = new Set(items.map(i => i.id)), all = [...items, ...blocks.filter(b => !seen.has(b.id))].slice(0, 25000);
  let done = 0;
  for (let i = 0; i < all.length; i += 40) {
    await Promise.all(all.slice(i, i + 40).map(async ({ id, file }) => {
      try {
        const img = new Image(); img.src = URL.createObjectURL(file); await img.decode();
        if (img.naturalHeight <= img.naturalWidth * 2) S.tex.set(id, img); // skip tall animation sheets
        done++;
      } catch (e) { /* unreadable texture */ }
    }));
    $('#texInfo').textContent = _('Loading {a}/{b}…', { a: done, b: all.length });
  }
  S.libs.items = [...new Set([...S.libs.items, ...all.map(a => a.id)])];
  refreshDatalists(); saveLibs();
  $('#libItems').value = S.libs.items.join('\n');
  $('#texInfo').textContent = _('({n} textures loaded)', { n: S.tex.size });
  UI.refreshAll();
}
async function loadTextures() {
  if (HAS_FS) {
    let root;
    try { root = await window.showDirectoryPicker({ mode: 'read' }); } catch (e) { return; }
    const list = [];
    const walk = async (dir, path) => {
      for await (const [name, h] of dir.entries()) {
        if (h.kind === 'directory') await walk(h, path + name + '/');
        else if (/\.png$/i.test(name) && /\/textures\//.test(path + name)) list.push({ path: path + name, file: await h.getFile() });
      }
    };
    $('#texInfo').textContent = _('Reading…');
    await walk(root, root.name + '/');
    await ingestTextures(list);
  } else {
    $('#texInput').click();
  }
}

/* ---------- help ---------- */
function renderHelp() {
  const lines = [
    '<b>Open datapack folder</b>: choose the folder that contains <code>data/</code>. It reads <code>data/*/waifingskilltree/trees/*.json</code> and <b>Save</b> writes back to the same folder (Chrome/Edge). In other browsers (Brave, Firefox, Safari) the folder still opens, but Save downloads a .zip.',
    '<b>Open files</b>: pick one or several <code>.json</code> trees, <code>.zip</code> datapacks (a top-level folder inside the zip is fine) or <code>.jar</code> files. You can also drag and drop files, zips or folders onto the page. When something is already open you choose, per tree, to add it, replace the open one or merge it into the current tree.',
    '<b>Wheel</b> = zoom · <b>middle button</b> or <b>Space+drag</b> = move the view · <b>Shift</b> = add to selection · <b>Ctrl</b> = flip grid snapping for that action (free ↔ snap).',
    '<b>Add</b>: click empty space. <b>Shift+click</b> links the new node to the selected one (to draw chains quickly).',
    '<b>Connect</b>: click A then B; repeating on the same pair removes the link. A link of 250 or more is drawn as a silver jump, and from 500 as a violet portal (hidden in the preview until you reach it).',
    '<b>Mirror</b>: every change is replicated on the nodes that are symmetric about the origin.',
    'After saving, run <code>/reload</code> in the game.',
  ];
  $('#helpBody').innerHTML = lines.map(l => `<li>${_(l)}</li>`).join('');
}

/* ---------- language ---------- */
function setLang(code) {
  LANG = code === 'es' ? 'es' : 'en';
  try { localStorage.setItem(LANG_KEY, LANG); } catch (e) { /* ignore */ }
  document.documentElement.lang = LANG;
  $('#lang').value = LANG;
  applyI18n(); renderHelp(); refreshDatalists();
  setTool(S.tool); UI.refreshAll();
}

/* ---------- wiring ---------- */
$('#btnOpenDir').onclick = () => (HAS_FS ? openFolder() : $('#dirInput').click());
$('#btnOpenFiles').onclick = () => $('#fileInput').click();
$('#fileInput').onchange = e => { const f = [...e.target.files]; e.target.value = ''; importItems(f.map(file => ({ path: file.name, file, loose: true }))); };
$('#dirInput').onchange = e => {
  const f = [...e.target.files]; e.target.value = '';
  importItems(f.map(file => ({ path: file.webkitRelativePath || file.name, file, loose: false })));
};
$('#btnSave').onclick = saveAction;
$('#btnZip').onclick = () => { exportZip(); toast(_('Datapack downloaded. Put it in world/datapacks and run /reload.')); };
$('#btnUndo').onclick = undo; $('#btnRedo').onclick = redo;
$('#btnValidate').onclick = showProblems;
$('#btnLibs').onclick = openLibs;
$('#btnHelp').onclick = () => { renderHelp(); $('#dlgHelp').showModal(); };
$('#closeProblems').onclick = () => $('#dlgProblems').close();
$('#closeHelp').onclick = () => $('#dlgHelp').close();
$('#impCancel').onclick = () => $('#dlgImport').close();
$('#lang').onchange = e => setLang(e.target.value);
$('#bannerClose').onclick = () => { $('#banner').hidden = true; try { localStorage.setItem('wse_banner', '1'); } catch (e) { /* ignore */ } };
/* ---------- search ---------- */
function nodeHaystack(n) {
  const desc = Array.isArray(n.description) ? n.description.join(' ') : String(n.description || '');
  const attrs = n.attributes.map(a => a.attribute + ' ' + (attrInfo(a.attribute)?.name || '') + ' ' + (a.condition ? JSON.stringify(a.condition) : ''));
  return [n.id, n.title, desc, n.icon, n.kind, n.shape, n.start ? 'start' : '', n.color, ...attrs, ...n.commands, ...n.refund_commands, ...n.recipes,
    ...n.unlock_commands, n.group || '', ...(n.click_recipes || []), ...(n.unlock_trees || []), ...n.requires_advancements, ...(n.requires_nodes || []), ...(n.free_choice_if || []), ...(n.cap_nodes || []), ...n.requires_items.map(i => i.item), ...n.requires_attributes.map(a => a.attribute)].join(' ').toLowerCase();
}
function searchMatches(t, q) {
  const toks = q.toLowerCase().split(/\s+/).filter(Boolean), out = new Set();
  if (!toks.length) return out;
  for (const n of t.nodes.values()) { const h = nodeHaystack(n); if (toks.every(k => h.includes(k))) out.add(n.id); }
  return out;
}
let searchIdx = -1;
function updateSearch() {
  S.searchQ = $('#search').value.trim();
  const hits = S.cur && S.searchQ ? searchMatches(S.cur, S.searchQ) : new Set();
  $('#searchInfo').textContent = S.searchQ ? _('{n} found', { n: hits.size }) : '';
  searchIdx = -1; requestDraw();
}
function searchNext(step) {
  if (!S.cur || !S.searchQ) return;
  const ids = [...searchMatches(S.cur, S.searchQ)].sort();
  if (!ids.length) return;
  searchIdx = (searchIdx + step + ids.length) % ids.length;
  if (S.view.z < 0.6) S.view.z = 0.8;
  S.sel = new Set([ids[searchIdx]]); UI.refreshInspector(); centerOn(ids[searchIdx]);
  $('#searchInfo').textContent = _('{i} of {n}', { i: searchIdx + 1, n: ids.length });
}
$('#search').addEventListener('input', updateSearch);
$('#search').addEventListener('keydown', e => {
  if (e.key === 'Enter') { e.preventDefault(); searchNext(e.shiftKey ? -1 : 1); }
  else if (e.key === 'Escape') { $('#search').value = ''; updateSearch(); $('#search').blur(); }
});
window.addEventListener('keydown', e => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') { e.preventDefault(); $('#search').focus(); $('#search').select(); } });

/* ---------- grid: free / snap modes ---------- */
function setSnap(on, persist = true) {
  $('#snap').checked = !!on;
  $$('#gridMode button').forEach(b => b.classList.toggle('on', (b.dataset.snap === '1') === !!on));
  if (persist) saveGrid();
  requestDraw();
}
function saveGrid() { try { localStorage.setItem('wse_grid', JSON.stringify({ snap: $('#snap').checked, size: gridSize(), show: $('#showGrid').checked })); } catch (e) { /* ignore */ } }
(function initGrid() {
  let g = {}; try { g = JSON.parse(localStorage.getItem('wse_grid') || '{}'); } catch (e) { /* ignore */ }
  if (g.size) $('#snapSize').value = g.size;
  if (g.show === false) $('#showGrid').checked = false;
  $('#gridPresets').innerHTML = [5, 10, 20, 25, 40, 50].map(n => `<button data-gs="${n}">${n}</button>`).join('');
  $('#gridPresets').onclick = e => { const b = e.target.closest('button'); if (!b) return; $('#snapSize').value = b.dataset.gs; setSnap(true); };
  $('#gridMode').onclick = e => { const b = e.target.closest('button'); if (b) setSnap(b.dataset.snap === '1'); };
  $('#snapSize').addEventListener('input', () => { saveGrid(); requestDraw(); });
  $('#showGrid').addEventListener('change', () => { saveGrid(); requestDraw(); });
  setSnap(g.snap !== undefined ? g.snap : true, false);   // snapping is the default; "Free" is one click away
})();
window.addEventListener('keydown', e => { if (e.key.toLowerCase() === 'g' && !e.ctrlKey && !e.metaKey && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || '')) setSnap(!$('#snap').checked); });

$('#toolBtns').onclick = e => { const b = e.target.closest('button'); if (b) setTool(b.dataset.tool); };
$('#mirror').onchange = requestDraw; $('#newShape').onchange = requestDraw;
$('#btnFit').onclick = fitView;
$('#btnColorize').onclick = () => { if (S.cur && colorizeBranches(S.cur)) commit(); };
$('#btnUncolor').onclick = () => { if (!S.cur) return; S.cur.nodes.forEach(n => { n.color = ''; }); commit(); };
$('#tabs').onclick = e => {
  if (e.target.closest('#tabAdd')) { $('#dlgNewTree').showModal(); return; }
  const tab = e.target.closest('.tab[data-idx]'); if (tab) UI.selectTree(S.trees[+tab.dataset.idx]);
};
$('#ntCancel').onclick = () => $('#dlgNewTree').close();
$('#ntOk').onclick = () => {
  const t = addTree($('#ntNs').value, $('#ntName').value, $('#ntTitle').value);
  if (t) { $('#dlgNewTree').close(); UI.selectTree(t); }
};
$('#libAttrsFile').onclick = () => { libTarget = 'attrs'; $('#libInput').click(); };
$('#libItemsFile').onclick = () => { libTarget = 'items'; $('#libInput').click(); };
$('#libInput').onchange = async e => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f) return;
  try {
    const text = await f.text();
    if (libTarget === 'attrs') {
      const merged = new Map(parseAttrLib($('#libAttrs').value || '[]').map(a => [a.id, a]));
      parseAttrLib(text).forEach(a => merged.set(a.id, a));
      $('#libAttrs').value = JSON.stringify([...merged.values()], null, 2);
    } else $('#libItems').value = [...new Set([...parseItemLib($('#libItems').value), ...parseItemLib(text)])].join('\n');
    toast(_('Imported. Press "Save and close" to apply.'));
  } catch (err) { toast(_('Invalid file: {err}', { err: err.message })); }
};
$('#libAttrsSample').onclick = () => download('attributes_example.json', JSON.stringify([
  { id: 'modid:example_attribute', name: 'Readable name', amount: 1, operation: 'add' },
  { id: 'other_mod:other_attribute', name: 'Another attribute', amount: 0.1, operation: 'mult_base' },
], null, 2));
$('#btnTextures').onclick = loadTextures;
$('#texInput').onchange = e => {
  const f = [...e.target.files]; e.target.value = '';
  $('#texInfo').textContent = _('Reading…');
  ingestTextures(f.map(file => ({ path: file.webkitRelativePath || file.name, file })));
};
$('#libReset').onclick = () => { if (confirm(_('Reset the libraries to vanilla Minecraft values?'))) { try { localStorage.removeItem('wse_libs'); } catch (e) { /* ignore */ } loadLibs(); openLibs(); } };
$('#libOk').onclick = () => {
  try {
    S.libs.attrs = parseAttrLib($('#libAttrs').value || '[]');
    S.libs.items = parseItemLib($('#libItems').value);
    saveLibs(); refreshDatalists(); $('#dlgLibs').close(); renderInspector(); toast(_('Libraries saved.'));
  } catch (e) { toast(_('Invalid JSON: {err}', { err: e.message })); }
};
window.addEventListener('beforeunload', e => { if (S.trees.some(t => t.dirty)) { e.preventDefault(); e.returnValue = ''; } });

/* ---------- startup ---------- */
loadLibs();
{
  let dismissed = false;
  try { dismissed = localStorage.getItem('wse_banner') === '1'; } catch (e) { /* ignore */ }
  $('#banner').hidden = HAS_FS || dismissed;
}
const first = addTree('my_pack', 'skilltree', _('New tree'));
first.dirty = false;
S.cur = first;
setTool('view');
resizeCanvas();
setLang(LANG);

/* ---------- side panel: every group but "Tool" starts folded; click a heading to open or fold it ---------- */
(() => {
  let open = {};
  try { open = JSON.parse(localStorage.getItem('wst_groups') || '{}'); } catch (e) { open = {}; }
  $$('#tools .group').forEach((g, i) => {
    const h = $('h3', g); if (!h) return;
    const key = h.textContent.trim() || 'g' + i;
    if (i === 0) return;                       // the Tool group always stays open
    g.classList.add('foldable');
    g.classList.toggle('closed', !open[key]);
    h.addEventListener('click', () => {
      g.classList.toggle('closed');
      open[key] = !g.classList.contains('closed');
      try { localStorage.setItem('wst_groups', JSON.stringify(open)); } catch (e) { /* no storage */ }
    });
  });
})();
