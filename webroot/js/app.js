//  QshopWebUI — 前端主控脚本
//  功能: 顶部导航 / 登录权限 / 模态框 / 商店查询 / 数据生成与强制终止 / 导入导出

(function (global) {
  'use strict';

  const ERR_IMG_PATH = 'errormt/errormt.png';

  //   前端日志工具（前端控制台打印所有 API 请求/响应 + UI 操作）
  //   - 支持 INFO / WARN / ERROR 级别
  //   - 自动打印时间戳 + 耗时
  const LOG_LEVEL = (global.__LOG_LEVEL__ || 'INFO').toUpperCase();
  const LOG_LEVELS = { SILENT: 0, ERROR: 1, WARN: 2, INFO: 3 };
  function ts() {
    const d = new Date();
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  }
  const uiLogger = {
    info(msg, ...extra) {
      if (LOG_LEVELS[LOG_LEVEL] < LOG_LEVELS.INFO) return;
      const line = `[${ts()}] [INFO] ${msg}`;
      extra.length ? console.log(line, ...extra) : console.log(line);
    },
    warn(msg, ...extra) {
      if (LOG_LEVELS[LOG_LEVEL] < LOG_LEVELS.WARN) return;
      const line = `[${ts()}] [WARN] ${msg}`;
      extra.length ? console.warn(line, ...extra) : console.warn(line);
    },
    error(msg, ...extra) {
      if (LOG_LEVELS[LOG_LEVEL] < LOG_LEVELS.ERROR) return;
      const line = `[${ts()}] [ERROR] ${msg}`;
      extra.length ? console.error(line, ...extra) : console.error(line);
    }
  };
  // apiFetch：统一 API 请求封装（带日志 + 错误处理）
  async function apiFetch(url, options) {
    const t0 = Date.now();
    const method = (options && options.method) || 'GET';
    uiLogger.info(`→ ${method} ${url}` + (options && options.body ? ` (body: ${String(options.body).substring(0, 160)})` : ''));
    try {
      const response = await fetch(url, options);
      const contentType = response.headers ? response.headers.get('content-type') : '';
      const isJson = contentType && contentType.indexOf('application/json') >= 0;
      const data = isJson ? await response.json() : await response.text();
      if (response.ok) {
        uiLogger.info(`← ${method} ${url} ${response.status} OK (${Date.now() - t0}ms)`);
      } else {
        uiLogger.warn(`← ${method} ${url} ${response.status} (${Date.now() - t0}ms)`);
      }
      return data;
    } catch (err) {
      uiLogger.error(`✗ ${method} ${url} 请求失败: ${err.message}`);
      throw err;
    }
  }

  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  // —— 预设图片路径（文本内容缺失时自动显示） ——
  const FALLBACK_IMG_PATH = 'errormt/errormt.png';

  // 判断文本内容是否有效（空 / undefined / 'Unknown' / '-' 视为缺失）
  function hasValidText(val) {
    if (val === undefined || val === null) return false;
    const s = String(val).trim();
    if (s === '' || s === '-' || s.toLowerCase() === 'unknown' || s.toLowerCase() === 'unknown item') return false;
    return true;
  }

  // 在容器中渲染文本或（缺失时）自动替换为预设图片
  //   textValue — 要显示的文本，若无效则显示图片
  //   container — 父容器节点
  //   opts — { tag: 'div'/'small'/'span', class: 'xxx', prefix: '' , imgClass: '' }
  function renderTextOrImage(textValue, container, opts) {
    opts = opts || {};
    const tagName = opts.tag || 'div';
    const cls = opts.class || '';
    const prefix = opts.prefix || '';
    const imgClass = opts.imgClass || 'text-missing-img';

    if (hasValidText(textValue)) {
      const node = el(tagName, { class: cls, text: prefix + String(textValue) });
      if (container) container.appendChild(node);
      return node;
    } else {
      const node = el(tagName, { class: cls + ' text-missing' });
      node.style.opacity = '0';
      const img = el('img', {
        class: imgClass,
        src: FALLBACK_IMG_PATH,
        alt: 'missing-text',
        loading: 'lazy',
        decoding: 'async',
        onerror: function () {
          this.style.display = 'none';
          node.textContent = '(内容缺失)';
          if (container) container.classList.add('text-missing-visible');
        }
      });
      node.appendChild(img);
      // 淡入过渡效果（与主 CSS .text-missing transition 配合）
      setTimeout(function () {
        node.style.opacity = '1';
        node.classList.add('text-missing-visible');
      }, 10);
      if (container) container.appendChild(node);
      return node;
    }
  }

  // 优化后的 el：属性处理分阶段，children 使用 DocumentFragment 批量挂载
  function el(tag, opts, children) {
    const node = document.createElement(tag);
    if (opts) {
      // 直接属性赋值（比 setAttribute 快）
      if (opts.class !== undefined) node.className = opts.class;
      if (opts.text !== undefined) node.textContent = opts.text;
      if (opts.html !== undefined) node.innerHTML = opts.html;
      if (opts.id !== undefined) node.id = opts.id;
      if (opts.value !== undefined) node.value = opts.value;
      if (typeof opts.style === 'object' && opts.style !== null) Object.assign(node.style, opts.style);
      else if (typeof opts.style === 'string') node.setAttribute('style', opts.style);
      // 其余属性 + 事件处理
      for (const k in opts) {
        if (k === 'class' || k === 'text' || k === 'html' || k === 'style' || k === 'id' || k === 'value') continue;
        const v = opts[k];
        if (k.indexOf('on') === 0 && typeof v === 'function') {
          node.addEventListener(k.slice(2), v, false);
        } else if (v !== undefined && v !== null && v !== false) {
          node.setAttribute(k, v === true ? '' : v);
        }
      }
    }
    if (children) {
      // 使用 DocumentFragment 一次性挂载，减少多次 reflow
      const frag = document.createDocumentFragment();
      const arr = Array.isArray(children) ? children : [children];
      for (let i = 0; i < arr.length; i++) {
        const c = arr[i];
        if (c == null || c === false) continue;
        if (c.nodeType) frag.appendChild(c);
        else if (Array.isArray(c)) {
          for (let j = 0; j < c.length; j++) { const x = c[j]; if (x && x.nodeType) frag.appendChild(x); }
        } else frag.appendChild(document.createTextNode(String(c)));
      }
      node.appendChild(frag);
    }
    return node;
  }

  // 批量渲染：将 items 通过 renderFn 渲染为节点，一次性加入容器（关键优化）
  function renderList(container, items, renderFn) {
    if (!container || !items || items.length === 0) return;
    const frag = document.createDocumentFragment();
    for (let i = 0; i < items.length; i++) {
      const node = renderFn(items[i], i);
      if (node && node.nodeType) frag.appendChild(node);
    }
    container.appendChild(frag);
  }

  const APICache = {
    store: new Map(), // key -> { data, timestamp }
    TTL: 60 * 1000,    // 默认 60 秒缓存
    MAX: 40,

    _key(path, params) { return path + (params ? '|' + JSON.stringify(params) : ''); },
    get(path, params) {
      const entry = this.store.get(this._key(path, params));
      if (!entry) return null;
      if (Date.now() - entry.timestamp > this.TTL) { this.store.delete(this._key(path, params)); return null; }
      return entry.data;
    },
    set(path, params, data) {
      // LRU：超出 MAX 时清理最早的 10 个
      if (this.store.size >= this.MAX) {
        const keys = this.store.keys();
        for (let i = 0; i < 10; i++) { const n = keys.next(); if (n.done) break; this.store.delete(n.value); }
      }
      this.store.set(this._key(path, params), { data, timestamp: Date.now() });
    },
    clear(partialPath) {
      if (!partialPath) { this.store.clear(); return; }
      for (const k of this.store.keys()) { if (k.indexOf(partialPath) === 0) this.store.delete(k); }
    }
  };

  // 每次进入某 tab 时，若已缓存过该 tab 容器，则直接切换 display；
  // 对数据驱动页面（browse、shops 等），首次渲染后直接隐藏，不再重建节点。
  const TabCache = new Map(); // tab -> { root: Element, needsRefresh: boolean }

  const state = {
    currentTab: 'home',
    isAdmin: false,
    isLoggedIn: false,
    username: null,
    role: 'guest',
    playerName: null,
    playerBalance: null,
    seedRunning: false,
    seedPoller: null,
    // QSFilterPlugin 连接状态
    qsfilter: { enabled: false, connected: false, qs_available: false, base_url: '', latency_ms: 0, last_error: null },
    // 加载进度状态
    loading: {
      startTime: Date.now(), progress: 0, stage: 0,
      stageNames: ['连接服务器中', '同步商店数据', '资源加载完成'],
      minShowMs: 200, slowDetectMs: 3500, done: false
    }
  };

  //  加载动画管理系统 (Loader)
  //  - DOM 加载后立即可见 (HTML 直接写入)
  //  - 实时进度同步（基于 API 请求完成情况）
  //  - 自动慢网络检测 + 超时保护
  const Loader = {
    _slowTimer: null,
    _hideTimer: null,

    updateProgress(percent, message) {
      const bar = document.getElementById('loader-progress-bar');
      const txt = document.getElementById('loader-progress-text');
      const p = Math.max(0, Math.min(100, Math.round(percent)));
      if (bar) bar.style.width = p + '%';
      if (txt) txt.textContent = p + '%' + (message ? ' - ' + message : ' - 正在加载...');
      state.loading.progress = p;
    },

    setStage(index) {
      const stats = document.getElementById('loader-stats');
      if (!stats) return;
      const items = stats.querySelectorAll('.loader-stat');
      items.forEach((it, i) => {
        it.classList.remove('active', 'done');
        if (i < index) it.classList.add('done');
        else if (i === index) it.classList.add('active');
      });
      state.loading.stage = index;
    },

    setSlow(slow) {
      const o = document.getElementById('loader-overlay');
      if (o) {
        if (slow) o.classList.add('slow-net');
        else o.classList.remove('slow-net');
      }
    },

    updateTip(text) {
      const t = document.getElementById('loader-tip');
      if (t) t.textContent = text;
    },

    // --- 资源预加载：异步并行请求，使用 Link preload 机制 ----
    async preloadResources() {
      const promises = [];
      // 1) 预加载主 API 数据（商店列表），这是最耗时的
      promises.push((async () => {
        try {
          if (typeof QSDB !== 'undefined' && QSDB.getShops) {
            await QSDB.getShops({ pageSize: 20, page: 1 });
            return true;
          }
        } catch (e) { return false; }
        return true;
      })());

      // 2) 预加载统计 / 健康检查 / 物品列表（并行）
      promises.push((async () => {
        try {
          if (typeof QSDB !== 'undefined' && QSDB.getStats) {
            await QSDB.getStats();
          }
        } catch (e) {}
        return true;
      })());
      promises.push((async () => {
        try {
          if (typeof QSDB !== 'undefined' && QSDB.getItems) {
            await QSDB.getItems({ pageSize: 30 });
          }
        } catch (e) {}
        return true;
      })());

      // 3) 预加载页面内可能出现的物品贴图（利用 <link rel="preload">）
      //    - 只预加载前 10 张，剩余在滚动时懒加载
      promises.push((async () => {
        try {
          const sampleItems = ['item/diamond.png', 'item/gold_ingot.png',
                                 'item/iron_ingot.png', 'item/coal.png',
                                 'item/emerald.png', 'item/stone.png',
                                 'item/soul_sand.png', 'item/netherite_ingot.png'];
          sampleItems.forEach((src) => {
            if (!document.querySelector('link[href="' + src + '"]')) {
              const link = document.createElement('link');
              link.rel = 'preload';
              link.as = 'image';
              link.href = src;
              // 忽略加载失败 - 不影响主流程
              link.addEventListener('error', () => { link.remove(); }, { once: true });
              document.head.appendChild(link);
            }
          });
        } catch (e) {}
        return true;
      })());

      // 进度平滑推进（伪进度，给用户可见的进度反馈）
      let smooth = 10;
      const smoothTimer = setInterval(() => {
        if (smooth < 50) {
          smooth += 1.5;
          this.updateProgress(smooth, '正在连接服务器...');
        } else clearInterval(smoothTimer);
      }, 120);

      await Promise.all(promises);
      clearInterval(smoothTimer);
      this.updateProgress(65, '数据同步完成');
      this.setStage(1);
      return true;
    },

    // --- 核心：启动加载序列 ----
    async start() {
      // 立即更新 UI（HTML 已经写入 DOM，此时只需要标记 stage 0）
      this.setStage(0);
      this.updateProgress(5, '初始化页面');

      // 慢网络检测：超时后切换到 "较慢" 模式
      this._slowTimer = setTimeout(() => {
        if (!state.loading.done) {
          this.setSlow(true);
          this.updateTip('提示：当前网络较慢，仍在加载中，请耐心等候');
        }
      }, state.loading.slowDetectMs);

      // 绝对保底：最长 12 秒强制显示页面（防止因 API 异常导致永久卡加载）
      setTimeout(() => {
        if (!state.loading.done) {
          console.warn('Loader: 加载超时，强制显示页面');
          this.hide(true);
        }
      }, 12000);

      // 执行预加载
      try {
        await this.preloadResources();
      } catch (e) {
        console.warn('Loader: 预加载异常', e);
      }

      // 完成最后阶段并通知 hide
      this.setStage(2);
      this.updateProgress(100, '加载完成');
      state.loading.done = true;

      // 最少显示 500ms，避免闪烁
      const elapsed = Date.now() - state.loading.startTime;
      const wait = Math.max(0, state.loading.minShowMs - elapsed);
      setTimeout(() => this.hide(false), wait);
    },

    hide(forced) {
      const o = document.getElementById('loader-overlay');
      if (!o) return;
      o.classList.add('loader-hidden');
      if (this._slowTimer) clearTimeout(this._slowTimer);
      // 动画结束后移除 DOM，释放内存
      setTimeout(() => {
        if (o && o.parentNode) o.parentNode.removeChild(o);
      }, 600);
    }
  };

  // 暴露到全局（便于调试）
  global.__QshopLoader = Loader;

  //  QSFilterPlugin 状态刷新（定时查询，显示在顶部导航）
  async function refreshQsFilterStatus() {
    try {
      const r = await QSDB.getQsFilterStatus();
      if (r && r.success) {
        state.qsfilter = {
          enabled: !!r.enabled,
          connected: !!r.connected,
          qs_available: !!r.qs_available,
          base_url: r.base_url || '',
          latency_ms: r.latency_ms || 0,
          last_error: r.last_error
        };
      } else {
        state.qsfilter.last_error = (r && r.error) || '状态查询失败';
      }
    } catch (e) {
      state.qsfilter.last_error = e.message || '网络错误';
    }
    // 触发导航栏重绘
    if (typeof TopNav !== 'undefined' && TopNav.render) {
      try { TopNav.render(); } catch (ignore) {}
    }
  }

  // 立即刷新 + 每 15 秒自动刷新
  setTimeout(() => { refreshQsFilterStatus(); }, 300);
  setInterval(() => { refreshQsFilterStatus(); }, 15000);

  //  模态框系统
  const Modal = {
    show(opts) {
      // opts: { title, body (HTML/node), danger, confirmText, cancelText, onConfirm, onCancel, showCancel }
      const overlay = el('div', { class: 'modal-overlay' });
      const panel = el('div', { class: 'modal-panel' });

      // header
      const header = el('div', { class: 'modal-header' + (opts.danger ? ' danger' : ''), text: opts.title || '提示' });
      panel.appendChild(header);

      // body
      const body = el('div', { class: 'modal-body' });
      if (opts.body instanceof Node) body.appendChild(opts.body);
      else if (typeof opts.body === 'string') body.innerHTML = opts.body;
      panel.appendChild(body);

      // footer
      const footer = el('div', { class: 'modal-footer' });
      const showCancel = opts.showCancel !== false;
      if (showCancel) {
        const cancelBtn = el('button', {
          class: 'neo-btn',
          text: opts.cancelText || '取消',
          onclick: () => {
            if (typeof opts.onCancel === 'function') {
              try { opts.onCancel(); } catch (e) {}
            }
            close();
          }
        });
        footer.appendChild(cancelBtn);
      }
      const confirmBtn = el('button', {
        class: 'neo-btn' + (opts.danger ? ' danger' : ' primary'),
        text: opts.confirmText || '确定'
      });
      confirmBtn.addEventListener('click', () => {
        let result = true;
        if (typeof opts.onConfirm === 'function') {
          try { result = opts.onConfirm(close); } catch (e) { result = true; }
        }
        // 如果 onConfirm 返回 false 表示异步: 由调用方自己控制关闭
        if (result !== false) close();
      });
      footer.appendChild(confirmBtn);
      panel.appendChild(footer);
      overlay.appendChild(panel);

      // Esc 关闭 / Enter 确认
      function onKey(e) {
        if (e.key === 'Escape') { close(); }
        else if (e.key === 'Enter') { confirmBtn.click(); }
      }
      document.addEventListener('keydown', onKey);
      // 点击遮罩关闭
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) close();
      });

      function close() {
        document.removeEventListener('keydown', onKey);
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
      }

      const modalRoot = $('#modal-root');
      (modalRoot || document.body).appendChild(overlay);

      // 返回一个可用于关闭的对象
      return {
        close: close,
        setTitle: (t) => { header.textContent = t; },
        setBody: (htmlOrNode) => { body.innerHTML = ''; if (htmlOrNode instanceof Node) body.appendChild(htmlOrNode); else body.innerHTML = htmlOrNode; },
        setConfirmText: (t) => { confirmBtn.textContent = t; },
        setCancelText: (t) => { cancelBtn && (cancelBtn.textContent = t); },
        setProcessing: (t) => { confirmBtn.textContent = t || '处理中...'; confirmBtn.disabled = true; if (showCancel) footer.querySelector('.neo-btn').disabled = true; },
        setDone: (t) => { confirmBtn.textContent = t || '完成'; confirmBtn.disabled = false; }
      };
    },

    confirm(opts) {
      return this.show(opts);
    }
  };

  //  Toast 提示系统
  const Toast = {
    show(message, type) {
      const t = type || 'info';
      const icons = { success: '✓', error: '✕', warning: '!', info: 'i' };
      const item = el('div', { class: 'toast-item ' + t }, [
        el('span', { class: 'toast-icon', text: icons[t] || 'i' }),
        el('span', { text: message })
      ]);
      const root = $('#toast-root') || document.body;
      root.appendChild(item);
      setTimeout(() => {
        item.style.transition = 'opacity 400ms';
        item.style.opacity = '0';
        setTimeout(() => { if (item.parentNode) item.parentNode.removeChild(item); }, 400);
      }, 5000);
    }
  };

  // ============================================================
  // 交易记录导出 / QuickShop 历史交易导入（统计中心 + 数据导入导出页共用）
  // ============================================================

  function tradesToCsv(trades) {
    const head = ['时间', '类型', '来源', '玩家', '物品', '材质', '份数', '物品数量', '单价', '总额', '税收', '商店ID', '店主', '玩家在线'];
    const cell = function (v) {
      const s = String(v == null ? '' : v);
      return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const pad = function (n) { return (n < 10 ? '0' : '') + n; };
    const srcLabel = function (src) { return src === 'game' ? '游戏内' : src === 'history' ? '历史导入' : '网页'; };
    const rows = trades.map(function (t) {
      const d = new Date(Number(t.t) || 0);
      const time = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
      return [time, t.type === 'BUY' ? '购买' : '收购', srcLabel(t.source), t.player, t.item, t.material,
        t.amount, t.items, t.unit_price, t.total, t.tax == null ? '' : t.tax, t.shop_id, t.owner, t.online ? '是' : '否'];
    });
    return '\uFEFF' + [head].concat(rows).map(function (r) { return r.map(cell).join(','); }).join('\r\n');
  }

  // 导出交易记录 CSV（cache 可选：已有明细时直接传，避免重复请求）
  async function exportTradesCsv(cache) {
    let trades = cache;
    if (!trades || !trades.length) {
      const r = await QSDB.getTrades();
      if (!r || !r.success) { Toast.show('读取失败: ' + ((r && r.error) || '需要管理员登录'), 'error'); return; }
      trades = r.trades || [];
    }
    if (!trades.length) { Toast.show('暂无交易记录', 'warning'); return; }
    const blob = new Blob([tradesToCsv(trades)], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'qshop-trades-' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
    Toast.show('已导出 ' + trades.length + ' 条交易记录', 'success');
  }

  // 导出全部玩家余额 CSV（管理员）
  async function exportBalancesCsv() {
    Toast.show('正在读取全部玩家余额...', 'info');
    const r = await QSDB.getBalances();
    if (!r || !r.success) { Toast.show('读取失败: ' + ((r && r.error) || '需要管理员登录'), 'error'); return; }
    const list = r.players || [];
    if (!list.length) { Toast.show('没有可导出的玩家数据', 'warning'); return; }
    const cell = function (v) { const s = String(v == null ? '' : v); return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
    const head = ['玩家', 'UUID', '余额'];
    const rows = list.map(function (p) { return [p.name, p.uuid, (Number(p.balance) || 0).toFixed(2)]; });
    const csv = '\uFEFF' + [head].concat(rows).map(function (r2) { return r2.map(cell).join(','); }).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'qshop-balances-' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
    Toast.show('已导出 ' + list.length + ' 名玩家的余额（合计 ' + (Number(r.sum) || 0).toLocaleString() + '）', 'success');
  }

  // 导出移除（删除）商店记录 CSV（管理员）
  async function exportShopRemovalsCsv() {
    const r = await QSDB.getShopRemovals();
    if (!r || !r.success) { Toast.show('读取失败: ' + ((r && r.error) || '需要管理员登录'), 'error'); return; }
    const list = r.removals || [];
    if (!list.length) { Toast.show('暂无移除商店记录（可点「导入 QuickShop 历史交易」找回历史记录）', 'warning'); return; }
    const cell = function (v) { const s = String(v == null ? '' : v); return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
    const pad = function (n) { return (n < 10 ? '0' : '') + n; };
    const head = ['时间', '来源', '操作者', '原因', '商店ID', '店主', '物品', '单价', '世界', 'X', 'Y', 'Z'];
    const rows = list.map(function (x) {
      const d = new Date(Number(x.t) || 0);
      const time = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
      return [time, x.source === 'history' ? '历史导入' : '实时记录', x.player, x.reason, x.shop_id, x.owner, x.item, x.price, x.world, x.x, x.y, x.z];
    });
    const csv = '\uFEFF' + [head].concat(rows).map(function (r2) { return r2.map(cell).join(','); }).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'qshop-shop-removals-' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
    Toast.show('已导出 ' + list.length + ' 条移除商店记录', 'success');
  }

  // 导入 QuickShop 历史交易（自动 /qs export + 解析导入）；onDone 为成功后的刷新回调
  function importQsHistory(onDone) {
    Modal.confirm({
      title: '导入 QuickShop 历史交易',
      danger: false,
      body: '<div>将自动执行 <b>/qs export</b> 导出 QuickShop 数据库，并导入两类历史数据：<b>购买 / 收购记录</b>（交易统计）与<b>商店移除记录</b>（审计导出）。</div>'
        + '<div class="hint" style="margin-top:6px">统计功能上线之前发生的游戏内交易也会被一并找回；重复导入会自动去重。</div>',
      confirmText: '开始导入',
      onConfirm: async function () {
        Toast.show('正在导出 QuickShop 数据并导入，请稍候...', 'info');
        const r = await QSDB.importTradeHistory();
        if (r && r.success) {
          const parts = [];
          if (r.imported) parts.push(r.imported + ' 条历史交易');
          if (r.removals_imported) parts.push(r.removals_imported + ' 条商店移除记录');
          let msg = parts.length ? ('导入完成：新增 ' + parts.join('、')) : '导入完成：没有新的历史数据';
          if (r.skipped) msg += '（跳过已有 ' + r.skipped + ' 条交易）';
          if (!r.fresh) msg += '（使用了已有导出包）';
          Toast.show(msg, 'success');
          if (typeof onDone === 'function') onDone();
        } else {
          Toast.show('导入失败: ' + ((r && r.error) || '未知错误'), 'error');
        }
      }
    });
  }

  //  服务器名称（config.yml 的 server-name）：显示在浏览器标题、导航栏、首页大标题
  let siteServerName = '';
  let siteServerSubtitle = '';
  function applySiteBrand() {
    try {
      const raw = (siteServerName || '').trim();
      const sub = (siteServerSubtitle || '').trim();
      if (!raw && !sub) return; // 未配置：保持默认
      if (raw) {
        document.title = raw + ' — Minecraft 商店';
        const logo = document.querySelector('.topnav-logo');
        if (logo) logo.textContent = raw;
        const loader = document.querySelector('.loader-title');
        if (loader) loader.textContent = raw;
        const heroTitle = document.querySelector('.home-hero-title');
        if (heroTitle) heroTitle.textContent = raw;
      }
      if (sub) {
        const heroSub = document.querySelector('.home-hero-subtitle');
        if (heroSub) heroSub.textContent = sub;
      }
    } catch (e) { }
  }

  //  页面可见性：config.yml pages.hide-* 为默认，网页后台「数据管理」可覆盖
  let hiddenPages = [];
  const PAGE_LABELS = { home: '首页', buy: '购买界面', sell: '收购界面', browse: '物品浏览', shops: '商店浏览', stats: '信息统计' };
  function applyPageVisibility() {
    try {
      let firstVisible = null;
      $$('#topnav-tabs .topnav-tab').forEach(function (btn) {
        const tab = btn.dataset.tab;
        if (!PAGE_LABELS[tab]) return;
        if (!state.isAdmin && hiddenPages.indexOf(tab) >= 0) {
          btn.style.display = 'none';
        } else {
          btn.style.display = '';
          if (!firstVisible) firstVisible = tab;
        }
      });
      // 当前停留页面被隐藏 → 跳到第一个可见页面
      const cur = state.currentTab === 'items' ? 'browse' : state.currentTab;
      if (!state.isAdmin && PAGE_LABELS[cur] && hiddenPages.indexOf(cur) >= 0 && firstVisible) {
        switchTab(firstVisible);
      }
    } catch (e) { }
  }

  //  顶部导航栏渲染
  const TopNav = {
    render() {
      // 页面可见性（隐藏配置）
      try { applyPageVisibility(); } catch (e) { }
      // Tab 高亮
      $$('#topnav-tabs .topnav-tab').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.tab === state.currentTab);
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
      });

      // 管理员可见的 tab
      $$('.admin-only').forEach((elNode) => {
        elNode.style.display = state.isAdmin ? '' : 'none';
      });

      // QSFilter 连接状态
      const qsStatusEl = $('#qsfilter-status');
      if (qsStatusEl && state.qsfilter) {
        const qs = state.qsfilter;
        const connected = qs.connected && qs.qs_available;
        const statusText = !qs.enabled
          ? '插件未启用'
          : connected
            ? ('已连接 (' + (qs.latency_ms || 0) + ' ms)')
            : qs.connected
              ? 'QS 未就绪'
              : '未连接';
        const statusColor = !qs.enabled
          ? '#6b7280'
          : connected
            ? '#10b981'
            : '#ef4444';

        qsStatusEl.innerHTML = '';
        const indicator = el('div', {
          class: 'status-dot',
          title: qs.enabled
            ? (connected
                ? ('QSFilterPlugin: ' + qs.base_url)
                : (qs.last_error || '连接失败'))
            : '已禁用（使用本地数据库）',
          style: {
            width: '10px', height: '10px', borderRadius: '50%',
            backgroundColor: statusColor, display: 'inline-block',
            boxShadow: '0 0 0 2px rgba(255,255,255,0.8), 0 0 8px ' + statusColor,
            verticalAlign: 'middle', marginRight: '6px'
          }
        });
        qsStatusEl.appendChild(indicator);
        qsStatusEl.appendChild(el('span', {
          text: 'MC: ' + statusText,
          style: { fontSize: '12px', fontWeight: '600', color: '#374151' }
        }));
      }

      // 登录按钮区
      const auth = $('#topnav-auth');
      if (!auth) return;
      auth.innerHTML = '';

      if (state.isLoggedIn && state.username) {
        const info = el('div', { class: 'auth-info' + (state.isAdmin ? ' admin' : '') }, [
          document.createTextNode('👤 ' + state.username),
          el('span', { class: 'role-badge', text: state.role.toUpperCase() })
        ]);
        auth.appendChild(info);

        const logoutBtn = el('button', {
          class: 'neo-btn',
          text: '退出登录',
          onclick: async () => {
            const r = await QSDB.logout();
            state.isLoggedIn = false;
            state.isAdmin = false;
            state.username = null;
            state.role = 'guest';
            this.render();
            if (state.currentTab === 'admin' || state.currentTab === 'data' || state.currentTab === 'backup' || state.currentTab === 'monitor' || state.currentTab === 'announce') {
              switchTab('home');
            }
            Toast.show('已退出登录', 'info');
          }
        });
        auth.appendChild(logoutBtn);
      } else if (state.playerName) {
        const info = el('div', { class: 'auth-info player' }, [
          document.createTextNode('🎮 ' + state.playerName),
          el('span', { class: 'role-badge', text: state.playerBalance != null ? ('$' + state.playerBalance) : '玩家' })
        ]);
        auth.appendChild(info);
        const logoutBtn = el('button', {
          class: 'neo-btn',
          text: '退出',
          onclick: async () => {
            try { await QSDB.logout(); } catch (e) { }
            state.playerName = null;
            state.playerBalance = null;
            this.render();
            Toast.show('已退出玩家登录', 'info');
          }
        });
        auth.appendChild(logoutBtn);
      } else {
        const gameLoginBtn = el('button', {
          class: 'neo-btn',
          text: '🎮 游戏登录',
          onclick: () => showLoginModal('player')
        });
        const loginBtn = el('button', {
          class: 'neo-btn primary',
          text: '管理员登录',
          onclick: () => showLoginModal('admin')
        });
        auth.appendChild(gameLoginBtn);
        auth.appendChild(loginBtn);
      }
    }
  };

  function showLoginModal(mode) {
    mode = mode === 'player' ? 'player' : 'admin';
    const isPlayer = mode === 'player';

    const form = el('div', { class: 'auth-form' });
    const userLabel = el('label');
    userLabel.appendChild(el('span', { text: isPlayer ? '游戏 ID' : '用户名' }));
    const userInput = el('input', { type: 'text', placeholder: isPlayer ? '请输入游戏 ID（同 AuthMe 账号）' : '请输入用户名' });
    userLabel.appendChild(userInput);

    const passLabel = el('label');
    passLabel.appendChild(el('span', { text: isPlayer ? '游戏密码' : '密码' }));
    const passInput = el('input', { type: 'password', placeholder: isPlayer ? '请输入该账号的 AuthMe 密码' : '请输入密码' });
    passLabel.appendChild(passInput);

    const tip = el('div', { style: { fontSize: '11px', color: '#6b7280', marginTop: '4px' },
      text: isPlayer ? '登录成功后：可直接购买（支持离线购买，物品上线时发放）' : '提示: 账户註冊為未來開發計畫' });

    form.appendChild(userLabel);
    form.appendChild(passLabel);
    form.appendChild(tip);

    const m = Modal.show({
      title: isPlayer ? '🎮 玩家登录（游戏账号）' : '管理员登录',
      body: form,
      confirmText: '登录',
      cancelText: '取消',
      onConfirm: async (close) => {
        const username = userInput.value.trim();
        const password = passInput.value;
        if (!username) { Toast.show(isPlayer ? '请输入游戏 ID' : '请输入用户名', 'warning'); return false; }
        m.setProcessing('登录中...');

        if (isPlayer) {
          const r = await QSDB.playerLogin(username, password);
          if (r && r.success) {
            state.playerName = r.player || username;
            state.playerBalance = (r.balance != null) ? r.balance : null;
            state.isLoggedIn = false;
            state.isAdmin = false;
            state.username = null;
            state.role = 'guest';
            try { localStorage.setItem('qsw_player_name', state.playerName); } catch (e) { }
            TopNav.render();
            Toast.show('欢迎回来，' + state.playerName + '！' + (r.balance != null ? (' 余额 $' + r.balance) : '') + (r.pending_items ? ('，待领取 ' + r.pending_items + ' 件') : ''), 'success');
            close();
          } else {
            m.setDone('重试');
            Toast.show('登录失败: ' + ((r && r.error) || '游戏ID或密码错误'), 'error');
            return false;
          }
          return;
        }

        const r = await QSDB.login(username, password);
        if (r && r.success) {
          state.isLoggedIn = true;
          state.isAdmin = !!r.is_admin || (QSDB.isAdmin && QSDB.isAdmin());
          const sess = QSDB.getSession ? QSDB.getSession() : null;
          state.username = (sess && sess.username) || username;
          state.role = (sess && sess.role) || 'user';
          state.playerName = null;
          state.playerBalance = null;
          TopNav.render();
          Toast.show('登录成功, 欢迎 ' + state.username + '!', 'success');
          close();
        } else {
          m.setDone('重试');
          Toast.show('登录失败: ' + (r.error || '用户名或密码错误'), 'error');
          // 不自动关闭, 让用户重试
          return false;
        }
      }
    });

    // 自动聚焦
    setTimeout(() => userInput.focus(), 100);
  }

  //  Tab 切换 - 支持三种页面过渡动画
  const tabs = { items: null, itemDetail: null, shops: null, admin: null, data: null };

  // 页面切换动画列表（随机轮换）
  const TRANSITIONS = [
    'anim-fade',         // 淡入淡出（默认）
    'anim-slide-right',  // 从右向左滑入
    'anim-scale'         // 缩放淡入
  ];

  // 辅助函数: 获取当前活跃的 tab-panel（与 state.currentTab 匹配或当前可见的）
  // 性能优化：避免每次都 querySelectorAll；使用 state 快速判断
  function ensureTabPanel(tabName) {
    const content = $('#content-area');
    if (!content) return null;

    // 优先查找与 state.currentTab 匹配的 data-tab panel
    const currentTab = state.currentTab;
    if (currentTab) {
      // 处理 itemDetail 特殊命名
      let key = currentTab;
      // 对于 itemDetail，可能没有具体 material 信息，使用现有可见 panel
      if (key === 'itemDetail') {
        // 查找 display 不为 none 的 panel
        const panels = content.querySelectorAll('.tab-panel');
        for (let i = 0; i < panels.length; i++) {
          if (panels[i].style.display !== 'none') return panels[i];
        }
        // 否则查找第一个 itemDetail_xxx
        const first = content.querySelector('.tab-panel[data-tab^="itemDetail_"]');
        if (first) return first;
      } else {
        const p = content.querySelector('.tab-panel[data-tab="' + key + '"]');
        if (p) return p;
      }
    }

    // 回退：找第一个可见（display !== 'none'）的 panel
    const panels = content.querySelectorAll('.tab-panel');
    for (let i = 0; i < panels.length; i++) {
      if (panels[i].style.display !== 'none') return panels[i];
    }
    // 最后：直接返回第一个
    if (panels.length > 0) return panels[0];

    // 极端情况：没有任何 panel，创建一个
    const panel = document.createElement('div');
    panel.className = 'tab-panel';
    content.appendChild(panel);
    return panel;
  }

  // 目标：相同 tab 切换后不再重建所有节点，仅在首次进入时渲染。
  // 对于数据驱动页面（browse、shops、itemDetail），缓存容器，刷新时只更新数据部分。
  function switchTab(tab, opts) {
    uiLogger.info('[UI] 切换页面 → ' + tab + (opts && opts.material ? ' (物品: ' + opts.material + ')' : ''));
    // 权限检查
    if ((tab === 'admin' || tab === 'data' || tab === 'backup' || tab === 'monitor' || tab === 'announce') && !state.isAdmin) {
      Toast.show('请先使用管理员账户登录', 'warning');
      showLoginModal();
      return;
    }

    // 防止重复切换到相同 tab
    if (state.currentTab === tab && tab !== 'itemDetail') return;

    const t0 = performance.now();
    state.currentTab = tab;
    TopNav.render();

    const content = $('#content-area');
    if (!content) return;

    // 1) 先把所有现存 .tab-panel 子节点设为隐藏：避免 DOM 销毁重建
    //    但由于之前的实现可能只有一个 panel，这里采用稳健策略：
    //    - 查找当前已缓存的 tab（带 data-tab 属性），全部设为 display:none
    //    - 若目标 tab 已存在，则直接 display:block 并触发轻量刷新
    //    - 若不存在，则新建 panel，添加 data-tab 标记，调用 initXxx
    const allPanels = content.querySelectorAll('.tab-panel');
    let targetPanel = null;
    let cachedKey = tab;
    if (tab === 'itemDetail' && opts && opts.material) cachedKey = 'itemDetail_' + opts.material;

    for (let i = 0; i < allPanels.length; i++) {
      const p = allPanels[i];
      if (p.dataset.tab === cachedKey) {
        targetPanel = p;
      } else {
        // 隐藏但保留 DOM，便于下次切换时直接显示（≈0ms 切换）
        p.style.display = 'none';
        p.classList.remove('anim-fade');
      }
    }

    if (targetPanel) {
      // —— 命中缓存：直接显示 + 可选刷新 ——
      targetPanel.style.display = '';
      // 快速淡入（不使用 CSS 动画类，避免 reflow）
      targetPanel.style.opacity = '0';
      // 使用 requestAnimationFrame 双缓冲，确保平滑且不阻塞
      requestAnimationFrame(() => {
        targetPanel.style.transition = 'opacity 180ms ease';
        targetPanel.style.opacity = '1';
      });

      // 对动态数据页，标记为需要刷新，但使用缓存的 API 响应可避免请求
      if (tab === 'itemDetail') {
        // 详情页：若缓存了不同 material，则需要重新渲染
        if (targetPanel.dataset.material !== (opts && opts.material)) {
          // 不是同一个物品，需要重新渲染
          targetPanel.dataset.material = opts && opts.material;
          targetPanel.innerHTML = '';
          renderIntoPanel(targetPanel, () => initItemDetail(opts && opts.material));
        } else {
          // 完全命中，无需操作
        }
      }
      if (typeof console !== 'undefined' && console.debug) console.debug('[switchTab] 缓存命中 ' + cachedKey + '，耗时 ' + Math.round(performance.now() - t0) + 'ms');
      return;
    }

    // —— 未命中缓存：新建 panel，渲染 ——
    // 批量 DOM 操作：先将容器从文档树剥离再操作，避免多次 reflow
    const fragment = document.createDocumentFragment();
    const panel = document.createElement('div');
    panel.className = 'tab-panel';
    panel.dataset.tab = cachedKey;
    if (tab === 'itemDetail' && opts && opts.material) panel.dataset.material = opts.material;
    // 初始透明度为 0，待渲染完成后平滑过渡（避免出现闪烁）
    panel.style.opacity = '0';
    fragment.appendChild(panel);
    content.appendChild(fragment);

    // 切换内容（调用各 initXxx 写入内容到 panel）
    renderIntoPanel(panel, () => {
      switch (tab) {
        case 'home': initHome(); break;
        case 'browse': initItemBrowse(); break;
        case 'items': initItemBrowse(); break;
        case 'itemDetail': initItemDetail(opts && opts.material); break;
        case 'shops': initShops(); break;
        case 'buy': initBuyPage(); break;
        case 'sell': initSellPage(); break;
        case 'stats': initStatsPage(); break;
        case 'monitor': initMonitorPage(); break;
        case 'backup': initBackupPage(); break;
        case 'admin': initAdmin(); break;
        case 'data': initDataIO(); break;
        case 'announce': initAnnouncementManage(); break;
      }
    });

    // 渲染完成后淡入
    requestAnimationFrame(() => {
      panel.style.transition = 'opacity 200ms ease';
      panel.style.opacity = '1';
    });

    if (typeof console !== 'undefined' && console.debug) console.debug('[switchTab] 首次渲染 ' + cachedKey + '，耗时 ' + Math.round(performance.now() - t0) + 'ms');
  }

  // 帮助函数：把内容渲染到指定 panel；通过将 panel 设置为实际渲染目标，
  // 并在 render 期间不触发 layout（利用 DocumentFragment）
  function renderIntoPanel(panel, renderFn) {
    // 为所有 initXxx 函数提供一致的根（.tab-panel）
    // 保持与旧逻辑兼容：initXxx 内部使用 document.getElementById('content-area')
    // 然后 appendChild → 其实最终都添加到 panel 中（因为 panel 是 content-area 唯一活跃子节点）
    // 但 initXxx 中通常通过 ensureTabPanel / getEl('#content-area') 获得容器；
    // 所以此处让 ensureTabPanel 返回当前 panel 即可。
    // 为了简化实现，我们让每个 initXxx 都通过 ensureTabPanel 获取当前 panel（它返回已存在的 panel）。
    // 这里在调用前设置一个标记：
    const currentActive = panel;
    const content = $('#content-area');
    if (content) {
      // 先隐藏其他 panels，确保当前 panel 为唯一活动
      const siblings = content.querySelectorAll('.tab-panel');
      for (let i = 0; i < siblings.length; i++) {
        if (siblings[i] !== currentActive) siblings[i].style.display = 'none';
      }
    }
    try {
      renderFn();
    } catch (err) {
      console.error('[renderIntoPanel] 渲染错误:', err);
    }
  }

  //  IntersectionObserver - 全局共享（只创建 1 次，不频繁 disconnect）
  let sharedScrollObserver = null;
  let sharedObserverReady = false;

  function getSharedScrollObserver() {
    if (sharedObserverReady) return sharedScrollObserver;
    if (!('IntersectionObserver' in window)) { sharedObserverReady = true; return null; }
    // 使用更高的 threshold + 更大的 rootMargin 以提前触发
    sharedScrollObserver = new IntersectionObserver((entries, observer) => {
      // 使用 requestAnimationFrame 批量处理，避免在滚动高峰期阻塞主线程
      requestAnimationFrame(() => {
        for (let i = 0; i < entries.length; i++) {
          const entry = entries[i];
          if (entry.isIntersecting) {
            const t = entry.target;
            const idx = parseInt(t.dataset.animIndex || '0', 10);
            const delay = Math.min((idx % 5) * 40, 200);
            // 使用内联 timeout，避免创建大量闭包
            if (delay > 0) {
              setTimeout(() => { t.classList.add('in-view'); }, delay);
            } else {
              t.classList.add('in-view');
            }
            observer.unobserve(t);
          }
        }
      });
    }, { threshold: 0.15, rootMargin: '0px 0px 40px 0px' });
    sharedObserverReady = true;
    return sharedScrollObserver;
  }

  // 优化版：只观察尚未进入视野的元素，减少重复工作
  function observeScrollAnimations(rootElement) {
    if (!rootElement) return;
    const obs = getSharedScrollObserver();
    if (!obs) {
      // 降级：直接加 in-view 类
      const items = rootElement.querySelectorAll('.shop-card, .item-card, .stat-card, .material-card, .neo-box');
      for (let i = 0; i < items.length; i++) items[i].classList.add('in-view');
      return;
    }
    const items = rootElement.querySelectorAll('.shop-card, .item-card, .enhanced-item-card, .stat-card, .material-card, .neo-box, .announcement-card');
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.classList.contains('in-view')) continue;
      if (!it.classList.contains('anim-reveal')) it.classList.add('anim-reveal');
      if (!it.dataset.animIndex) it.dataset.animIndex = String(i);
      obs.observe(it);
    }
  }

  //  首页: 公告展示 (首页仅展示公告, 不含详细统计)
  async function initHome() {
    const root = ensureTabPanel();
    if (!root) return;

    // 页面标题区
    const header = el('div', { class: 'home-hero' }, [
      el('h2', { class: 'home-hero-title', text: siteServerName ? siteServerName : 'Qshop 商店系统' }),
      el('div', { class: 'home-hero-subtitle', text: siteServerSubtitle ? siteServerSubtitle : 'Minecraft 服务器商店数据汇总与价格查询平台' })
    ]);
    root.appendChild(header);

    // 公告列表容器
    const annWrap = el('div', { class: 'announcement-wrapper' });
    root.appendChild(annWrap);

    // 加载中提示
    const loadingHint = el('div', { class: 'announcement-loading', text: '正在加载公告...' });
    annWrap.appendChild(loadingHint);

    // 异步加载公告
    try {
      const data = await QSDB.getAnnouncements();
      if (data && data.success && data.results && data.results.length > 0) {
        annWrap.innerHTML = '';
        const sectionTitle = el('div', { class: 'page-title-row' }, [
          el('h2', { class: 'page-title', text: '系统公告' }),
          el('div', { class: 'page-subtitle', text: '共 ' + data.results.length + ' 条公告' })
        ]);
        annWrap.appendChild(sectionTitle);

        const annList = el('div', { class: 'announcement-list' });
        data.results.forEach((a) => annList.appendChild(renderAnnouncementCard(a, false)));
        annWrap.appendChild(annList);

        // 注册滚动动画
        observeScrollAnimations(annWrap);
      } else {
        annWrap.innerHTML = '';
        annWrap.appendChild(el('div', { class: 'announcement-empty', text: '暂无公告' }));
      }
    } catch (e) {
      annWrap.innerHTML = '';
      annWrap.appendChild(el('div', { class: 'announcement-error', text: '加载公告失败: ' + (e.message || String(e)) }));
    }
  }

  // 渲染单个公告卡片（供首页和管理页共用）
  function renderAnnouncementCard(ann, isAdminView) {
    const priorityText = ann.priority === 'high' ? '置顶' : ann.priority === 'low' ? '低优先级' : '普通';
    const timeStr = new Date(ann.createdAt || Date.now()).toLocaleString();
    const priorityClass = 'priority-' + (ann.priority || 'normal');
    const publishedText = ann.published ? '' : ' (草稿)';
    const contentFormatted = (ann.content || '').split('\n').filter(line => line.trim()).map(line => el('p', { class: 'announcement-line' }, [document.createTextNode(line)])).join('');

    const card = el('div', { class: 'announcement-card ' + priorityClass });

    // 标题行
    const header = el('div', { class: 'announcement-header' }, [
      el('div', { class: 'announcement-title', text: ann.title || '(无标题)' }),
      el('span', { class: 'announcement-badge', text: priorityText + publishedText })
    ]);
    card.appendChild(header);

    // 元信息
    card.appendChild(el('div', { class: 'announcement-meta' }, [
      el('span', { class: 'meta-', text: '作者: ' + (ann.author || 'unknown') }),
      el('span', { class: 'meta-time', text: '时间: ' + timeStr })
    ]));

    // 内容（将 p 元素真正添加到 DOM）
    const contentBody = el('div', { class: 'announcement-content' });
    const lines = (ann.content || '').split('\n');
    lines.forEach((line) => {
      contentBody.appendChild(el('p', { class: 'announcement-line', text: line }));
    });
    card.appendChild(contentBody);

    // 管理页的操作按钮
    if (isAdminView) {
      const actionRow = el('div', { class: 'announcement-actions' });
      actionRow.appendChild(el('button', {
        class: 'neo-btn',
        text: '编辑',
        onclick: () => showAnnouncementEditor(ann)
      }));
      actionRow.appendChild(el('button', {
        class: 'neo-btn danger',
        text: '删除',
        onclick: () => {
          Modal.confirm({
            title: '确认删除公告',
            body: '标题: ' + ann.title,
            onConfirm: async () => {
              const r = await QSDB.deleteAnnouncement(ann.id);
              if (r && r.success) {
                Toast.show('已删除公告', 'success');
                initAnnouncementManage();
              } else {
                Toast.show('删除失败: ' + (r.error || '未知错误'), 'error');
              }
              return true;
            }
          });
        }
      }));
      card.appendChild(actionRow);
    }

    return card;
  }

  //  公告管理: 创建/编辑/删除 (admin 专属)
  async function initAnnouncementManage() {
    const root = ensureTabPanel();
    if (!root) return;

    // 页面标题区
    const titleRow = el('div', { class: 'page-title-row' }, [
      el('h2', { class: 'page-title', text: '公告管理' }),
      el('div', { class: 'page-subtitle', text: '创建、编辑、发布和删除公告' })
    ]);
    root.appendChild(titleRow);

    // 创建新公告按钮
    const createBtnRow = el('div', { style: { marginBottom: '16px' } });
    createBtnRow.appendChild(el('button', {
      class: 'neo-btn primary',
      text: '+ 创建新公告',
      onclick: () => showAnnouncementEditor(null)
    }));
    root.appendChild(createBtnRow);

    // 公告列表区
    const listArea = el('div', { class: 'admin-ann-list', id: 'admin-ann-list' });
    listArea.appendChild(el('div', { class: 'announcement-loading', text: '正在加载公告...' }));
    root.appendChild(listArea);

    // 加载并渲染
    try {
      const data = await QSDB.getAnnouncements();
      listArea.innerHTML = '';
      if (data && data.success && data.results && data.results.length > 0) {
        const list = el('div', { class: 'announcement-list' });
        data.results.forEach(a => list.appendChild(renderAnnouncementCard(a, true)));
        listArea.appendChild(list);
        observeScrollAnimations(listArea);
      } else {
        listArea.appendChild(el('div', { class: 'announcement-empty', text: '暂无公告，点击上方按钮创建' }));
      }
    } catch (e) {
      listArea.innerHTML = '';
      listArea.appendChild(el('div', { class: 'announcement-error', text: '加载失败: ' + (e.message || String(e)) }));
    }
  }

  // 弹出公告编辑框
  function showAnnouncementEditor(existingAnn) {
    const isEdit = existingAnn && existingAnn.id;
    const titleText = isEdit ? '编辑公告' : '创建新公告';
    const modalContent = document.createElement('div');
    modalContent.className = 'ann-editor';
    modalContent.innerHTML = `
      <div class="form-row">
        <label class="form-label">标题</label>
        <input type="text" class="form-input" id="ann-edit-title" placeholder="公告标题 (最长 200 字)" maxlength="200" value="${(existingAnn ? existingAnn.title || '' : '').replace(/"/g, '&quot;')}" />
      </div>
      <div class="form-row">
        <label class="form-label">优先级</label>
        <select class="form-select" id="ann-edit-priority">
          <option value="high">置顶</option>
          <option value="normal">普通</option>
          <option value="low">低</option>
        </select>
      </div>
      <div class="form-row">
        <label class="form-label">内容</label>
        <textarea class="form-textarea" id="ann-edit-content" placeholder="公告正文 (最长 10000 字)" rows="8" maxlength="10000"></textarea>
      </div>
      <div class="form-row form-row-inline">
        <label>
          <input type="checkbox" id="ann-edit-published" checked />
          <span style="margin-left:6px">发布后对所有用户可见</span>
        </label>
      </div>
    `;
    // 设置 textarea 值 (innerHTML 不会正确处理多行)
    modalContent.querySelector('#ann-edit-content').value = existingAnn ? existingAnn.content || '' : '';
    if (existingAnn && existingAnn.priority) {
      modalContent.querySelector('#ann-edit-priority').value = existingAnn.priority;
    }
    if (existingAnn && existingAnn.published === false) {
      modalContent.querySelector('#ann-edit-published').checked = false;
    }

    Modal.confirm({
      title: titleText,
      body: modalContent,
      confirmText: isEdit ? '保存修改' : '创建公告',
      onConfirm: async () => {
        const titleVal = modalContent.querySelector('#ann-edit-title').value.trim();
        const contentVal = modalContent.querySelector('#ann-edit-content').value.trim();
        const priorityVal = modalContent.querySelector('#ann-edit-priority').value;
        const publishedVal = modalContent.querySelector('#ann-edit-published').checked;
        if (!titleVal) { Toast.show('请输入标题', 'error'); return false; }
        if (!contentVal) { Toast.show('请输入内容', 'error'); return false; }
        let apiPromise;
        if (isEdit) {
          apiPromise = QSDB.updateAnnouncement(existingAnn.id, { title: titleVal, content: contentVal, priority: priorityVal, published: publishedVal });
        } else {
          apiPromise = QSDB.createAnnouncement({ title: titleVal, content: contentVal, priority: priorityVal, published: publishedVal });
        }
        const r = await apiPromise;
        if (r && r.success) {
          Toast.show(isEdit ? '公告已更新' : '公告已创建', 'success');
          initAnnouncementManage();
          return true;
        } else {
          Toast.show('操作失败: ' + (r.error || '未知错误'), 'error');
          return false;
        }
      }
    });
  }

  //  物品浏览 (原名: 物品堆叠列表)
  //  搜索 + 过滤 + 排序 + 分页；点击卡片进入物品详情页
  async function initItemBrowse() {
    const root = ensureTabPanel();
    if (!root) return;

    // 页面标题区
    const titleRow = el('div', { class: 'page-title-row' }, [
      el('h2', { class: 'page-title', text: '物品浏览' }),
      el('div', { class: 'page-subtitle', text: '点击任意物品查看所有销售该物品的店主' })
    ]);
    root.appendChild(titleRow);

    // 工具栏
    const toolbar = el('div', { class: 'items-toolbar' });

    // 搜索框
    const searchWrap = el('div', { class: 'search-box' });
    const searchInput = el('input', { type: 'text', placeholder: '搜索物品名 / 材质 / 店主（支持拼音 zhizhu / 首字母 zz）' });
    const searchBtn = el('button', { class: 'search-btn', text: '搜索' });
    searchWrap.appendChild(searchInput);
    searchWrap.appendChild(searchBtn);
    toolbar.appendChild(searchWrap);

    // 类型过滤
    const typeSel = el('select', { class: 'filter-select' });
    [['', '全部类型'], ['SELLING', '仅出售'], ['BUYING', '仅收购']].forEach(([v, l]) => {
      typeSel.appendChild(el('option', { value: v, text: l }));
    });
    toolbar.appendChild(typeSel);

    // 世界过滤
    const worldSel = el('select', { class: 'filter-select' });
    worldSel.appendChild(el('option', { value: '', text: '全部世界' }));
    toolbar.appendChild(worldSel);

    // 价格区间
    const priceMin = el('input', { type: 'number', placeholder: '最低价格', class: 'filter-input' });
    const priceMax = el('input', { type: 'number', placeholder: '最高价格', class: 'filter-input' });
    toolbar.appendChild(priceMin);
    toolbar.appendChild(priceMax);

    // 排序
    const sortSel = el('select', { class: 'filter-select' });
    [
      ['shops_desc', '商店数↓'], ['shops_asc', '商店数↑'],
      ['owners_desc', '店主数↓'],
      ['price_asc', '平均价格↑'], ['price_desc', '平均价格↓'],
      ['activity_desc', '活跃度↓'],
      ['name_asc', '名称A-Z'], ['name_desc', '名称Z-A']
    ].forEach(([v, l]) => sortSel.appendChild(el('option', { value: v, text: l })));
    toolbar.appendChild(sortSel);

    // 重置按钮
    const resetBtn = el('button', { class: 'neo-btn', text: '重置筛选', onclick: () => {
      searchInput.value = ''; typeSel.value = ''; worldSel.value = '';
      priceMin.value = ''; priceMax.value = ''; sortSel.value = 'shops_desc';
      doSearch(true);
    } });
    toolbar.appendChild(resetBtn);

    root.appendChild(toolbar);

    // 结果区
    const grid = el('div', { class: 'items-grid' });
    const status = el('div', { class: 'items-status', text: '加载中...' });
    root.appendChild(grid);
    root.appendChild(status);

    // 分页
    const pager = el('div', { class: 'items-pager' });
    const prevBtn = el('button', { class: 'neo-btn', text: '« 上一页', onclick: () => { if (page > 1) { page--; doSearch(false); } } });
    const pageInfo = el('span', { class: 'items-pageinfo', text: '' });
    const nextBtn = el('button', { class: 'neo-btn', text: '下一页 »', onclick: () => { if (page < totalPages) { page++; doSearch(false); } } });
    pager.appendChild(prevBtn);
    pager.appendChild(pageInfo);
    pager.appendChild(nextBtn);
    root.appendChild(pager);

    // 加载世界列表
    try {
      const worlds = await QSDB.getWorlds();
      worldSel.innerHTML = '';
      worldSel.appendChild(el('option', { value: '', text: '全部世界' }));
      (worlds || []).forEach((w) => {
        const name = typeof w === 'string' ? w : (w.world || w.name);
        worldSel.appendChild(el('option', { value: name, text: name }));
      });
    } catch (e) {}

    // 数据加载
    let page = 1, total = 0, totalPages = 1, loading = false;
    async function doSearch(reset) {
      if (loading) return;
      loading = true;
      grid.innerHTML = '';
      status.textContent = '加载中...';
      try {
        const data = await QSDB.getItemList({
          q: searchInput.value.trim() || '',
          shop_type: typeSel.value || '',
          world: worldSel.value || '',
          min_price: priceMin.value,
          max_price: priceMax.value,
          sort: sortSel.value || 'shops_desc',
          page: reset ? 1 : page,
          pageSize: 30
        });
        if (data && data.success) {
          total = typeof data.total === 'number' ? data.total : 0;
          totalPages = Math.max(1, data.total_pages || 1);
          page = reset ? 1 : (data.page || 1);
          if (!data.results || data.results.length === 0) {
            grid.innerHTML = '';
            status.textContent = '暂无匹配的物品';
            grid.appendChild(el('div', { class: 'items-empty', text: '没有符合条件的物品，请调整筛选条件' }));
          } else {
            data.results.forEach((item) => grid.appendChild(renderItemCard(item)));
            status.textContent = '共 ' + total + ' 种物品 · 当前第 ' + page + ' / ' + totalPages + ' 页';
            // 注册滚动动画观察
            observeScrollAnimations(grid);
          }
          prevBtn.disabled = page <= 1;
          nextBtn.disabled = page >= totalPages;
          pageInfo.textContent = '第 ' + page + ' / ' + totalPages + ' 页 · 共 ' + total + ' 种';
        } else {
          status.textContent = '查询失败: ' + (data.error || '未知错误');
        }
      } catch (err) {
        status.textContent = '查询失败: ' + err.message;
      } finally { loading = false; }
    }

    // 事件绑定
    const debounced = debounce(() => doSearch(true), 300);
    searchInput.addEventListener('input', debounced);
    searchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') doSearch(true); });
    searchBtn.addEventListener('click', () => doSearch(true));
    typeSel.addEventListener('change', () => doSearch(true));
    worldSel.addEventListener('change', () => doSearch(true));
    sortSel.addEventListener('change', () => doSearch(true));
    priceMin.addEventListener('change', () => doSearch(true));
    priceMax.addEventListener('change', () => doSearch(true));

    doSearch(true);
  }

  // 物品浏览卡片（出售 + 收购双模式显示）
  function renderItemCard(item) {
    const material = item.material || 'Unknown';
    const displayName = item.shop_cn_name || item.item_name || material;

    // —— 出售价相关 ——
    const avgSell = item.avg_sell_price !== null && item.avg_sell_price !== undefined ? Number(item.avg_sell_price)
                   : (item.avg_price !== null && item.avg_price !== undefined ? Number(item.avg_price) : null);
    const sellPriceText = item.price_display ? String(item.price_display)
                        : (avgSell !== null && !isNaN(avgSell) ? Number(avgSell).toFixed(2) : '—');
    const minSell = item.min_sell_price;
    const maxSell = item.max_sell_price;
    const sellingShops = item.selling_shops || 0;

    // —— 收购价相关 ——
    const avgBuy = item.avg_buy_price !== null && item.avg_buy_price !== undefined ? Number(item.avg_buy_price) : null;
    const minBuy = item.min_buy_price;
    const maxBuy = item.max_buy_price;
    const buyingShops = item.buying_shops || 0;
    const totalShops = item.total_shops || 0;

    const card = el('div', {
      class: 'item-card enhanced-item-card',
      onclick: () => { state.currentTab = 'itemDetail'; switchTab('itemDetail', { material: material }); }
    });

    // —— 左：物品图片区 ——
    const leftBox = el('div', { class: 'item-card-image-box' });
    if (item.item_image) {
      const img = el('img', {
        class: 'item-card-image',
        src: item.item_image,
        alt: String(displayName),
        loading: 'lazy',
        decoding: 'async',
        onerror: function () {
          try {
            this.onerror = function () { this.style.display = 'none'; };
            this.src = ERR_IMG_PATH;
            this.className = 'item-card-image error-img';
          } catch (e) {
            this.style.display = 'none';
            leftBox.innerHTML = '';
            leftBox.appendChild(el('img', { class: 'item-card-image error-img', src: ERR_IMG_PATH, alt: 'error' }));
          }
        }
      });
      leftBox.appendChild(img);
    } else {
      leftBox.appendChild(el('img', { class: 'item-card-image error-img', src: ERR_IMG_PATH, alt: 'error', loading: 'lazy', decoding: 'async' }));
    }
    card.appendChild(leftBox);

    // —— 右：信息区 ——
    const rightBox = el('div', { class: 'item-card-content' });

    // 标题：中文名称 + 材质英文（文本缺失自动显示预设图片）
    const nameRow = el('div', { class: 'item-card-name-row' });
    // —— 物品中文名称（缺失 → 预设图片） ——
    if (hasValidText(displayName) && displayName !== material) {
      nameRow.appendChild(el('div', { class: 'item-card-name', text: String(displayName) }));
      nameRow.appendChild(el('small', { class: 'item-card-material',
        text: hasValidText(material) && String(material).toUpperCase() !== 'UNKNOWN' ? String(material) : '' }));
    } else if (hasValidText(material) && String(material).toUpperCase() !== 'UNKNOWN') {
      nameRow.appendChild(el('div', { class: 'item-card-name', text: String(material) }));
      nameRow.appendChild(el('small', { class: 'item-card-material', text: '' }));
    } else {
      // 文本缺失：直接显示预设图片替代整行（带淡入过渡）
      nameRow.classList.add('text-missing-row');
      const fallbackImg = el('img', {
        class: 'text-missing-img text-missing-row-img',
        src: FALLBACK_IMG_PATH,
        alt: 'missing',
        loading: 'lazy',
        decoding: 'async',
        style: 'opacity:0; transition:opacity 350ms ease;',
        onerror: function () { this.style.display = 'none'; }
      });
      nameRow.appendChild(fallbackImg);
      setTimeout(function () { fallbackImg.style.opacity = '1'; }, 15);
    }
    rightBox.appendChild(nameRow);

    // 核心数据：出售 + 收购双列（若有对应数据则显示）
    const dataRow = el('div', { class: 'item-card-data-row' });

    // 出售列（若有数据则显示）
    if (sellingShops > 0) {
      const sellBlock = el('div', { class: 'item-card-price-block block-selling' }, [
        el('div', { class: 'item-card-price-label', text: '出售 (' + String(sellingShops) + ' 家)' }),
        el('div', { class: 'item-card-price-val', text: '$ ' + sellPriceText }),
        el('div', { class: 'item-card-price-range', text: (minSell != null ? Number(minSell).toFixed(2) : '—') + ' ~ ' + (maxSell != null ? Number(maxSell).toFixed(2) : '—') })
      ]);
      dataRow.appendChild(sellBlock);
    }

    // 收购列（若有数据则显示）
    if (buyingShops > 0) {
      const buyAvgText = avgBuy !== null && !isNaN(avgBuy) ? Number(avgBuy).toFixed(2) : '—';
      const buyBlock = el('div', { class: 'item-card-price-block block-buying' }, [
        el('div', { class: 'item-card-price-label', text: '收购 (' + String(buyingShops) + ' 家)' }),
        el('div', { class: 'item-card-price-val', text: '$ ' + buyAvgText }),
        el('div', { class: 'item-card-price-range', text: (minBuy != null ? Number(minBuy).toFixed(2) : '—') + ' ~ ' + (maxBuy != null ? Number(maxBuy).toFixed(2) : '—') })
      ]);
      dataRow.appendChild(buyBlock);
    }

    // 如果都为空：显示默认"尚无数据"
    if (sellingShops === 0 && buyingShops === 0) {
      dataRow.appendChild(el('div', { class: 'item-card-price-block' }, [
        el('div', { class: 'item-card-price-label', text: '暂无店铺' }),
        el('div', { class: 'item-card-price-val', text: '—' })
      ]));
    }

    rightBox.appendChild(dataRow);

    // 统计条（简洁版）
    const statsRow = el('div', { class: 'item-card-stats-row' });
    function addChip(text, cls) {
      statsRow.appendChild(el('span', { class: 'item-card-chip ' + (cls || ''), text: String(text) }));
    }
    if (sellingShops) addChip('出售 ' + sellingShops + ' 家', 'selling');
    if (buyingShops) addChip('收购 ' + buyingShops + ' 家', 'buying');
    if (item.owner_count) addChip(item.owner_count + ' 位店主', 'owner');
    if (item.world_count) addChip(item.world_count + ' 个世界', 'world');
    rightBox.appendChild(statsRow);

    // 详情按钮
    rightBox.appendChild(el('div', { class: 'item-card-footer' }, [
      el('button', {
        class: 'neo-btn neo-btn-primary item-detail-btn',
        text: '查看详情 →',
        onclick: (ev) => {
          ev.stopPropagation();
          state.currentTab = 'itemDetail';
          switchTab('itemDetail', { material: material });
        }
      })
    ]));

    card.appendChild(rightBox);
    return card;
  }

  //  物品详情页: 显示所有销售该物品的店主列表 + 聚合统计 + 过滤 + 排序
  async function initItemDetail(material) {
    const root = ensureTabPanel();
    if (!root) return;

    if (!material) {
      root.appendChild(el('div', { class: 'items-empty', text: '未指定物品' }));
      return;
    }

    // 页面导航返回
    const titleEl = el('h2', { class: 'item-detail-title' });
    if (hasValidText(material) && String(material).toUpperCase() !== 'UNKNOWN') {
      titleEl.textContent = String(material);
    } else {
      titleEl.classList.add('text-missing-row');
      const titleFb = el('img', {
        class: 'text-missing-img text-missing-row-img',
        src: FALLBACK_IMG_PATH,
        alt: 'missing',
        style: 'opacity:0; transition:opacity 350ms ease;',
        onerror: function () { this.style.display = 'none'; }
      });
      titleEl.appendChild(titleFb);
      setTimeout(function () { titleFb.style.opacity = '1'; }, 15);
    }
    const backRow = el('div', { class: 'item-detail-back' }, [
      el('button', {
        class: 'neo-btn',
        text: '« 返回物品列表',
        onclick: () => switchTab('browse')
      }),
      titleEl
    ]);
    root.appendChild(backRow);

    // 统计卡片容器（双列布局：基本统计 + 出售/收购价格汇总）
    const statsRow = el('div', { class: 'stats-row item-stats-row' });

    function makeStat(valText, labelText, cls) {
      return el('div', { class: 'stat-card' + (cls ? ' ' + cls : '') }, [
        el('div', { class: 'val', text: String(valText) }),
        el('div', { class: 'lbl', text: String(labelText) })
      ]);
    }

    const statShops = makeStat('...', '家商店');
    const statOwners = makeStat('...', '位店主');
    const statWorlds = makeStat('...', '个世界');
    // 出售价格
    const statMinSell = makeStat('...', '最低出售价', 'stat-selling');
    const statMaxSell = makeStat('...', '最高出售价', 'stat-selling');
    const statAvgSell = makeStat('...', '平均出售价', 'stat-selling');
    // 收购价格
    const statMinBuy = makeStat('...', '最低收购价', 'stat-buying');
    const statMaxBuy = makeStat('...', '最高收购价', 'stat-buying');
    const statAvgBuy = makeStat('...', '平均收购价', 'stat-buying');

    statsRow.appendChild(statShops);
    statsRow.appendChild(statOwners);
    statsRow.appendChild(statWorlds);
    statsRow.appendChild(statMinSell);
    statsRow.appendChild(statMaxSell);
    statsRow.appendChild(statAvgSell);
    statsRow.appendChild(statMinBuy);
    statsRow.appendChild(statMaxBuy);
    statsRow.appendChild(statAvgBuy);
    root.appendChild(statsRow);
    observeScrollAnimations(statsRow);

    // 过滤工具栏
    const toolbar = el('div', { class: 'items-toolbar' });
    const typeSel = el('select', { class: 'filter-select' });
    [['', '全部类型'], ['SELLING', '出售'], ['BUYING', '收购']].forEach(([v, l]) => {
      typeSel.appendChild(el('option', { value: v, text: l }));
    });
    toolbar.appendChild(typeSel);

    const worldSel = el('select', { class: 'filter-select' });
    worldSel.appendChild(el('option', { value: '', text: '全部世界' }));
    toolbar.appendChild(worldSel);

    const ownerInput = el('input', { type: 'text', placeholder: '按店主名过滤...', class: 'filter-input' });
    toolbar.appendChild(ownerInput);

    const priceMin = el('input', { type: 'number', placeholder: '最低价格', class: 'filter-input' });
    const priceMax = el('input', { type: 'number', placeholder: '最高价格', class: 'filter-input' });
    toolbar.appendChild(priceMin);
    toolbar.appendChild(priceMax);

    const sortSel = el('select', { class: 'filter-select' });
    [
      ['price_asc', '价格↑'], ['price_desc', '价格↓'],
      ['owner_asc', '店主A-Z'], ['activity_desc', '活跃度↓'],
      ['world_asc', '世界A-Z'], ['quantity_desc', '库存量↓'],
      ['newest', '最新']
    ].forEach(([v, l]) => sortSel.appendChild(el('option', { value: v, text: l })));
    toolbar.appendChild(sortSel);

    const resetBtn = el('button', { class: 'neo-btn', text: '重置筛选', onclick: () => {
      typeSel.value = ''; worldSel.value = ''; ownerInput.value = '';
      priceMin.value = ''; priceMax.value = ''; sortSel.value = 'price_asc';
      doSearch(true);
    } });
    toolbar.appendChild(resetBtn);

    root.appendChild(toolbar);

    // 结果区
    const grid = el('div', { class: 'shop-grid' });
    const status = el('div', { class: 'items-status', text: '加载中...' });
    root.appendChild(grid);
    root.appendChild(status);

    // 分页
    const pager = el('div', { class: 'items-pager' });
    const prevBtn = el('button', { class: 'neo-btn', text: '« 上一页', onclick: () => { if (page > 1) { page--; doSearch(false); } } });
    const pageInfo = el('span', { class: 'items-pageinfo', text: '' });
    const nextBtn = el('button', { class: 'neo-btn', text: '下一页 »', onclick: () => { if (page < totalPages) { page++; doSearch(false); } } });
    pager.appendChild(prevBtn);
    pager.appendChild(pageInfo);
    pager.appendChild(nextBtn);
    root.appendChild(pager);

    // 加载世界列表（用于过滤下拉）
    try {
      const worlds = await QSDB.getWorlds();
      worldSel.innerHTML = '';
      worldSel.appendChild(el('option', { value: '', text: '全部世界' }));
      (worlds || []).forEach((w) => {
        const name = typeof w === 'string' ? w : (w.world || w.name);
        worldSel.appendChild(el('option', { value: name, text: name }));
      });
    } catch (e) {}

    // 数据
    let page = 1, total = 0, totalPages = 1, loading = false;
    async function doSearch(reset) {
      if (loading) return;
      loading = true;
      grid.innerHTML = '';
      status.textContent = '加载中...';
      try {
        const data = await QSDB.getItemDetail(material, {
          shop_type: typeSel.value || '',
          world: worldSel.value || '',
          owner: ownerInput.value.trim() || '',
          min_price: priceMin.value,
          max_price: priceMax.value,
          sort: sortSel.value || 'price_asc',
          page: reset ? 1 : page,
          pageSize: 25
        });
        if (data && data.success) {
          total = typeof data.total === 'number' ? data.total : 0;
          totalPages = Math.max(1, data.total_pages || 1);
          page = reset ? 1 : (data.page || 1);

          // 更新标题：优先中文名称
          const displayTitle = data.shop_cn_name || data.item_name || data.material || material;
          // 动态更新：若 displayTitle 有效则显示文本，否则显示预设图片（淡入）
          if (hasValidText(displayTitle) && String(displayTitle).toUpperCase() !== 'UNKNOWN' && String(displayTitle).toUpperCase() !== 'UNKNOWN ITEM') {
            titleEl.classList.remove('text-missing-row');
            titleEl.innerHTML = '';
            titleEl.style.opacity = '0';
            titleEl.textContent = String(displayTitle);
            setTimeout(function () { titleEl.style.transition = 'opacity 300ms ease'; titleEl.style.opacity = '1'; }, 10);
          } else {
            titleEl.innerHTML = '';
            titleEl.classList.add('text-missing-row');
            const fb = el('img', {
              class: 'text-missing-img text-missing-row-img',
              src: FALLBACK_IMG_PATH,
              alt: 'missing',
              style: 'opacity:0; transition:opacity 350ms ease;',
              onerror: function () { this.style.display = 'none'; }
            });
            titleEl.appendChild(fb);
            setTimeout(function () { fb.style.opacity = '1'; }, 15);
          }
          titleEl.title = '物品 ID: ' + (data.material || material);

          // 填充顶部统计
          const s = data.stats || {};
          statShops.querySelector('.val').textContent = (s.selling_shops ? s.selling_shops + ' 出售' : '') + (s.buying_shops ? ' + ' + s.buying_shops + ' 收购' : '') || (s.total_shops || 0);
          statOwners.querySelector('.val').textContent = s.owner_count || 0;
          statWorlds.querySelector('.val').textContent = s.world_count || 0;
          statMinSell.querySelector('.val').textContent = s.min_sell_price != null ? parseFloat(s.min_sell_price).toFixed(2) : '—';
          statMaxSell.querySelector('.val').textContent = s.max_sell_price != null ? parseFloat(s.max_sell_price).toFixed(2) : '—';
          statAvgSell.querySelector('.val').textContent = s.avg_sell_price != null ? parseFloat(s.avg_sell_price).toFixed(2) : '—';
          statMinBuy.querySelector('.val').textContent = s.min_buy_price != null ? parseFloat(s.min_buy_price).toFixed(2) : '—';
          statMaxBuy.querySelector('.val').textContent = s.max_buy_price != null ? parseFloat(s.max_buy_price).toFixed(2) : '—';
          statAvgBuy.querySelector('.val').textContent = s.avg_buy_price != null ? parseFloat(s.avg_buy_price).toFixed(2) : '—';

          if (!data.shops || data.shops.length === 0) {
            status.textContent = '暂无匹配的商店';
            grid.appendChild(el('div', { class: 'items-empty', text: '该物品暂无符合条件的商店记录' }));
          } else {
            data.shops.forEach((shop) => grid.appendChild(renderShopDetailCard(shop)));
            status.textContent = '共 ' + total + ' 家商店 · 当前第 ' + page + ' / ' + totalPages + ' 页';
            // 注册滚动动画观察
            observeScrollAnimations(grid);
          }
          prevBtn.disabled = page <= 1;
          nextBtn.disabled = page >= totalPages;
          pageInfo.textContent = '第 ' + page + ' / ' + totalPages + ' 页 · 共 ' + total + ' 家';
        } else {
          status.textContent = '查询失败: ' + (data.error || '未知错误');
        }
      } catch (err) {
        status.textContent = '查询失败: ' + err.message;
      } finally { loading = false; }
    }

    // 事件绑定
    typeSel.addEventListener('change', () => doSearch(true));
    worldSel.addEventListener('change', () => doSearch(true));
    sortSel.addEventListener('change', () => doSearch(true));
    priceMin.addEventListener('change', () => doSearch(true));
    priceMax.addEventListener('change', () => doSearch(true));
    const debouncedOwner = debounce(() => doSearch(true), 400);
    ownerInput.addEventListener('input', debouncedOwner);

    doSearch(true);
  }

  // 物品详情页中的商店卡片（库存语义 + 出售/收购区分）
  // 店主名过滤：系统商店的 i18n 占位（「无限」/Unlimited）与无效值不作为店主显示
  function shopOwnerName(shop) {
    if (!shop || shop.owner_name == null) return '';
    const n = String(shop.owner_name).trim();
    if (!n) return '';
    if (/^(无限|unlimited|unknown|console|系统商店|n\/a|—|-)$/i.test(n)) return '';
    return n;
  }

  function renderShopDetailCard(shop) {
    const card = el('div', { class: 'shop-card shop-detail-card' });

    // —— 1. 顶部：物品中文名称 + 交易类型徽标 + 系统/玩家标识 ——
    const isBuying = shop.shop_type === 'BUYING';
    const isSystem = shop.is_system_shop === true;

    const titleBadges = el('div', { class: 'shop-title-badges' }, [
      el('span', {
        class: 'neo-badge ' + (isBuying ? 'info badge-buying' : 'inverted badge-selling'),
        text: isBuying ? '收购' : '出售'
      }),
      el('span', {
        class: 'neo-badge ' + (isSystem ? 'badge-system' : 'badge-player'),
        text: isSystem ? '系统商店' : '玩家商店'
      })
    ]);
    const header = el('div', { class: 'shop-card-header' }, [
      el('div', { class: 'shop-title' }, [
        (function () {
          const titleWrap = el('div', { class: 'shop-title-wrap' });
          const rawName = shop.shop_cn_name || shop.item_name || shop.material;
          const rawMat = shop.material;
          const nameValid = hasValidText(rawName) && String(rawName).toUpperCase() !== 'UNKNOWN' && String(rawName).toUpperCase() !== 'UNKNOWN ITEM';
          if (nameValid) {
            titleWrap.appendChild(el('div', { class: 'shop-name', text: String(rawName) }));
            if (hasValidText(rawMat) && String(rawMat).toUpperCase() !== 'UNKNOWN') {
              titleWrap.appendChild(el('small', { class: 'shop-material', text: '材质: ' + String(rawMat) }));
            } else {
              // 材质缺失：小图标替代
              const matSmall = el('small', { class: 'shop-material shop-material-missing' });
              matSmall.appendChild(el('img', { class: 'text-missing-img-inline', src: FALLBACK_IMG_PATH, alt: '', style: 'opacity:0; transition:opacity 300ms ease; width:14px; height:14px;',
                onerror: function () { this.style.display = 'none'; },
                onload: function () { this.style.opacity = '1'; } }));
              titleWrap.appendChild(matSmall);
            }
          } else {
            // 整个文本缺失：整行替换为预设图片（淡入）
            titleWrap.classList.add('text-missing-row');
            const fb = el('img', {
              class: 'text-missing-img text-missing-row-img',
              src: FALLBACK_IMG_PATH,
              alt: 'missing',
              loading: 'lazy',
              decoding: 'async',
              style: 'opacity:0; transition:opacity 350ms ease;',
              onerror: function () { this.style.display = 'none'; }
            });
            titleWrap.appendChild(fb);
            setTimeout(function () { fb.style.opacity = '1'; }, 15);
          }
          return titleWrap;
        })()
      ]),
      titleBadges
    ]);
    card.appendChild(header);

    // —— 2. 物品图片区 ——
    const imgBox = el('div', { class: 'shop-card-left' });
    if (shop.item_image) {
      const img = el('img', {
        class: 'item-image',
        src: shop.item_image,
        alt: shop.material || 'item',
        loading: 'lazy',
        decoding: 'async',
        onerror: function () {
          try {
            this.onerror = function () { this.style.display = 'none'; };
            this.src = ERR_IMG_PATH;
            this.className = 'item-image error-img';
          } catch (e) {
            this.style.display = 'none';
            imgBox.innerHTML = '';
            imgBox.appendChild(el('img', { class: 'item-image error-img', src: ERR_IMG_PATH, alt: 'error' }));
          }
        }
      });
      imgBox.appendChild(img);
    } else {
      imgBox.appendChild(el('img', { class: 'item-image error-img', src: ERR_IMG_PATH, alt: 'error', loading: 'lazy', decoding: 'async' }));
    }
    card.appendChild(imgBox);

    // —— 3. 价格和库存 ——
    const priceDisplay = shop.price_display !== undefined && shop.price_display !== null
      ? String(shop.price_display)
      : Number(shop.price || 0).toFixed(2);

    const info = el('div', { class: 'shop-card-info' });

    function tr(k, v, extraClass) {
      const row = el('div', { class: 'shop-row' + (extraClass ? ' ' + extraClass : '') }, [
        el('span', { class: 'k', text: k }),
        el('span', { class: 'v', text: String(v) })
      ]);
      info.appendChild(row);
    }

    // 价格行（出售/收购各自语义化）
    tr(isBuying ? '收购价' : '出售价', '$' + priceDisplay);
    if (shop.stacking_amount != null && shop.stacking_amount !== '') tr('堆叠数', shop.stacking_amount);

    // —— 核心：库存语义显示 ——
    // 优先使用后端返回的语义字段（is_infinite / stock_text / stock_label）
    let stockLabel = '库存量';
    let stockText = '';
    let stockClass = 'highlight-row';
    let isInfinite = false;
    let quantityNum = null;

    if (shop.is_infinite === true || shop.is_infinite === false) {
      // 后端已提供语义字段
      isInfinite = shop.is_infinite;
      // stock_text="—" 表示未设置，直接使用；否则 fallback 到 quantity
      stockText = shop.stock_text || (isInfinite ? '无限' : shop.quantity);
      stockLabel = shop.stock_label || (isBuying ? '收购量' : '库存量');
      quantityNum = shop.quantity_num;
    } else {
      // 兼容旧数据：自行判断
      const q = (shop.quantity === null || shop.quantity === undefined || shop.quantity === '') ? -1 : Number(shop.quantity);
      isInfinite = q < 0;
      stockText = isInfinite ? '无限' : String(q);
      stockLabel = isBuying ? '收购量' : '库存量';
      quantityNum = q;
    }
    // 未知状态（null）：单独的 CSS class
    const isUnknown = stockText === '—' || stockText === undefined || stockText === null;
    if (shop.is_low_stock === true) stockClass += ' quantity-low';
    else if (isInfinite) stockClass += ' quantity-infinite';
    else if (isUnknown) stockClass += ' quantity-unknown';
    else stockClass += ' quantity-normal';

    // 数量高亮行（完整语义化显示）
    const suffix = isInfinite ? '' : (isUnknown ? '' : ' 件');
    const qRow = el('div', { class: 'shop-row shop-row-quantity ' + stockClass }, [
      el('span', { class: 'k', text: stockLabel }),
      el('span', {
        class: 'v quantity-big',
        text: (isBuying ? '需要 ' : '库存 ') + stockText + suffix
      })
    ]);
    info.appendChild(qRow);

    tr('世界', shop.world || '-');
    tr('坐标', '(' + shop.x + ', ' + shop.y + ', ' + shop.z + ')');
    if (shop.price_reasonable != null && shop.price_reasonable !== '') tr('价格合理', shop.price_reasonable === true || shop.price_reasonable === 'true' ? '是' : '否');
    if (shop.activity_score != null) tr('活跃度', shop.activity_score);
    // 店主行：'无限' 等无效名（系统商店占位）不显示
    const ownerShown = shopOwnerName(shop);
    if (ownerShown) tr('店主', ownerShown);

    // —— 玩家商店限制提示（系统商店不显示无意义的「无限」上限行）——
    if (!isSystem) {
      const stockMax = isBuying
        ? (shop.max_buy_quantity != null ? String(shop.max_buy_quantity) : '1000')
        : (shop.max_stock_capacity != null ? String(shop.max_stock_capacity) : '2000');
      tr('单次/库存上限', stockMax + ' 件');
    }

    // —— 在线购买按钮（仅出售商店）——
    if (!isBuying) {
      card.appendChild(el('div', { class: 'shop-buy-bar' }, [
        el('button', {
          class: 'neo-btn primary purchase-btn',
          text: '🛒 在线购买',
          onclick: function () { showPurchaseModal(shop); }
        })
      ]));
    }

    // —— 在线卖出按钮（仅收购商店）——
    if (isBuying) {
      card.appendChild(el('div', { class: 'shop-buy-bar' }, [
        el('button', {
          class: 'neo-btn primary purchase-btn',
          text: '💰 卖给 TA',
          onclick: function () { showSellModal(shop); }
        })
      ]));
    }

    card.appendChild(info);
    return card;
  }

  // 渲染商店卡片（带图片 + 中文名称 + 店主下移 + 价格格式化防溢出）
  function renderShopCard(shop) {
    const card = el('div', { class: 'shop-card' });

    // —— 1. 顶部：中文名称 + 出售/收购标签 ——
    const header = el('div', { class: 'shop-card-header' });
    const titleCol = el('div', { class: 'shop-title' });
    const rawShopName = shop.shop_cn_name || shop.item_name || shop.material;
    const rawShopMat = shop.material;
    const nameValid = hasValidText(rawShopName) && String(rawShopName).toUpperCase() !== 'UNKNOWN';
    const matValid = hasValidText(rawShopMat) && String(rawShopMat).toUpperCase() !== 'UNKNOWN';
    if (nameValid) {
      titleCol.appendChild(el('div', { class: 'shop-name', text: String(rawShopName) }));
      if (matValid) {
        titleCol.appendChild(el('small', { class: 'shop-material', text: '材质: ' + String(rawShopMat) }));
      } else {
        const missingMat = el('small', { class: 'shop-material shop-material-missing' });
        missingMat.appendChild(el('img', { class: 'text-missing-img-inline', src: FALLBACK_IMG_PATH, alt: '',
          style: 'opacity:0; transition:opacity 300ms ease; width:14px; height:14px;',
          onerror: function () { this.style.display = 'none'; },
          onload: function () { this.style.opacity = '1'; } }));
        titleCol.appendChild(missingMat);
      }
    } else {
      // 名称完全缺失：整行显示预设图片（淡入）
      titleCol.classList.add('text-missing-row');
      const fb = el('img', {
        class: 'text-missing-img text-missing-row-img',
        src: FALLBACK_IMG_PATH,
        alt: 'missing',
        loading: 'lazy',
        decoding: 'async',
        style: 'opacity:0; transition:opacity 350ms ease;',
        onerror: function () { this.style.display = 'none'; }
      });
      titleCol.appendChild(fb);
      setTimeout(function () { fb.style.opacity = '1'; }, 15);
    }
    header.appendChild(titleCol);
    header.appendChild(
      el('div', null, [
        el('span', { class: 'neo-badge ' + (shop.shop_type === 'BUYING' ? 'info' : 'inverted'), text: shop.shop_type === 'BUYING' ? '收购' : '出售' })
      ])
    );
    card.appendChild(header);

    // —— 2. 物品图片区（Minecraft 像素风）——
    const imgBox = el('div', { class: 'shop-card-left' });
    if (shop.item_image) {
      const img = el('img', {
        class: 'item-image',
        src: shop.item_image,
        alt: shop.material || 'item',
        loading: 'lazy',
        onerror: function () {
          try {
            this.onerror = function () { this.style.display = 'none'; };
            this.src = ERR_IMG_PATH;
            this.className = 'item-image error-img';
          } catch (e) {
            this.style.display = 'none';
            imgBox.innerHTML = '';
            imgBox.appendChild(el('img', { class: 'item-image error-img', src: ERR_IMG_PATH, alt: 'error' }));
          }
        }
      });
      imgBox.appendChild(img);
    } else {
      imgBox.appendChild(el('img', { class: 'item-image error-img', src: ERR_IMG_PATH, alt: 'error', loading: 'lazy', decoding: 'async' }));
    }
    card.appendChild(imgBox);

    // —— 3. 价格信息（防溢出）——
    const info = el('table', { class: 'kv-table' });
    function tr(label, val) {
      const r = el('tr');
      r.appendChild(el('td', { text: label }));
      r.appendChild(el('td', { text: String(val) }));
      info.appendChild(r);
    }
    tr('价格', '$' + (shop.price_display !== undefined && shop.price_display !== null ? String(shop.price_display) : Number(shop.price || 0).toFixed(2)));
    tr('堆叠', shop.stacking_amount || 1);
    tr('世界', shop.world || '-');
    tr('坐标', '(' + shop.x + ', ' + shop.y + ', ' + shop.z + ')');
    const ownerShownShop = shopOwnerName(shop);
    if (ownerShownShop) tr('店主', ownerShownShop);

    // —— 在线购买按钮（仅出售商店）——
    if (shop.shop_type !== 'BUYING') {
      card.appendChild(el('div', { class: 'shop-buy-bar' }, [
        el('button', {
          class: 'neo-btn primary purchase-btn',
          text: '🛒 在线购买',
          onclick: function () { showPurchaseModal(shop); }
        })
      ]));
    }

    card.appendChild(info);
    return card;
  }

  //  在线购买弹窗（玩家必须在线）
  function showPurchaseModal(shop) {
    const unitPrice = Number(shop.price || 0);
    const unitDisplay = (shop.price_display !== undefined && shop.price_display !== null)
      ? String(shop.price_display)
      : unitPrice.toFixed(2);
    const stackAmount = Math.max(1, parseInt(shop.stacking_amount, 10) || 1);
    const itemName = shop.shop_cn_name || shop.item_name || shop.material || '物品';
    const ownerName = shopOwnerName(shop);

    const logged = !!state.playerName;
    let savedName = '';
    try { savedName = localStorage.getItem('qsw_player_name') || ''; } catch (e) { }

    const nameInput = el('input', {
      type: 'text', class: 'purchase-input', placeholder: '请输入你的游戏 ID',
      value: logged ? state.playerName : savedName, maxlength: '16', style: { width: '100%' }
    });
    if (logged) nameInput.setAttribute('readonly', 'readonly');
    const passInput = el('input', {
      type: 'password', class: 'purchase-input', placeholder: '游戏密码（AuthMe，验证身份）', style: { width: '100%' }
    });
    const qtyInput = el('input', {
      type: 'number', class: 'purchase-input purchase-qty', value: '1',
      min: '1', max: '64', style: { width: '110px' }
    });
    const maxBtn = el('button', { class: 'neo-btn', text: '最大', style: { marginLeft: '8px', padding: '8px 14px' } });
    const summary = el('div', { class: 'purchase-summary' });
    const balanceInfo = el('div', { style: { fontSize: '13px', fontWeight: '600', color: '#065f46', marginTop: '-4px' } });
    let currentBalance = null;
    if (logged && state.playerBalance != null) {
      currentBalance = Number(state.playerBalance);
      balanceInfo.textContent = '💰 你的余额: $' + currentBalance;
    } else if (logged) {
      balanceInfo.textContent = '💰 余额查询中...';
      QSDB.getWallet('').then(function (w) {
        if (w && w.success && w.balance != null) {
          currentBalance = Number(w.balance);
          state.playerBalance = w.balance;
          balanceInfo.textContent = '💰 你的余额: $' + w.balance + (w.pending_items ? ('（待领取 ' + w.pending_items + ' 件）') : '');
        }
      }).catch(function () { });
    } else {
      balanceInfo.textContent = '💰 输入游戏 ID 后自动查询余额';
      nameInput.addEventListener('change', function () {
        const n = nameInput.value.trim();
        if (!n) return;
        balanceInfo.textContent = '💰 查询中...';
        QSDB.getWallet(n).then(function (w) {
          if (w && w.success && w.balance != null) {
            currentBalance = Number(w.balance);
            balanceInfo.textContent = '💰 ' + n + ' 的余额: $' + w.balance + (w.pending_items ? ('（待领取 ' + w.pending_items + ' 件）') : '');
          } else {
            balanceInfo.textContent = '💰 无法查询余额（经济插件未安装或账户不存在）';
          }
        }).catch(function () { balanceInfo.textContent = '💰 余额查询失败'; });
      });
    }
    maxBtn.onclick = function () {
      let maxQ = 64;
      if (shop.is_infinite !== true) {
        const stockNum = (shop.quantity_num != null) ? Number(shop.quantity_num) : ((shop.quantity != null && shop.quantity !== '') ? Number(shop.quantity) : NaN);
        if (!isNaN(stockNum) && stockNum >= 0) maxQ = Math.min(maxQ, Math.floor(stockNum / stackAmount));
      }
      if (currentBalance != null && unitPrice > 0) maxQ = Math.min(maxQ, Math.floor(currentBalance / unitPrice));
      maxQ = Math.max(1, maxQ);
      qtyInput.value = String(maxQ);
      refreshSummary();
    };

    function currentQty() {
      return Math.max(1, Math.min(64, parseInt(qtyInput.value, 10) || 1));
    }
    function refreshSummary() {
      const q = currentQty();
      const total = Math.round(unitPrice * q * 100) / 100;
      summary.textContent = '单价 $' + unitDisplay + ' × ' + q + ' 份 = 总计 $' + total.toFixed(2);
    }
    qtyInput.addEventListener('input', refreshSummary);
    refreshSummary();

    const bodyNode = el('div', { class: 'purchase-dialog' }, [
      el('div', { class: 'purchase-item' }, [
        shop.item_image ? el('img', {
          class: 'purchase-img', src: shop.item_image, alt: '',
          onerror: function () { this.style.display = 'none'; }
        }) : null,
        el('div', { style: { minWidth: '0', flex: '1' } }, [
          el('div', { class: 'purchase-name', text: itemName }),
          el('div', { class: 'purchase-sub', text: (ownerName ? '店主: ' + ownerName + ' · ' : '') + '世界: ' + (shop.world || '-') + ' · (' + shop.x + ', ' + shop.y + ', ' + shop.z + ')' })
        ])
      ]),
      el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: logged ? '游戏 ID（已登录）' : '游戏 ID（未登录需在线 + 密码验证）' }),
        nameInput
      ]),
      logged ? null : el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: '游戏密码（AuthMe，验证身份）' }),
        passInput
      ]),
      el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: '购买份数（1 份 = ' + stackAmount + ' 个，最多 64 份）' }),
        el('div', { style: { display: 'flex', alignItems: 'center' } }, [qtyInput, maxBtn])
      ]),
      summary,
      balanceInfo,
      el('div', { class: 'purchase-tip', text: logged ? '✔ 已登录游戏账号：可离线购买，物品将在你上线时自动发放。' : '⚠ 未登录：需要玩家在线且验证游戏密码；物品直接放入背包。' })
    ]);

    let modalRef = null;
    modalRef = Modal.show({
      title: '在线购买 · ' + itemName,
      body: bodyNode,
      confirmText: '确认购买',
      onConfirm: function () {
        const player = nameInput.value.trim();
        if (!player) { Toast.show('请输入你的游戏 ID', 'error'); return false; }
        if (!/^[A-Za-z0-9_]{1,16}$/.test(player)) { Toast.show('游戏 ID 只能包含字母、数字、下划线（1-16 位）', 'error'); return false; }
        const password = logged ? '' : passInput.value;
        if (!logged && !password) { Toast.show('未登录需要输入游戏密码（AuthMe）验证身份', 'error'); return false; }
        const qty = currentQty();
        try { modalRef.setProcessing('处理中...'); } catch (e) { }
        QSDB.purchaseShop(shop.shop_id, player, qty, password).then(function (r) {
          if (r && r.success) {
            try { localStorage.setItem('qsw_player_name', player); } catch (e) { }
            Toast.show('✓ 购买成功：' + r.item + ' ×' + r.amount + ' 份，花费 $' + r.total_price + '，余额 $' + r.balance_left, 'success');
            QSDB.clearCache();
            refreshTradePage();
            try { modalRef.close(); } catch (e) { }
            // 稍后刷新当前物品详情页（库存已变化）
            setTimeout(function () {
              try {
                const content = $('#content-area');
                if (!content) return;
                const panels = content.querySelectorAll('.tab-panel');
                for (let i = 0; i < panels.length; i++) {
                  const p = panels[i];
                  if (p.style.display !== 'none' && p.dataset.tab === 'itemDetail_' + (shop.material || '')) {
                    p.innerHTML = '';
                    renderIntoPanel(p, function () { return initItemDetail(shop.material); });
                    break;
                  }
                }
              } catch (e) { }
            }, 400);
          } else {
            Toast.show('购买失败：' + ((r && r.error) || '未知错误'), 'error');
            try { modalRef.setDone('确认购买'); } catch (e) { }
          }
        }).catch(function (e) {
          Toast.show('购买失败：' + (e && e.message ? e.message : e), 'error');
          try { modalRef.setDone('确认购买'); } catch (e2) { }
        });
        return false; // 异步处理，保持弹窗开启
      }
    });
  }

  // 交易成功后：若当前停留在 购买/收购 页则静默刷新
  function refreshTradePage() {
    try {
      const content = $('#content-area');
      if (!content) return;
      const panels = content.querySelectorAll('.tab-panel');
      for (let i = 0; i < panels.length; i++) {
        const p = panels[i];
        if (p.style.display === 'none') continue;
        const t = p.dataset.tab;
        if (t === 'buy' || t === 'sell') {
          p.innerHTML = '';
          renderIntoPanel(p, function () { return t === 'buy' ? initBuyPage() : initSellPage(); });
        }
        break;
      }
    } catch (e) { }
  }

  // 交易页公共骨架（购买/收购共用）：搜索 + 排序 + 分页 + 商店卡片网格
  async function initTradePage(pageKey, shopType, title, subtitle, emptyText) {
    const root = ensureTabPanel(pageKey);
    if (!root) return;
    root.innerHTML = '';
    root.appendChild(el('div', { class: 'page-header' }, [
      el('h2', { class: 'page-title', text: title }),
      el('div', { class: 'page-subtitle', text: subtitle })
    ]));

    const toolbar = el('div', { class: 'toolbar', style: { marginBottom: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' } });
    const searchInput = el('input', { type: 'text', placeholder: '搜索物品 / 店主（支持拼音 zhizhu / 首字母 zz）', class: 'filter-input', style: { flex: '2', minWidth: '220px', padding: '10px 14px', border: '3px solid #000', borderRadius: '4px' } });
    const sortSelect = el('select', { class: 'filter-select' });
    [['price_asc', '价格低→高'], ['price_desc', '价格高→低'], ['newest', '最新']].forEach(function (o) {
      sortSelect.appendChild(el('option', { value: o[0], text: o[1] }));
    });
    const refreshBtn = el('button', { class: 'neo-btn', text: '刷新' });
    toolbar.appendChild(searchInput);
    toolbar.appendChild(sortSelect);
    toolbar.appendChild(refreshBtn);
    root.appendChild(toolbar);

    const info = el('div', { style: { margin: '6px 0 12px', color: '#6b7280', fontSize: '13px' } });
    root.appendChild(info);
    const grid = el('div', { class: 'shop-grid' });
    root.appendChild(grid);
    const pager = el('div', { style: { display: 'flex', gap: '8px', justifyContent: 'center', margin: '16px 0' } });
    root.appendChild(pager);

    const PAGE = 24;
    let page = 1;

    async function load() {
      grid.innerHTML = '';
      grid.appendChild(el('div', { style: { padding: '20px', color: '#6b7280', gridColumn: '1/-1' }, text: '加载中...' }));
      const r = await QSDB.search(searchInput.value.trim(), { shop_type: shopType, sort: sortSelect.value, page: page, pageSize: PAGE });
      grid.innerHTML = '';
      pager.innerHTML = '';
      if (!r.success) {
        grid.appendChild(el('div', { style: { padding: '20px', color: '#991b1b', gridColumn: '1/-1' }, text: '加载失败: ' + (r.error || '未知错误') }));
        info.textContent = '';
        return;
      }
      if (!r.results.length) {
        grid.appendChild(el('div', { style: { padding: '20px', color: '#6b7280', gridColumn: '1/-1' }, text: emptyText }));
        info.textContent = '';
        return;
      }
      r.results.forEach(function (s) { grid.appendChild(renderShopDetailCard(s)); });
      info.textContent = '共 ' + r.total + ' 个商店 · 第 ' + r.page + ' / ' + r.total_pages + ' 页';
      if (r.total_pages > 1) {
        const prev = el('button', { class: 'neo-btn', text: '上一页' });
        const next = el('button', { class: 'neo-btn', text: '下一页' });
        prev.disabled = r.page <= 1;
        next.disabled = r.page >= r.total_pages;
        prev.onclick = function () { if (page > 1) { page--; load(); } };
        next.onclick = function () { if (page < r.total_pages) { page++; load(); } };
        pager.appendChild(prev);
        pager.appendChild(next);
      }
    }
    refreshBtn.onclick = function () { QSDB.clearCache(); load(); };
    let timer = null;
    searchInput.oninput = function () { clearTimeout(timer); timer = setTimeout(function () { page = 1; load(); }, 350); };
    sortSelect.onchange = function () { page = 1; load(); };
    load();
  }

  //  购买界面（玩家视角：浏览出售商店并购买）
  function initBuyPage() {
    return initTradePage('buy', 'SELLING', '🛒 购买界面', '服务器上所有出售商店 · 直接在线购买（玩家需在游戏中）', '没有找到出售中的商店');
  }

  //  收购界面（玩家视角：把物品卖给收购商店）
  function initSellPage() {
    return initTradePage('sell', 'BUYING', '💰 收购界面', '服务器上所有收购商店 · 把背包里的物品卖给他们（玩家需在游戏中）', '没有找到收购中的商店');
  }

  //  在线出售弹窗（卖给收购商店，报酬到账，物品从背包扣除）
  function showSellModal(shop) {
    const unitPrice = Number(shop.price || 0);
    const unitDisplay = (shop.price_display !== undefined && shop.price_display !== null)
      ? String(shop.price_display)
      : unitPrice.toFixed(2);
    const stackAmount = Math.max(1, parseInt(shop.stacking_amount, 10) || 1);
    const itemName = shop.shop_cn_name || shop.item_name || shop.material || '物品';
    const ownerName = shopOwnerName(shop);

    const logged = !!state.playerName;
    let savedName = '';
    try { savedName = localStorage.getItem('qsw_player_name') || ''; } catch (e) { }

    const nameInput = el('input', {
      type: 'text', class: 'purchase-input', placeholder: '请输入你的游戏 ID',
      value: logged ? state.playerName : savedName, maxlength: '16', style: { width: '100%' }
    });
    if (logged) nameInput.setAttribute('readonly', 'readonly');
    const passInput = el('input', {
      type: 'password', class: 'purchase-input', placeholder: '游戏密码（AuthMe，验证身份）', style: { width: '100%' }
    });
    const qtyInput = el('input', {
      type: 'number', class: 'purchase-input purchase-qty', value: '1',
      min: '1', max: '64', style: { width: '110px' }
    });
    const maxBtn = el('button', { class: 'neo-btn', text: '最大', style: { marginLeft: '8px', padding: '8px 14px' } });
    const summary = el('div', { class: 'purchase-summary' });
    const balanceInfo = el('div', { style: { fontSize: '13px', fontWeight: '600', color: '#065f46', marginTop: '-4px' } });
    if (logged && state.playerBalance != null) {
      balanceInfo.textContent = '💰 你的余额: $' + state.playerBalance;
    } else {
      balanceInfo.textContent = '💰 输入游戏 ID 后自动查询余额';
      nameInput.addEventListener('change', function () {
        const n = nameInput.value.trim();
        if (!n) return;
        balanceInfo.textContent = '💰 查询中...';
        QSDB.getWallet(n).then(function (w) {
          if (w && w.success && w.balance != null) {
            balanceInfo.textContent = '💰 ' + n + ' 的余额: $' + w.balance;
          } else {
            balanceInfo.textContent = '💰 无法查询余额（经济插件未安装或账户不存在）';
          }
        }).catch(function () { balanceInfo.textContent = '💰 余额查询失败'; });
      });
    }
    maxBtn.onclick = async function () {
      const p = nameInput.value.trim();
      if (!p) { Toast.show('请先输入游戏 ID', 'warning'); return; }
      maxBtn.disabled = true;
      const r = await QSDB.inventoryCheck(shop.shop_id, p);
      maxBtn.disabled = false;
      if (!r || !r.success) { Toast.show('查询失败: ' + ((r && r.error) || '未知错误'), 'error'); return; }
      if (!r.online) { Toast.show('玩家不在线，无法查询背包（收购需要在线）', 'warning'); return; }
      const can = Math.max(0, Math.floor(Number(r.can_sell) || 0));
      if (can < 1) { Toast.show('背包里没有足够的物品可卖', 'warning'); return; }
      qtyInput.value = String(Math.min(64, can));
      refreshSummary();
    };

    function currentQty() {
      return Math.max(1, Math.min(64, parseInt(qtyInput.value, 10) || 1));
    }
    function refreshSummary() {
      const q = currentQty();
      const total = Math.round(unitPrice * q * 100) / 100;
      summary.textContent = '收购单价 $' + unitDisplay + ' × ' + q + ' 份 = 你将获得 $' + total.toFixed(2);
    }
    qtyInput.addEventListener('input', refreshSummary);
    refreshSummary();

    const bodyNode = el('div', { class: 'purchase-dialog' }, [
      el('div', { class: 'purchase-item' }, [
        shop.item_image ? el('img', {
          class: 'purchase-img', src: shop.item_image, alt: '',
          onerror: function () { this.style.display = 'none'; }
        }) : null,
        el('div', { style: { minWidth: '0', flex: '1' } }, [
          el('div', { class: 'purchase-name', text: itemName }),
          el('div', { class: 'purchase-sub', text: (ownerName ? '店主: ' + ownerName + ' · ' : '') + '世界: ' + (shop.world || '-') + ' · (' + shop.x + ', ' + shop.y + ', ' + shop.z + ')' })
        ])
      ]),
      el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: logged ? '游戏 ID（已登录）' : '游戏 ID（未登录需在线 + 密码验证）' }),
        nameInput
      ]),
      logged ? null : el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: '游戏密码（AuthMe，验证身份）' }),
        passInput
      ]),
      el('div', { class: 'purchase-field' }, [
        el('label', { class: 'purchase-label', text: '出售份数（1 份 = ' + stackAmount + ' 个，最多 64 份）' }),
        el('div', { style: { display: 'flex', alignItems: 'center' } }, [qtyInput, maxBtn])
      ]),
      summary,
      balanceInfo,
      el('div', { class: 'purchase-tip', text: '⚠ 物品将从你的背包扣除；报酬直接存入你的账户。' + (ownerName ? '' : '（系统商店：无限收购）') })
    ]);

    let modalRef = null;
    modalRef = Modal.show({
      title: '在线出售 · ' + itemName,
      body: bodyNode,
      confirmText: '确认出售',
      onConfirm: function () {
        const player = nameInput.value.trim();
        if (!player) { Toast.show('请输入你的游戏 ID', 'error'); return false; }
        if (!/^[A-Za-z0-9_]{1,16}$/.test(player)) { Toast.show('游戏 ID 只能包含字母、数字、下划线（1-16 位）', 'error'); return false; }
        const password = logged ? '' : passInput.value;
        if (!logged && !password) { Toast.show('未登录需要输入游戏密码（AuthMe）验证身份', 'error'); return false; }
        const qty = currentQty();
        try { modalRef.setProcessing('处理中...'); } catch (e) { }
        QSDB.sellShop(shop.shop_id, player, qty, password).then(function (r) {
          if (r && r.success) {
            try { localStorage.setItem('qsw_player_name', player); } catch (e) { }
            Toast.show('✓ 出售成功：' + r.item + ' ×' + r.amount + ' 份，获得 $' + r.total_price + '，余额 $' + r.balance_left, 'success');
            QSDB.clearCache();
            try { modalRef.close(); } catch (e) { }
            refreshTradePage();
          } else {
            Toast.show('出售失败：' + ((r && r.error) || '未知错误'), 'error');
            try { modalRef.setDone('确认出售'); } catch (e) { }
          }
        }).catch(function (e) {
          Toast.show('出售失败：' + (e && e.message ? e.message : e), 'error');
          try { modalRef.setDone('确认出售'); } catch (e2) { }
        });
        return false; // 异步处理，保持弹窗开启
      }
    });
  }

  //  商店浏览页 (独立)
  async function initShops() {
    const root = ensureTabPanel();
    if (!root) return;
    const title = el('h2', { style: { fontSize: '20px', marginBottom: '16px' }, text: '全量商店浏览' });
    root.appendChild(title);

    const toolbar = el('div', { class: 'toolbar', style: { marginBottom: '12px' } });
    const searchInput = el('input', { type: 'text', placeholder: '搜索...（支持拼音 zhizhu / 首字母 zz）', style: { flex: '2', minWidth: '280px', padding: '10px 14px', border: '3px solid #000', borderRadius: '4px' } });
    const matSelect = el('select', { class: 'filter-select' });
    matSelect.appendChild(el('option', { value: '', text: '全部物品' }));
    const sortSelect = el('select', { class: 'filter-select' });
    [['relevance', '默认'], ['price_asc', '价格低→高'], ['price_desc', '价格高→低'], ['owner', '店主A-Z'], ['newest', '最新']].forEach(([v, l]) => {
      sortSelect.appendChild(el('option', { value: v, text: l }));
    });
    toolbar.appendChild(searchInput);
    toolbar.appendChild(matSelect);
    toolbar.appendChild(sortSelect);
    root.appendChild(toolbar);

    const grid = el('div', { class: 'shop-grid', style: { gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))' } });
    const status = el('div', { style: { textAlign: 'center', margin: '16px', fontSize: '13px', color: '#6b7280' } });
    const moreBtn = el('button', {
      class: 'neo-btn',
      style: { display: 'none', margin: '10px auto 0', padding: '10px 28px' },
      text: '加载更多'
    });
    root.appendChild(grid);
    root.appendChild(status);
    root.appendChild(moreBtn);

    let page = 1, total = 0, rendered = 0, loading = false;

    async function doSearch(reset) {
      if (loading) return;
      loading = true;
      if (reset) { page = 1; total = 0; rendered = 0; grid.innerHTML = ''; }
      try {
        const data = await QSDB.search(searchInput.value, {
          page: page, pageSize: 40, material: matSelect.value || undefined, sort: sortSelect.value
        });
        if (data.success) {
          total = data.total || 0;
          (data.results || []).forEach((shop) => grid.appendChild(renderShopCard(shop)));
          rendered += (data.results || []).length;
          status.textContent = '已显示 ' + rendered + ' / 共 ' + total + ' 条';
          moreBtn.style.display = rendered < total ? 'block' : 'none';
          // 注册滚动动画观察
          observeScrollAnimations(grid);
        }
      } catch (e) { status.textContent = '加载失败: ' + e.message; }
      finally { loading = false; }
    }
    moreBtn.onclick = () => { page++; doSearch(false); };
    const debounced = debounce(() => doSearch(true), 250);
    searchInput.addEventListener('input', debounced);
    matSelect.addEventListener('change', () => doSearch(true));
    sortSelect.addEventListener('change', () => doSearch(true));

    // 加载材料分类
    try {
      const mats = await QSDB.getMaterials();
      if (Array.isArray(mats)) {
        mats.forEach((m) => {
          const name = typeof m === 'string' ? m : m.material;
          matSelect.appendChild(el('option', { value: name, text: name }));
        });
      }
    } catch (e) {}

    doSearch(true);

    // 实时数据：每 15 秒自动刷新（仅当无搜索/无筛选、停留第一页、未向下浏览时，避免打扰操作）
    const autoTimer = setInterval(function () {
      if (!grid.isConnected) { clearInterval(autoTimer); return; } // 页面已切换 → 自动停止
      if (loading || page !== 1) return;
      if ((searchInput.value || '').trim()) return;
      if (matSelect.value) return;
      if (sortSelect.value && sortSelect.value !== 'relevance') return;
      if (window.scrollY > 400) return;
      doSearch(true);
    }, 15000);
  }

  //  数据管理: 生成测试数据 / 清空 / 强制终止
  async function initAdmin() {
    const root = ensureTabPanel();
    if (!root) return;

    // 欢迎标题
    root.appendChild(el('h2', { style: { fontSize: '20px', marginBottom: '16px' }, text: '📊 数据管理' }));

    // --- 卡片 0: 页面可见性 ---
    const visCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '👁 页面可见性' }),
      el('p', { class: 'hint', text: '勾选后该页面将对普通访客隐藏（管理员登录后仍可见）；保存后对所有人立即生效。' })
    ]);
    const visList = el('div', { style: { display: 'flex', gap: '16px', flexWrap: 'wrap', margin: '10px 0' } });
    const visBoxes = {};
    Object.keys(PAGE_LABELS).forEach(function (tab) {
      const cb = el('input', { type: 'checkbox', style: { marginRight: '6px' } });
      cb.checked = hiddenPages.indexOf(tab) >= 0;
      visBoxes[tab] = cb;
      visList.appendChild(el('label', { style: { display: 'flex', alignItems: 'center', fontSize: '13px', fontWeight: '600', cursor: 'pointer' } }, [cb, PAGE_LABELS[tab]]));
    });
    visCard.appendChild(visList);
    const visSave = el('button', { class: 'neo-btn primary', text: '保存可见性设置' });
    visSave.onclick = async function () {
      const hidden = Object.keys(visBoxes).filter(function (t) { return visBoxes[t].checked; });
      visSave.disabled = true;
      const r = await QSDB.setPages(hidden);
      visSave.disabled = false;
      if (r && r.success) {
        hiddenPages = r.hidden_pages || hidden;
        applyPageVisibility();
        Toast.show(hidden.length ? ('已隐藏 ' + hidden.length + ' 个页面') : '全部页面已设为可见', 'success');
      } else {
        Toast.show('保存失败: ' + ((r && r.error) || '未知错误'), 'error');
      }
    };
    visCard.appendChild(visSave);
    root.appendChild(visCard);

    // --- 卡片 0.5: 交易统计中心 ---
    const tradeCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '📈 交易统计中心' }),
      el('p', { class: 'hint', text: '网页 + 游戏内交易明细聚合（基于最近 1 万条记录）；购买=玩家买入，收购=玩家卖出。' })
    ]);
    const tradeStats = el('div', { style: { display: 'flex', gap: '20px', flexWrap: 'wrap', margin: '10px 0', fontSize: '13px', fontWeight: '600' } });
    tradeCard.appendChild(tradeStats);
    const tradeCharts = el('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '12px' } });
    tradeCard.appendChild(tradeCharts);
    const tradeEcon = el('div', { style: { marginTop: '12px', padding: '10px', border: '2px dashed #059669', borderRadius: '6px', background: '#ecfdf5', fontSize: '13px', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '6px' } });
    tradeCard.appendChild(tradeEcon);
    const tradeRanks = el('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: '12px', marginTop: '12px' } });
    tradeCard.appendChild(tradeRanks);
    const tradeBtns = el('div', { style: { display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '12px' } });
    const exportTradesBtn = el('button', { class: 'neo-btn primary', text: '导出交易记录 CSV' });
    const importHistoryBtn = el('button', { class: 'neo-btn', text: '📥 导入 QuickShop 历史交易' });
    const clearTradesBtn = el('button', { class: 'neo-btn danger', text: '清空交易记录' });
    tradeBtns.appendChild(exportTradesBtn);
    tradeBtns.appendChild(importHistoryBtn);
    tradeBtns.appendChild(clearTradesBtn);
    tradeCard.appendChild(tradeBtns);
    root.appendChild(tradeCard);

    // --- 卡片: 商店批量清理 ---
    const purgeCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '🧹 商店批量清理' }),
      el('p', { class: 'hint', text: '按玩家名查询其名下全部商店并一键删除。删除前会自动加载商店所在区块（修复"批量删除留下残留、删不掉"的问题），删除记录会写入「移除商店记录」。删除不可恢复，请谨慎操作。' })
    ]);
    const purgeRow = el('div', { style: { display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center', marginTop: '8px' } });
    const purgeInput = el('input', { class: 'filter-input', placeholder: '玩家名（如 Jinx）', style: { maxWidth: '220px' } });
    const purgeQueryBtn = el('button', { class: 'neo-btn', text: '查询其商店' });
    purgeRow.appendChild(purgeInput);
    purgeRow.appendChild(purgeQueryBtn);
    purgeCard.appendChild(purgeRow);
    const purgeInfo = el('div', { style: { marginTop: '10px', fontSize: '13px', lineHeight: '1.8' } });
    purgeCard.appendChild(purgeInfo);
    const purgeDelBtn = el('button', { class: 'neo-btn danger', text: '删除全部', style: { display: 'none', marginTop: '10px' } });
    purgeCard.appendChild(purgeDelBtn);
    root.appendChild(purgeCard);

    let purgeIds = [];
    let purgeOwner = '';
    async function purgeQuery() {
      const name = (purgeInput.value || '').trim();
      if (!name) { Toast.show('请先输入玩家名', 'warning'); return; }
      purgeInfo.textContent = '查询中...';
      purgeDelBtn.style.display = 'none';
      purgeIds = [];
      const r = await QSDB.adminSearchShops({ owner: name, pageSize: 500 });
      if (!r || !r.success) { purgeInfo.textContent = '查询失败: ' + ((r && r.error) || '需要管理员登录'); return; }
      const list = r.shops || [];
      const total = r.total || list.length;
      if (!list.length) { purgeInfo.textContent = '没有找到该玩家名下的商店'; return; }
      purgeIds = list.map(function (s) { return s.shop_id; });
      purgeOwner = name;
      purgeInfo.innerHTML = '';
      purgeInfo.appendChild(el('div', {}, [
        el('b', { text: '找到 ' + total + ' 家商店' }),
        el('span', { class: 'muted-text', text: total > 500 ? '（一次最多处理 500 家，请分批执行）' : '' })
      ]));
      list.slice(0, 6).forEach(function (s) {
        purgeInfo.appendChild(el('div', { class: 'muted-text', style: { fontSize: '12px' }, text: '· #' + s.shop_id + ' ' + (s.item_name || s.material || '?') + ' · ' + (s.shop_type === 'BUYING' ? '收购' : '出售') + ' · $' + s.price }));
      });
      if (list.length > 6) purgeInfo.appendChild(el('div', { class: 'muted-text', style: { fontSize: '12px' }, text: '…… 等共 ' + list.length + ' 家' }));
      purgeDelBtn.textContent = '⚠️ 删除全部 ' + list.length + ' 家商店';
      purgeDelBtn.style.display = '';
    }
    purgeQueryBtn.onclick = purgeQuery;
    purgeDelBtn.onclick = function () {
      if (!purgeIds.length) return;
      Modal.confirm({
        title: '删除商店（不可恢复）',
        danger: true,
        body: '<div>即将删除玩家 <b>' + purgeOwner + '</b> 名下的 <b>' + purgeIds.length + '</b> 家商店。</div><div class="hint" style="margin-top:6px">删除前自动加载各商店区块；此操作不可恢复。</div>',
        confirmText: '确认删除',
        onConfirm: async function () {
          Toast.show('正在删除 ' + purgeIds.length + ' 家商店（自动加载区块）...', 'info');
          const r = await QSDB.adminBatch(purgeIds, 'delete');
          if (r && r.success) {
            Toast.show('删除完成：成功 ' + (r.deleted_count || 0) + ' / ' + purgeIds.length + ' 家', 'success');
            purgeQuery();
          } else {
            Toast.show('删除失败: ' + ((r && r.error) || '未知错误'), 'error');
          }
        }
      });
    };

    // --- 卡片: 移除商店记录（表格） ---
    const remCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '📋 移除商店记录' }),
      el('p', { class: 'hint', text: '所有删除的商店都会记录在这里：游戏内删除（含 /qs removeall 批量命令）、WorldEdit/方块破坏、网页后台删除。点「导入 QuickShop 历史交易」可找回历史删除记录。' })
    ]);
    const remBar = el('div', { style: { display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center', margin: '8px 0' } });
    const remInfo = el('span', { style: { fontSize: '13px', fontWeight: '600' } });
    const remRefreshBtn = el('button', { class: 'neo-btn', text: '🔄 刷新' });
    const remExportBtn = el('button', { class: 'neo-btn', text: '导出 CSV' });
    remBar.appendChild(remInfo);
    remBar.appendChild(remRefreshBtn);
    remBar.appendChild(remExportBtn);
    remCard.appendChild(remBar);
    const remTableWrap = el('div', { style: { overflowX: 'auto' } });
    remCard.appendChild(remTableWrap);
    const remPager = el('div', { style: { display: 'flex', gap: '10px', alignItems: 'center', marginTop: '10px', fontSize: '13px' } });
    const remPrev = el('button', { class: 'neo-btn', text: '← 上一页' });
    const remNext = el('button', { class: 'neo-btn', text: '下一页 →' });
    const remPageInfo = el('span', {});
    remPager.appendChild(remPrev);
    remPager.appendChild(remNext);
    remPager.appendChild(remPageInfo);
    remCard.appendChild(remPager);
    root.appendChild(remCard);

    let remData = [];
    let remPage = 1;
    const remPageSize = 50;
    function remFmtTime(ms) {
      const d = new Date(Number(ms) || 0);
      const p = function (n) { return (n < 10 ? '0' : '') + n; };
      return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function remCell(value) {
      const s = value == null || value === '' ? '—' : String(value);
      return el('td', { style: { border: '1px solid #000', padding: '5px 8px', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, [
        el('span', { text: s, title: s })
      ]);
    }
    function remRender() {
      remTableWrap.innerHTML = '';
      if (!remData.length) {
        remTableWrap.appendChild(el('div', { class: 'muted-text', style: { padding: '12px 0', fontSize: '13px' }, text: '暂无移除记录。（新的删除会实时记录；历史删除可点「导入 QuickShop 历史交易」找回）' }));
        remInfo.textContent = '共 0 条';
        remPageInfo.textContent = '';
        return;
      }
      const pages = Math.max(1, Math.ceil(remData.length / remPageSize));
      if (remPage > pages) remPage = pages;
      const start = (remPage - 1) * remPageSize;
      const rows = remData.slice(start, start + remPageSize);
      const tbl = el('table', { style: { width: '100%', borderCollapse: 'collapse', fontSize: '12px' } });
      const thead = el('thead', {});
      const htr = el('tr', {});
      ['时间', '来源', '操作者', '原因', '店主', '物品', '位置'].forEach(function (h) {
        htr.appendChild(el('th', { style: { border: '2px solid #000', padding: '6px 8px', background: '#fef08a', textAlign: 'left', whiteSpace: 'nowrap' }, text: h }));
      });
      thead.appendChild(htr);
      tbl.appendChild(thead);
      const tbody = el('tbody', {});
      rows.forEach(function (x) {
        const tr = el('tr', {});
        tr.appendChild(remCell(remFmtTime(x.t)));
        tr.appendChild(remCell(x.source === 'history' ? '历史导入' : '实时记录'));
        tr.appendChild(remCell(x.player || ''));
        tr.appendChild(remCell(x.reason || ''));
        tr.appendChild(remCell(x.owner || ''));
        tr.appendChild(remCell(x.item || ''));
        tr.appendChild(remCell(x.world ? (x.world + ' ' + x.x + ',' + x.y + ',' + x.z) : ''));
        tbody.appendChild(tr);
      });
      tbl.appendChild(tbody);
      remTableWrap.appendChild(tbl);
      remInfo.textContent = '共 ' + remData.length + ' 条记录';
      remPageInfo.textContent = '第 ' + remPage + ' / ' + pages + ' 页';
      remPrev.disabled = remPage <= 1;
      remNext.disabled = remPage >= pages;
    }
    async function remLoad() {
      remInfo.textContent = '加载中...';
      const r = await QSDB.getShopRemovals();
      if (!r || !r.success) { remInfo.textContent = '加载失败: ' + ((r && r.error) || '需要管理员登录'); return; }
      remData = r.removals || [];
      remPage = 1;
      remRender();
    }
    remRefreshBtn.onclick = remLoad;
    remExportBtn.onclick = function () { exportShopRemovalsCsv(); };
    remPrev.onclick = function () { if (remPage > 1) { remPage--; remRender(); } };
    remNext.onclick = function () { if (remPage < Math.ceil(remData.length / remPageSize)) { remPage++; remRender(); } };
    remLoad();

    let tradesCache = [];
    const num2 = function (v) { const n = Number(v) || 0; return Math.round(n * 100) / 100; };
    const pad2 = function (n) { return (n < 10 ? '0' : '') + n; };
    function isSystemOwner(owner) {
      if (owner == null) return true;
      const o = String(owner).trim().toLowerCase();
      return o === '' || o === '系统商店' || o === '无限' || o === 'unlimited' || o === 'server' || o === 'console' || o === 'unknown';
    }
    function bumpMap(map, key, amount, items) {
      let e = map.get(key);
      if (!e) { e = { count: 0, amount: 0, items: 0 }; map.set(key, e); }
      e.count++;
      e.amount += amount;
      e.items += items;
    }
    function barPanel(title, labels, fullLabels, series) {
      const box = el('div', { style: { border: '2px solid #000', borderRadius: '6px', padding: '10px', background: '#fff' } });
      box.appendChild(el('div', { style: { fontWeight: '700', marginBottom: '8px', fontSize: '13px' }, text: title }));
      const allVals = [];
      series.forEach(function (s) { s.data.forEach(function (v) { allVals.push(v || 0); }); });
      const max = Math.max(1, ...allVals);
      const chart = el('div', { style: { display: 'flex', alignItems: 'flex-end', gap: '2px', height: '110px' } });
      for (let i = 0; i < labels.length; i++) {
        const tip = fullLabels ? (fullLabels[i] + '\n' + series.map(function (s) { return s.name + ': ' + (s.data[i] || 0); }).join('\n')) : '';
        const col = el('div', { title: tip, style: { flex: '1', minWidth: '4px', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', alignItems: 'center', height: '100%' } });
        const bars = el('div', { style: { display: 'flex', gap: '1px', alignItems: 'flex-end', height: '88px' } });
        series.forEach(function (s) {
          const h = Math.max(1, Math.round(((s.data[i] || 0) / max) * 86));
          bars.appendChild(el('div', { style: { width: '5px', height: h + 'px', background: s.color, borderRadius: '2px 2px 0 0' } }));
        });
        col.appendChild(bars);
        col.appendChild(el('div', { style: { fontSize: '9px', color: '#6b7280', marginTop: '2px' }, text: labels[i] }));
        chart.appendChild(col);
      }
      box.appendChild(chart);
      const legend = el('div', { style: { display: 'flex', gap: '12px', marginTop: '6px', fontSize: '11px', flexWrap: 'wrap' } });
      series.forEach(function (s) {
        legend.appendChild(el('span', {}, [el('span', { style: { display: 'inline-block', width: '10px', height: '10px', background: s.color, marginRight: '4px', borderRadius: '2px', verticalAlign: 'middle' } }), s.name]));
      });
      box.appendChild(legend);
      return box;
    }
    function rankPanel(title, map, fmt) {
      const box = el('div', { style: { border: '2px solid #000', borderRadius: '6px', padding: '10px', background: '#fff' } });
      box.appendChild(el('div', { style: { fontWeight: '700', marginBottom: '6px', fontSize: '13px' }, text: title }));
      const arr = Array.from(map.entries()).sort(function (a, b) { return b[1].amount - a[1].amount; }).slice(0, 10);
      if (!arr.length) {
        box.appendChild(el('div', { style: { color: '#9ca3af', fontSize: '12px' }, text: '暂无数据' }));
        return box;
      }
      arr.forEach(function (entry, i) {
        const row = el('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: '12px', padding: '3px 0', borderBottom: '1px dashed #e5e7eb' } });
        row.appendChild(el('span', { title: entry[0], style: { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '62%' }, text: (i + 1) + '. ' + entry[0] }));
        row.appendChild(el('span', { style: { fontWeight: '700', flexShrink: '0' }, text: fmt(entry[1]) }));
        box.appendChild(row);
      });
      return box;
    }

    async function loadTradesPanel() {
      tradeStats.textContent = '加载中...';
      const r = await QSDB.getTrades();
      if (!r || !r.success) {
        tradeStats.textContent = '加载失败: ' + ((r && r.error) || '未知错误（需要管理员登录）');
        return;
      }
      tradesCache = r.trades || [];

      // ===== 聚合 =====
      const byDay = new Map(), byHourBuy = new Array(24).fill(0), byHourSell = new Array(24).fill(0);
      const buyers = new Map(), sellers = new Map(), itemBuy = new Map(), itemSell = new Map();
      const owners = new Map(), shops = new Map();
      const players = new Set();
      let sysIn = 0, sysOut = 0, playerFlow = 0, taxSum = 0, totalAmount = 0, maxTrade = null;
      const nowD = new Date();
      const todayStart = new Date(nowD.getFullYear(), nowD.getMonth(), nowD.getDate()).getTime();
      const ydayStart = todayStart - 86400000;
      const d7Start = todayStart - 6 * 86400000;
      const d30Start = todayStart - 29 * 86400000;
      const acc = { todayBuy: 0, todayBuyAmount: 0, todaySell: 0, todaySellAmount: 0, ydayBuy: 0, ydayBuyAmount: 0, ydaySell: 0, ydaySellAmount: 0, d7Buy: 0, d7BuyAmount: 0, d7Sell: 0, d7SellAmount: 0, d30Buy: 0, d30BuyAmount: 0, d30Sell: 0, d30SellAmount: 0 };

      for (const t of tradesCache) {
        const ts = Number(t.t) || 0;
        const amount = Number(t.total) || 0;
        const items = Number(t.items) || 0;
        const isBuy = t.type === 'BUY';
        const player = t.player || '未知';
        const item = t.item || t.material || '未知';
        const owner = t.owner || '';
        const sys = isSystemOwner(owner);
        totalAmount += amount;
        players.add(player);
        if (t.tax) taxSum += Number(t.tax) || 0;
        if (!maxTrade || amount > Number(maxTrade.total)) maxTrade = t;
        if (isBuy) { if (sys) sysOut += amount; else playerFlow += amount; }
        else { if (sys) sysIn += amount; else playerFlow += amount; }

        const d = new Date(ts);
        const dayKey = d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
        let day = byDay.get(dayKey);
        if (!day) { day = { buy: 0, buyAmount: 0, sell: 0, sellAmount: 0 }; byDay.set(dayKey, day); }
        if (isBuy) { day.buy++; day.buyAmount += amount; } else { day.sell++; day.sellAmount += amount; }

        const hour = d.getHours();
        if (isBuy) byHourBuy[hour]++; else byHourSell[hour]++;

        bumpMap(isBuy ? buyers : sellers, player, amount, items);
        bumpMap(isBuy ? itemBuy : itemSell, item, amount, items);
        if (!sys) bumpMap(owners, owner, amount, items);
        bumpMap(shops, '#' + (t.shop_id || '?'), amount, items);

        if (ts >= todayStart) { if (isBuy) { acc.todayBuy++; acc.todayBuyAmount += amount; } else { acc.todaySell++; acc.todaySellAmount += amount; } }
        else if (ts >= ydayStart) { if (isBuy) { acc.ydayBuy++; acc.ydayBuyAmount += amount; } else { acc.ydaySell++; acc.ydaySellAmount += amount; } }
        if (ts >= d7Start) { if (isBuy) { acc.d7Buy++; acc.d7BuyAmount += amount; } else { acc.d7Sell++; acc.d7SellAmount += amount; } }
        if (ts >= d30Start) { if (isBuy) { acc.d30Buy++; acc.d30BuyAmount += amount; } else { acc.d30Sell++; acc.d30SellAmount += amount; } }
      }

      // ===== 概览 =====
      tradeStats.innerHTML = '';
      [
        '今日: 买 ' + acc.todayBuy + ' 笔 $' + num2(acc.todayBuyAmount) + ' · 卖 ' + acc.todaySell + ' 笔 $' + num2(acc.todaySellAmount),
        '昨日: 买 ' + acc.ydayBuy + ' 笔 $' + num2(acc.ydayBuyAmount) + ' · 卖 ' + acc.ydaySell + ' 笔 $' + num2(acc.ydaySellAmount),
        '近7日: 买 $' + num2(acc.d7BuyAmount) + ' · 卖 $' + num2(acc.d7SellAmount),
        '近30日: 买 $' + num2(acc.d30BuyAmount) + ' · 卖 $' + num2(acc.d30SellAmount),
        '总记录: ' + tradesCache.length + ' 笔'
      ].forEach(function (s) { tradeStats.appendChild(el('span', { text: s })); });

      // ===== 图表 =====
      tradeCharts.innerHTML = '';
      const days = [];
      for (let i = 13; i >= 0; i--) days.push(new Date(todayStart - i * 86400000));
      const dayLabels = [], dayFull = [], dBuyAmount = [], dSellAmount = [];
      days.forEach(function (dd) {
        const key = dd.getFullYear() + '-' + pad2(dd.getMonth() + 1) + '-' + pad2(dd.getDate());
        const st = byDay.get(key) || { buyAmount: 0, sellAmount: 0 };
        dayLabels.push(String(dd.getDate()));
        dayFull.push(key);
        dBuyAmount.push(num2(st.buyAmount));
        dSellAmount.push(num2(st.sellAmount));
      });
      tradeCharts.appendChild(barPanel('近 14 天交易金额（$）', dayLabels, dayFull, [
        { name: '购买', color: '#3b82f6', data: dBuyAmount },
        { name: '收购', color: '#f59e0b', data: dSellAmount }
      ]));
      const hourLabels = [], hourFull = [];
      for (let h = 0; h < 24; h++) { hourLabels.push(String(h)); hourFull.push(h + ' 时'); }
      tradeCharts.appendChild(barPanel('24 小时交易笔数', hourLabels, hourFull, [
        { name: '购买', color: '#3b82f6', data: byHourBuy },
        { name: '收购', color: '#f59e0b', data: byHourSell }
      ]));

      // ===== 经济流向 =====
      tradeEcon.innerHTML = '';
      const net = sysIn - sysOut;
      [
        '系统商店净增发: $' + num2(net) + '（' + (net >= 0 ? '◆ 印钞' : '▼ 回收') + '）',
        '玩家间流转: $' + num2(playerFlow),
        '税收合计: $' + num2(taxSum),
        '平均单笔: $' + num2(tradesCache.length ? totalAmount / tradesCache.length : 0),
        '活跃交易玩家: ' + players.size + ' 人',
        '最高单笔: ' + (maxTrade ? ('$' + num2(maxTrade.total) + '（' + (maxTrade.player || '?') + ' · ' + (maxTrade.item || '?') + '）') : '—')
      ].forEach(function (s) { tradeEcon.appendChild(el('span', { text: s })); });

      // ===== 排行榜 =====
      tradeRanks.innerHTML = '';
      tradeRanks.appendChild(rankPanel('🏆 消费榜（买家 Top10）', buyers, function (s) { return '$' + num2(s.amount) + ' · ' + s.count + '笔'; }));
      tradeRanks.appendChild(rankPanel('💰 收入榜（卖家 Top10）', sellers, function (s) { return '$' + num2(s.amount) + ' · ' + s.count + '笔'; }));
      tradeRanks.appendChild(rankPanel('🔥 热销榜（被买走 Top10）', itemBuy, function (s) { return num2(s.items) + ' 件 · $' + num2(s.amount); }));
      tradeRanks.appendChild(rankPanel('📦 收购榜（被卖出 Top10）', itemSell, function (s) { return num2(s.items) + ' 件 · $' + num2(s.amount); }));
      tradeRanks.appendChild(rankPanel('🏪 店主营收榜 Top10', owners, function (s) { return '$' + num2(s.amount) + ' · ' + s.count + '笔'; }));
      tradeRanks.appendChild(rankPanel('🏬 商店热度榜 Top10', shops, function (s) { return s.count + ' 笔 · $' + num2(s.amount); }));
    }
    exportTradesBtn.onclick = function () {
      exportTradesCsv(tradesCache);
    };
    importHistoryBtn.onclick = function () {
      importQsHistory(loadTradesPanel);
    };
    clearTradesBtn.onclick = function () {
      Modal.confirm({
        title: '清空交易记录',
        danger: true,
        body: '<div>确定清空全部交易记录吗？<b>此操作不可恢复</b>。</div>',
        confirmText: '确认清空',
        onConfirm: async function () {
          const r2 = await QSDB.clearTrades();
          if (r2 && r2.success) {
            Toast.show('交易记录已清空', 'success');
            loadTradesPanel();
          } else {
            Toast.show('清空失败: ' + ((r2 && r2.error) || '未知错误'), 'error');
          }
        }
      });
    };
    loadTradesPanel();

    // --- 卡片 1: 生成测试数据 + 强制终止 ---
    const seedCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '生成测试数据' }),
      el('p', { class: 'hint', text: '可快速生成随机商店数据用于测试。系统支持自定义数量 (1 - 5,000,000)，并可随时强制终止任务并清空已生成的 seed 数据。' })
    ]);

    // 数量选择
    const countRow = el('div', { style: { display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '10px' } });
    const countSelect = el('select', { class: 'filter-select', style: { flex: '1' } });
    [[100, '100 条'], [1000, '1,000 条'], [5000, '5,000 条'], [10000, '10,000 条'], [50000, '50,000 条'], [100000, '100,000 条'], [500000, '500,000 条'], [1000000, '1,000,000 条']].forEach(([v, t]) => {
      countSelect.appendChild(el('option', { value: String(v), text: t }));
    });
    countSelect.value = '10000';

    const customCountInput = el('input', { type: 'number', min: '1', max: '5000000', placeholder: '或输入自定义数量 (1 - 5,000,000)',
      style: { flex: '2', padding: '10px 12px', border: '3px solid #000', borderRadius: '4px', fontSize: '13px', fontWeight: '600' } });
    countRow.appendChild(countSelect);
    countRow.appendChild(customCountInput);
    seedCard.appendChild(countRow);
    seedCard.appendChild(el('div', { style: { fontSize: '11px', color: '#6b7280', marginBottom: '12px' },
      text: '从下拉选择预设数量，或直接输入自定义数量' }));

    // 模式选择
    const modeSelect = el('select', { class: 'filter-select', style: { marginRight: '10px' } });
    modeSelect.appendChild(el('option', { value: 'append', text: '追加到现有数据' }));
    modeSelect.appendChild(el('option', { value: 'replace', text: '替换（先清空后生成）' }));
    seedCard.appendChild(modeSelect);

    // 按钮组: 开始生成 + 强制终止
    const btnRow = el('div', { style: { display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap', marginTop: '12px' } });
    const seedBtn = el('button', { class: 'neo-btn primary', text: '开始生成' });
    const terminateBtn = el('button', {
      class: 'terminate-btn',
      text: '强制终止',
      style: { display: 'none' }
    });
    btnRow.appendChild(seedBtn);
    btnRow.appendChild(terminateBtn);
    seedCard.appendChild(btnRow);

    // 进度条 + 状态
    const progressTrack = el('div', { class: 'progress-track', style: { opacity: 0 } });
    const progressFill = el('div', { class: 'progress-fill' });
    progressTrack.appendChild(progressFill);
    seedCard.appendChild(progressTrack);

    const statusLine = el('div', { class: 'progress-status', text: '未开始' });
    seedCard.appendChild(statusLine);
    root.appendChild(seedCard);

    // --- 卡片 2: 清空所有商店数据 ---
    const clearCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '清空所有商店数据' }),
      el('p', { class: 'hint', text: '⚠️ 警告: 此操作将永久删除所有商店数据，不可恢复。建议先使用导出功能备份。' })
    ]);
    const clearBtn = el('button', {
      class: 'neo-btn danger',
      text: '立即清空所有数据',
      onclick: () => {
        Modal.confirm({
          title: '确认清空',
          danger: true,
          body: '<div>你确定要<b class="warn-text">永久清空所有商店数据</b>吗？</div><div class="muted-text">此操作不可恢复，请确保已完成重要数据的备份。</div>',
          confirmText: '确认清空',
          cancelText: '取消',
          onConfirm: async (close) => {
            try {
              const r = await QSDB.clear();
              if (r && r.cleared !== undefined) {
                Toast.show('已清空 ' + r.cleared + ' 条数据', 'success');
                statusLine.textContent = '已清空: ' + r.cleared + ' 条数据';
              } else {
                Toast.show('操作完成', 'success');
              }
            } catch (err) {
              Toast.show('清空失败: ' + err.message, 'error');
            }
          }
        });
      }
    });
    clearCard.appendChild(clearBtn);
    root.appendChild(clearCard);

    // === 卡片 3: 实时统计（数据库） ===
    const statsCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '📊 实时数量统计' }),
      el('p', { class: 'hint', text: '从数据库实时读取（绕过缓存），每 15 秒自动刷新一次。' })
    ]);
    const statsStatus = el('div', { class: 'progress-status', text: '加载中...' });
    const statsGrid = el('div', { class: 'items-grid', style: { gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' } });
    const statsRefreshBtn = el('button', { class: 'neo-btn primary', text: '立即刷新' });
    const statsAutoLabel = el('span', { text: '自动刷新中 (15s)', style: { marginLeft: '12px', color: '#059669' } });
    const statsBtnRow = el('div', { style: { display: 'flex', alignItems: 'center', marginTop: '12px' } });
    statsBtnRow.appendChild(statsRefreshBtn);
    statsBtnRow.appendChild(statsAutoLabel);
    statsCard.appendChild(statsStatus);
    statsCard.appendChild(statsGrid);
    statsCard.appendChild(statsBtnRow);
    root.appendChild(statsCard);

    function renderStats(data) {
      statsGrid.innerHTML = '';
      if (!data || !data.stats) return;
      const items = [
        { label: '总商店数',   v: data.stats.total_shops,    color: '#2563eb' },
        { label: '出售商店',   v: data.stats.total_selling,  color: '#059669' },
        { label: '收购商店',   v: data.stats.total_buying,   color: '#d97706' },
        { label: '总物品种类', v: data.stats.total_materials, color: '#7c3aed' },
        { label: '店主数',     v: data.stats.total_owners,   color: '#be185d' },
        { label: '世界数',     v: data.stats.total_worlds,   color: '#334155' },
        { label: '活跃度',     v: data.stats.total_activity, color: '#1d4ed8' },
        { label: '平均价格',   v: data.stats.avg_price != null ? Number(data.stats.avg_price).toFixed(2) : '-', color: '#b45309' },
        { label: '最低价格',   v: data.stats.min_price != null ? Number(data.stats.min_price).toFixed(2) : '-', color: '#065f46' },
        { label: '最高价格',   v: data.stats.max_price != null ? Number(data.stats.max_price).toFixed(2) : '-', color: '#991b1b' }
      ];
      items.forEach(it => {
        const box = el('div', { class: 'item-card-price-block', style: { border: `3px solid ${it.color}`, background: '#f8fafc' } });
        box.appendChild(el('div', { class: 'item-card-price-label', text: it.label, style: { color: it.color, fontSize: '12px', fontWeight: '700' } }));
        box.appendChild(el('div', { class: 'item-card-price-val', text: typeof it.v === 'number' ? it.v.toLocaleString() : String(it.v), style: { fontSize: '22px', fontWeight: '900' } }));
        statsGrid.appendChild(box);
      });
      statsStatus.textContent = '✅ 已同步 @ ' + data.timestamp + '（耗时 ' + data.elapsed_ms + 'ms）';
      if (data.memory && (data.memory.total_shops !== data.stats.total_shops)) {
        statsStatus.innerHTML += ' <span style="color:#b45309">⚠ 与内存缓存有差异 (' + data.memory.total_shops + ' vs ' + data.stats.total_shops + ')</span>';
      }
    }

    async function refreshStats() {
      try {
        const d = await QSDB.getRealtimeStats();
        if (d && d.success) renderStats(d);
        else statsStatus.textContent = '❌ ' + (d && d.error || '加载失败');
      } catch (e) { statsStatus.textContent = '❌ 加载失败: ' + e.message; }
    }
    statsRefreshBtn.addEventListener('click', refreshStats);
    // 初始加载 + 定时 15 秒自动刷新
    refreshStats();
    let statsTimer = setInterval(refreshStats, 15000);
    if (window.__adminStatsTimer) clearInterval(window.__adminStatsTimer);
    window.__adminStatsTimer = statsTimer;

    // === 卡片 4: 服务器重启 ===
    const restartCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '🔄 重启服务器' }),
      el('p', { class: 'hint', text: '修改 .env 后，需要重启服务器才能生效。点击下方按钮重启前端服务，将在 1.5 秒后自动开始（保留当前浏览器连接）。' })
    ]);
    const restartStatus = el('div', { class: 'progress-status', text: '待操作' });
    const restartBtn = el('button', {
      class: 'neo-btn danger',
      text: '立即重启服务器',
      onclick: () => {
        Modal.confirm({
          title: '确认重启',
          body: '<div><b class="warn-text">确定要重启服务器吗？</b></div><div class="muted-text">修改配置项后需要重启才能生效。重启期间页面将短暂不可用，约 5-10 秒。</div>',
          confirmText: '确认重启',
          danger: true,
          onConfirm: async () => {
            try {
              restartBtn.disabled = true;
              restartBtn.textContent = '重启中...';
              restartStatus.textContent = '正在请求服务器重启...';
              const r = await QSDB.restartServer();
              if (r && r.success) {
                restartStatus.textContent = '✅ 已提交重启请求，倒计时 ' + Math.ceil((r.restart_at - Date.now()) / 1000) + ' 秒。';
                // 5 秒后自动跳转到首页（触发页面刷新，重新连接新进程）
                setTimeout(() => { window.location.reload(); }, 5000);
              } else {
                restartStatus.textContent = '❌ ' + ((r && r.error) || '重启失败');
                restartBtn.disabled = false;
                restartBtn.textContent = '立即重启服务器';
              }
            } catch (e) {
              restartStatus.textContent = '❌ 错误: ' + e.message;
              restartBtn.disabled = false;
              restartBtn.textContent = '立即重启服务器';
            }
          }
        });
      }
    });
    restartCard.appendChild(restartBtn);
    restartCard.appendChild(restartStatus);
    root.appendChild(restartCard);

    // === 卡片 5: .env 配置编辑器 ===
    const configCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '⚙ 环境变量配置 (.env)' }),
      el('p', { class: 'hint', text: '直接修改系统环境变量（仅管理员可操作）。修改后点击保存，并使用上方按钮重启服务器。' })
    ]);
    const configStatus = el('div', { class: 'progress-status', text: '加载中...' });
    const configGroups = el('div');
    const configSaveBtn = el('button', { class: 'neo-btn primary', text: '💾 保存配置', style: { marginTop: '14px' } });
    const configResetBtn = el('button', { class: 'neo-btn', text: '恢复默认值', style: { marginLeft: '10px' } });
    configCard.appendChild(configStatus);
    configCard.appendChild(configGroups);
    const configBtnRow = el('div');
    configBtnRow.appendChild(configSaveBtn);
    configBtnRow.appendChild(configResetBtn);
    configCard.appendChild(configBtnRow);
    root.appendChild(configCard);

    let adminConfigData = null;
    async function reloadConfig() {
      configStatus.textContent = '加载中...';
      try {
        const d = await QSDB.getAdminConfig();
        if (d && d.success) {
          adminConfigData = d;
          configGroups.innerHTML = '';
          configStatus.textContent = '✅ 已加载配置 — ' + d.env_path;
          const groupKeys = Object.keys(d.groups || {});
          groupKeys.forEach(gk => {
            const group = el('div', { style: { marginBottom: '22px', padding: '16px', border: '3px solid #000', background: '#f8fafc' } });
            group.appendChild(el('h4', { text: d.group_labels[gk] || gk, style: { margin: '0 0 12px 0', fontSize: '14px' } }));
            (d.groups[gk] || []).forEach(f => {
              const row = el('div', { style: { display: 'flex', alignItems: 'center', marginBottom: '8px', gap: '10px' } });
              const label = el('label', { style: { flex: '0 0 280px', fontWeight: '700', fontSize: '13px' }, text: f.key });
              const desc = el('span', { class: 'muted-text', style: { fontSize: '12px', display: 'block', marginLeft: '280px', color: '#64748b' }, text: f.label + (f.min != null ? ` (min: ${f.min}, max: ${f.max})` : '') });
              let input;
              if (f.type === 'boolean') {
                input = el('select', { 'data-key': f.key, class: 'filter-select', style: { flex: '1' } });
                input.appendChild(el('option', { value: 'true', text: 'true (启用)' }));
                input.appendChild(el('option', { value: 'false', text: 'false (禁用)' }));
                input.value = String(f.value === true);
              } else if (f.type === 'number') {
                input = el('input', { type: 'number', 'data-key': f.key, value: String(f.value), class: 'filter-input', style: { flex: '1', padding: '8px 10px', border: '2px solid #000', borderRadius: '4px', fontSize: '13px' } });
                if (f.min != null) input.min = String(f.min);
                if (f.max != null) input.max = String(f.max);
              } else {
                input = el('input', { type: 'text', 'data-key': f.key, value: String(f.value), class: 'filter-input', style: { flex: '1', padding: '8px 10px', border: '2px solid #000', borderRadius: '4px', fontSize: '13px' } });
              }
              if (f.in_file === false) {
                const tag = el('span', { text: 'NEW', style: { marginLeft: '8px', padding: '2px 6px', fontSize: '10px', color: '#fff', background: '#d97706', borderRadius: '3px' } });
                row.appendChild(label);
                row.appendChild(tag);
                row.appendChild(input);
              } else {
                row.appendChild(label);
                row.appendChild(input);
              }
              const wrap = el('div');
              wrap.appendChild(row);
              wrap.appendChild(desc);
              group.appendChild(wrap);
            });
            configGroups.appendChild(group);
          });
        } else {
          configStatus.textContent = '❌ ' + ((d && d.error) || '加载失败');
        }
      } catch (e) { configStatus.textContent = '❌ 加载失败: ' + e.message; }
    }

    configSaveBtn.addEventListener('click', () => {
      if (!adminConfigData) return;
      const inputs = configGroups.querySelectorAll('[data-key]');
      const updates = {};
      for (let i = 0; i < inputs.length; i++) {
        const key = inputs[i].getAttribute('data-key');
        updates[key] = inputs[i].value;
      }
      Modal.confirm({
        title: '确认保存配置',
        body: '<div>即将保存 ' + Object.keys(updates).length + ' 个配置项。<br><b class="warn-text">请在保存后重启服务器</b>才能生效。</div>',
        confirmText: '确认保存',
        onConfirm: async () => {
          try {
            configSaveBtn.disabled = true;
            configStatus.textContent = '保存中...';
            const r = await QSDB.setAdminConfig(updates);
            if (r && r.success) {
              configStatus.textContent = '✅ 保存成功，已更新 ' + r.updated + ' 个字段；请使用上方重启按钮使配置生效。';
              Toast.show('配置已保存，请重启服务器', 'success');
              reloadConfig();
            } else {
              configStatus.textContent = '❌ ' + ((r && r.error) || '保存失败');
            }
          } catch (e) { configStatus.textContent = '❌ ' + e.message; }
          finally { configSaveBtn.disabled = false; }
        }
      });
    });

    configResetBtn.addEventListener('click', () => {
      Modal.confirm({
        title: '恢复默认值',
        body: '<div>将从后端重新拉取当前有效配置（不会重置文件，仅刷新此表单视图）。</div>',
        confirmText: '重新加载',
        onConfirm: reloadConfig
      });
    });

    reloadConfig();

    customCountInput.addEventListener('input', () => {
      const v = parseInt(customCountInput.value, 10);
      if (!isNaN(v) && v > 0) countSelect.value = 'custom';
    });
    countSelect.addEventListener('change', () => {
      if (countSelect.value !== 'custom') customCountInput.value = '';
    });

    function updateSeedRunning(isRunning) {
      state.seedRunning = !!isRunning;
      seedBtn.disabled = isRunning;
      seedBtn.style.opacity = isRunning ? '0.5' : '';
      seedBtn.textContent = isRunning ? '生成中...' : '开始生成';
      terminateBtn.style.display = isRunning ? '' : 'none';
      terminateBtn.disabled = !isRunning;
      progressTrack.style.opacity = isRunning ? '1' : '0.3';
      if (!isRunning && state.seedPoller) {
        clearInterval(state.seedPoller);
        state.seedPoller = null;
      }
    }

    // 开始生成
    seedBtn.addEventListener('click', async () => {
      let count;
      const customVal = parseInt(customCountInput.value, 10);
      if (!isNaN(customVal) && customVal > 0) count = customVal;
      else count = parseInt(countSelect.value, 10);
      if (!count || count < 1) { Toast.show('请先选择或输入要生成的数量', 'warning'); return; }
      if (count > 5000000) { Toast.show('单次最大 5,000,000 条', 'warning'); return; }
      const mode = modeSelect.value;

      // 启动进度轮询
      let totalCount = count;
      updateSeedRunning(true);
      statusLine.textContent = '启动中...';
      progressFill.style.width = '0%';

      // 启动 progress poller
      state.seedPoller = setInterval(async () => {
        try {
          const info = await QSDB.seedProgress();
          if (info && typeof info.inserted === 'number') {
            const pct = Math.min(100, Math.floor((info.inserted / totalCount) * 100));
            progressFill.style.width = pct + '%';
            statusLine.textContent = '已生成 ' + (info.inserted || 0).toLocaleString() + ' / ' + totalCount.toLocaleString() + ' 条 (' + pct + '%)';
            if (info.running === false && pct > 0) {
              // 后端完成
            }
          }
        } catch (e) {}
      }, 1000);

      try {
        const r = await QSDB.seed(count, mode);
        clearInterval(state.seedPoller);
        state.seedPoller = null;

        if (r && r.cancelled) {
          progressFill.style.width = '100%';
          statusLine.textContent = '任务已取消，已生成 ' + ((r.inserted || 0)).toLocaleString() + ' 条数据';
          Toast.show('任务已取消', 'warning');
        } else if (r && (r.success || r.inserted !== undefined)) {
          progressFill.style.width = '100%';
          statusLine.textContent = '✅ 完成: 已生成 ' + ((r.inserted || count) || 0).toLocaleString() + ' 条数据, 模式: ' + (r.mode || mode);
          Toast.show('已生成 ' + ((r.inserted || count) || 0).toLocaleString() + ' 条数据', 'success');
        } else {
          statusLine.textContent = '❌ ' + ((r && r.error) || '生成失败');
          Toast.show('生成失败: ' + ((r && r.error) || '未知错误'), 'error');
        }
      } catch (err) {
        clearInterval(state.seedPoller);
        state.seedPoller = null;
        statusLine.textContent = '❌ 失败: ' + err.message;
        Toast.show('生成失败: ' + err.message, 'error');
      } finally {
        updateSeedRunning(false);
      }
    });

    // 强制终止
    terminateBtn.addEventListener('click', () => {
      const body = el('div', null);
      body.innerHTML = '<p><b class="warn-text">确定要强制终止当前批量任务并清空所有相关数据吗？</b>此操作不可恢复。</p><ul><li>将立即停止正在进行的数据生成任务</li><li>将清空所有以 seed_ 为前缀的相关数据</li><li>将刷新本地缓存</li></ul><div class="muted-text">点击「确定」开始执行，点击「取消」或按 ESC 关闭对话框。</div>';

      Modal.confirm({
        title: '确认强制终止任务',
        danger: true,
        body: body,
        confirmText: '确定强制终止',
        cancelText: '取消',
        onConfirm: async (close) => {
          // 进入处理状态
          const m = Modal.show({
            title: '正在执行终止操作...',
            danger: true,
            body: '<div class="warn-text">终止中，正在清理相关数据，请稍候...</div>',
            confirmText: '完成',
            showCancel: false,
            onConfirm: () => true
          });

          try {
            const result = await QSDB.terminateSeed();
            m.close();

            if (result.success) {
              progressFill.style.width = '0%';
              progressTrack.style.opacity = '0';
              statusLine.textContent = '🛑 任务已强制终止, 已清理 ' + ((result.deleted || 0)).toLocaleString() + ' 条 seed 数据' + (result.elapsed_ms ? '（耗时 ' + (result.elapsed_ms / 1000).toFixed(2) + 's）' : '');
              Toast.show(result.message || '批量任务已成功终止，相关数据已清空', 'success');
              updateSeedRunning(false);
              // 刷新统计
              try {
                const s = await QSDB.getStats();
                if (s && $('#stat-shops .val')) {
                  $('#stat-shops .val').textContent = s.total_shops || 0;
                  $('#stat-materials .val').textContent = s.total_materials || 0;
                }
              } catch (e) {}
            } else {
              Toast.show('终止失败: ' + (result.error || '未知错误'), 'error');
              statusLine.textContent = '❌ 终止失败: ' + (result.error || '未知错误');
            }
          } catch (err) {
            m.close();
            Toast.show('终止失败: ' + err.message, 'error');
            statusLine.textContent = '❌ 终止失败: ' + err.message;
          }
        }
      });
    });

    // 初始状态
    updateSeedRunning(false);
  }

  //  导入 / 导出
  async function initDataIO() {
    const root = ensureTabPanel();
    if (!root) return;
    root.appendChild(el('h2', { style: { fontSize: '20px', marginBottom: '16px' }, text: '📦 数据导入 / 导出' }));

    // 导出
    const exportCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '导出数据' }),
      el('p', { class: 'hint', text: '商店数据可导出为 JSON / CSV；交易记录、全部玩家余额、被移除的商店记录均可导出为 CSV，用于统计分析和审计。' })
    ]);
    const exportBtn = el('button', { class: 'neo-btn primary', text: '导出为 JSON',
      onclick: async () => {
        try {
          const data = await QSDB.exportAll();
          const json = JSON.stringify(data, null, 2);
          const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'qshop_export_' + new Date().toISOString().slice(0, 10) + '.json';
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 2000);
          Toast.show('导出成功', 'success');
        } catch (err) { Toast.show('导出失败: ' + err.message, 'error'); }
      }
    });
    const exportCsvBtn = el('button', { class: 'neo-btn', text: '导出为 CSV',
      onclick: async () => {
        try {
          const data = await QSDB.exportAll();
          const shops = (data && Array.isArray(data.shops)) ? data.shops : [];
          const headers = ['id', 'shop_type', 'owner_name', 'material', 'item_name', 'price', 'stacking_amount', 'world', 'x', 'y', 'z'];
          const esc = (v) => { const s = v === null || v === undefined ? '' : String(v); if (s.indexOf(',') > -1 || s.indexOf('"') > -1) return '"' + s.replace(/"/g, '""') + '"'; return s; };
          const lines = [headers.join(',')];
          shops.forEach((s) => lines.push(headers.map((h) => esc(s[h])).join(',')));
          const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'qshop_export_' + new Date().toISOString().slice(0, 10) + '.csv';
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 2000);
          Toast.show('CSV 导出成功 (' + shops.length + ' 行)', 'success');
        } catch (err) { Toast.show('导出失败: ' + err.message, 'error'); }
      }
    });
    const btnRow1 = el('div', { style: { display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '8px' } });
    btnRow1.appendChild(exportBtn); btnRow1.appendChild(exportCsvBtn);
    const exportTradesCsvBtn = el('button', { class: 'neo-btn', text: '导出交易记录 CSV',
      onclick: function () { exportTradesCsv(); }
    });
    const importHistoryBtn2 = el('button', { class: 'neo-btn', text: '📥 导入 QuickShop 历史交易',
      onclick: function () { importQsHistory(); }
    });
    const exportBalancesBtn = el('button', { class: 'neo-btn', text: '导出玩家余额 CSV',
      onclick: function () { exportBalancesCsv(); }
    });
    const exportRemovalsBtn = el('button', { class: 'neo-btn', text: '导出移除商店记录 CSV',
      onclick: function () { exportShopRemovalsCsv(); }
    });
    btnRow1.appendChild(exportTradesCsvBtn);
    btnRow1.appendChild(importHistoryBtn2);
    btnRow1.appendChild(exportBalancesBtn);
    btnRow1.appendChild(exportRemovalsBtn);
    exportCard.appendChild(btnRow1);
    root.appendChild(exportCard);

    // 导入
    const importCard = el('div', { class: 'admin-card' }, [
      el('h3', { text: '导入数据' }),
      el('p', { class: 'hint', text: '支持 JSON / CSV 格式，文件大小建议 < 50MB。导入的数据将被整合到现有数据库中。' })
    ]);
    const fileInput = el('input', { type: 'file', accept: '.json,.csv', style: { marginBottom: '12px' } });
    const importBtn = el('button', { class: 'neo-btn primary', text: '开始导入',
      onclick: async () => {
        const file = fileInput.files && fileInput.files[0];
        if (!file) { Toast.show('请先选择一个文件', 'warning'); return; }
        try {
          const text = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(new Error('读取失败'));
            reader.readAsText(file);
          });
          let shops = [];
          if (file.name.toLowerCase().endsWith('.csv')) {
            const rows = text.split(/\r?\n/).filter((x) => x.trim());
            if (rows.length < 2) throw new Error('CSV 文件为空或格式错误');
            const parseCsv = (line) => {
              const out = []; let cur = ''; let inQ = false;
              for (let i = 0; i < line.length; i++) {
                const c = line[i];
                if (inQ) { if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') inQ = false; else cur += c; }
                else { if (c === ',') { out.push(cur); cur = ''; } else if (c === '"') inQ = true; else cur += c; }
              }
              out.push(cur); return out;
            };
            const headers = parseCsv(rows[0]).map((h) => h.trim());
            for (let i = 1; i < rows.length; i++) {
              const vals = parseCsv(rows[i]);
              const obj = {};
              headers.forEach((h, idx) => { obj[h] = vals[idx]; });
              if (obj.price !== undefined) obj.price = parseFloat(obj.price);
              if (obj.stacking_amount !== undefined) obj.stacking_amount = parseInt(obj.stacking_amount, 10);
              shops.push(obj);
            }
          } else {
            const json = JSON.parse(text);
            shops = Array.isArray(json) ? json : (json && Array.isArray(json.shops)) ? json.shops : [];
          }
          if (!shops.length) throw new Error('没有可导入的数据');

          const m = Modal.show({
            title: '导入中...',
            danger: false,
            body: '<div>正在导入 ' + shops.length + ' 条数据，请稍候...</div>',
            confirmText: '完成', showCancel: false, onConfirm: () => true
          });

          const r = await QSDB.importAll(shops);
          m.close();
          if (r && (r.added !== undefined || r.total !== undefined)) {
            Toast.show('导入完成: 新增 ' + (r.added || 0) + ' / 更新 ' + (r.updated || 0), 'success');
          } else {
            Toast.show('导入完成', 'success');
          }
        } catch (err) { Toast.show('导入失败: ' + err.message, 'error'); }
      }
    });
    importCard.appendChild(fileInput);
    const btnRow2 = el('div');
    btnRow2.appendChild(importBtn);
    importCard.appendChild(btnRow2);
    root.appendChild(importCard);
  }

  //  工具函数
  function debounce(fn, ms) {
    let t = null;
    return function () {
      const ctx = this, args = arguments;
      clearTimeout(t);
      t = setTimeout(() => fn.apply(ctx, args), ms);
    };
  }

  //  初始化
  document.addEventListener('DOMContentLoaded', async () => {
    // —— 第 1 步：同步身份验证（与资源预加载并行, 缩短总耗时）——
    const authPromise = (async () => {
      try {
        if (typeof QSDB.checkAuth === 'function') {
          const info = await QSDB.checkAuth();
          state.isLoggedIn = !!(info && (info.isAdmin || info.authenticated));
          state.isAdmin = !!(info && info.isAdmin);
          state.username = info && info.username;
          state.role = info && info.role || (state.isAdmin ? 'admin' : 'user');
          // 玩家会话（AuthMe）：恢复玩家身份并查余额
          if (info && info.role === 'player' && info.username) {
            state.playerName = info.username;
            state.isLoggedIn = false;
            state.isAdmin = false;
            QSDB.getWallet('').then(function (w) {
              if (w && w.success) { state.playerBalance = w.balance; TopNav.render(); }
            }).catch(function () { });
          }
        } else if (typeof QSDB.isLoggedIn === 'function' && QSDB.isLoggedIn()) {
          state.isLoggedIn = true;
          state.isAdmin = QSDB.isAdmin();
        }
      } catch (e) {}
    })();

    // —— 第 2 步：启动加载动画（与身份验证并行）——
    const loaderPromise = Loader.start();

    // —— 第 3 步：等待两者完成，然后渲染主内容 ——
    try {
      await Promise.all([authPromise, loaderPromise]);
    } catch (e) {
      // 即使 Loader 出错也不阻止页面渲染
      console.warn('Init error:', e);
    }

    TopNav.render();
    switchTab('items');

    // 服务器名称（config.yml server-name）→ 更新标题/导航栏/首页
    QSDB.getHealth().then(function (h) {
      if (h && h.success) {
        if (h.server_name) siteServerName = String(h.server_name);
        if (h.server_subtitle) siteServerSubtitle = String(h.server_subtitle);
        applySiteBrand();
      }
    }).catch(function () { });

    // 页面可见性（config.yml pages.hide-* / 后台设置）
    QSDB.getPages().then(function (r) {
      if (r && r.success && r.hidden_pages) {
        hiddenPages = r.hidden_pages;
        applyPageVisibility();
      }
    }).catch(function () { });
  });

  //  信息统计 (initStatsPage) - 简化版, 无 24h 分布图
  function initStatsPage() {
    const root = ensureTabPanel('stats');
    if (!root) return;

    root.innerHTML = '';

    // 标题
    const header = el('div', { class: 'page-header' }, [
      el('h2', { class: 'page-title', text: '信息统计' }),
      el('div', { class: 'page-subtitle', text: '系统整体数据与 QSFilter 连接状态' })
    ]);
    root.appendChild(header);

    // 核心统计卡片区
    const statCards = el('div', { class: 'stats-row' });
    const cardConfigs = [
      { key: 'total_shops', label: '商店总数' },
      { key: 'unique_materials', label: '物品种类' },
      { key: 'unique_owners', label: '店主数量' },
      { key: 'total_today', label: '今日请求' },
      { key: 'selling_count', label: '出售商店' },
      { key: 'buying_count', label: '收购商店' }
    ];
    cardConfigs.forEach(c => {
      const card = el('div', { class: 'stat-card', id: 'statcard-' + c.key }, [
        el('div', { class: 'val', id: 'statval-' + c.key, text: '...' }),
        el('div', { class: 'lbl', text: c.label })
      ]);
      statCards.appendChild(card);
    });
    root.appendChild(statCards);

    // QSFilter 状态面板
    const qsSection = el('div', { class: 'chart-section' });
    qsSection.appendChild(el('h3', { class: 'section-title', text: 'QSFilter 连接状态' }));
    const qsInfo = el('div', { class: 'qs-info-panel', id: 'qs-info-panel', text: '加载中...' });
    qsSection.appendChild(qsInfo);
    root.appendChild(qsSection);

    observeScrollAnimations(statCards);

    // 调用 API 获取数据
    (async () => {
      try {
        const data = await QSDB.getStatsRequests();
        // 更新卡片
        if (data.cache) {
          updateStat('total_shops', data.cache.total_shops);
          updateStat('unique_materials', data.cache.unique_materials);
          updateStat('unique_owners', data.cache.unique_owners);
          updateStat('selling_count', data.cache.selling_shops);
          updateStat('buying_count', data.cache.buying_shops);
        }
        updateStat('total_today', data.total_today || 0);

        // QSFilter 信息面板
        if (data.qsfilter) {
          const qf = data.qsfilter;
          qsInfo.innerHTML = `
            <div class="info-row">
              <span class="info-label">连接状态:</span>
              <span class="info-value qs-badge ${qf.connected ? 'success' : 'error'}">${qf.connected ? '已连接' : '未连接'}</span>
            </div>
            <div class="info-row">
              <span class="info-label">轮询次数:</span>
              <span class="info-value">${qf.polling_count || 0}</span>
            </div>
            <div class="info-row">
              <span class="info-label">WebHook 事件:</span>
              <span class="info-value">${qf.webhook_event_count || 0}</span>
            </div>
            <div class="info-row">
              <span class="info-label">最近轮询:</span>
              <span class="info-value">${qf.polling_last_at ? new Date(qf.polling_last_at).toLocaleString() : '无'}</span>
            </div>
          `;
        }
      } catch (e) {
        Toast.show('获取统计数据失败: ' + e.message, 'error');
      }
    })();
  }

  function updateStat(key, value) {
    const v = document.getElementById('statval-' + key);
    if (v) v.textContent = value !== undefined && value !== null ? value : '-';
  }

  //  同步监控页面 (initMonitorPage)
  function initMonitorPage() {
    const root = ensureTabPanel('monitor');
    if (!root) return;

    root.innerHTML = '';

    const header = el('div', { class: 'page-header' }, [
      el('h2', { class: 'page-title', text: '数据同步监控' }),
      el('div', { class: 'page-subtitle', text: '监控 QSFilter 连接状态、同步历史和事件日志' })
    ]);
    root.appendChild(header);

    // 状态卡片区
    const statCards = el('div', { class: 'stats-row' });
    const cards = [
      { key: 'connected', label: 'QSFilter 连接', value: '检测中...' },
      { key: 'last_sync', label: '最近同步', value: '...' },
      { key: 'shop_count', label: '当前商店数', value: '...' },
      { key: 'webhook_events', label: 'WebHook 事件', value: '...' }
    ];
    cards.forEach(c => {
      const card = el('div', { class: 'stat-card', id: 'moncard-' + c.key }, [
        el('div', { class: 'val', id: 'monval-' + c.key, text: c.value }),
        el('div', { class: 'lbl', text: c.label })
      ]);
      statCards.appendChild(card);
    });
    root.appendChild(statCards);

    // 手动同步按钮
    const actionRow = el('div', { class: 'action-buttons' });
    const syncBtn = el('button', {
      class: 'neo-btn primary',
      text: '立即手动同步',
      onclick: async () => {
        try {
          syncBtn.textContent = '同步中...';
          syncBtn.disabled = true;
          const result = await QSDB.triggerSyncNow();
          Toast.show('同步完成: ' + (result.shops_count || result.shops_count) + ' 家商店', 'success');
          loadSyncStatus();
        } catch (e) {
          Toast.show('同步失败: ' + e.message, 'error');
        } finally {
          syncBtn.textContent = '立即手动同步';
          syncBtn.disabled = false;
        }
      }
    });
    actionRow.appendChild(syncBtn);
    root.appendChild(actionRow);

    // 同步历史
    const historySection = el('div', { class: 'chart-section' });
    historySection.appendChild(el('h3', { class: 'section-title', text: '最近同步历史' }));
    const historyTable = el('table', { class: 'data-table', id: 'sync-history-table' });
    historySection.appendChild(historyTable);
    root.appendChild(historySection);

    // 下一次同步时间
    const scheduleSection = el('div', { class: 'chart-section' });
    scheduleSection.appendChild(el('h3', { class: 'section-title', text: '调度配置' }));
    const scheduleInfo = el('div', { class: 'qs-info-panel', id: 'schedule-info' });
    scheduleSection.appendChild(scheduleInfo);
    root.appendChild(scheduleSection);

    // 加载数据
    loadSyncStatus();

    // 自动刷新: 每 15 秒刷新一次
    if (window._monitorTimer) clearInterval(window._monitorTimer);
    window._monitorTimer = setInterval(loadSyncStatus, 15000);
  }

  async function loadSyncStatus() {
    try {
      const data = await QSDB.getSyncStatus();
      const connected = document.getElementById('monval-connected');
      if (connected) connected.textContent = data.cache && data.cache.total_shops > 0 ? '正常' : '等待数据';
      const lastSync = document.getElementById('monval-last_sync');
      if (lastSync) lastSync.textContent = data.last_full_sync_at ? new Date(data.last_full_sync_at).toLocaleTimeString() : '尚未同步';
      const shopCount = document.getElementById('monval-shop_count');
      if (shopCount) shopCount.textContent = data.cache ? data.cache.total_shops : '-';
      const whCount = document.getElementById('monval-webhook_events');
      if (whCount) whCount.textContent = (data.webhook && data.webhook.event_count) ? data.webhook.event_count : 0;

      // 同步历史表
      const tbl = document.getElementById('sync-history-table');
      if (tbl) {
        let html = '<thead><tr><th>时间</th><th>来源</th><th>结果</th><th>商店数</th><th>耗时 (ms)</th><th>错误</th></tr></thead><tbody>';
        const history = data.history || [];
        if (history.length === 0) {
          html += '<tr><td colspan="6" style="text-align:center;padding:20px;color:#888;">暂无同步记录</td></tr>';
        } else {
          history.forEach(h => {
            const dateStr = h.time ? new Date(h.time).toLocaleString() : '-';
            html += `<tr class="${h.success ? '' : 'row-error'}">
              <td>${dateStr}</td>
              <td>${h.source || '-'}</td>
              <td>${h.success ? '<span class="ok-tag">成功</span>' : '<span class="err-tag">失败</span>'}</td>
              <td>${h.shop_count || 0}</td>
              <td>${h.duration_ms || 0}</td>
              <td>${h.error || '-'}</td>
            </tr>`;
          });
        }
        html += '</tbody>';
        tbl.innerHTML = html;
      }

      // 调度信息
      const sch = document.getElementById('schedule-info');
      if (sch) {
        const times = Array.isArray(data.sync_schedule_times) ? data.sync_schedule_times.join(', ') : '-';
        sch.innerHTML = `
          <div class="info-row"><span class="info-label">每日定时同步时间:</span><span class="info-value">${times}</span></div>
          <div class="info-row"><span class="info-label">最大重试次数:</span><span class="info-value">${data.max_retries || '-'}</span></div>
          <div class="info-row"><span class="info-label">WebHook 启用:</span><span class="info-value">${data.webhook && data.webhook.enabled ? '是' : '否'}</span></div>
          <div class="info-row"><span class="info-label">WebHook 最近事件时间:</span><span class="info-value">${data.webhook && data.webhook.last_at ? new Date(data.webhook.last_at).toLocaleString() : '无'}</span></div>
          <div class="info-row"><span class="info-label">缓存总数:</span><span class="info-value">${data.cache ? data.cache.total_shops : 0}</span></div>
        `;
      }
    } catch (e) {
      Toast.show('获取同步状态失败: ' + e.message, 'error');
    }
  }

  //  备份恢复管理页面 (initBackupPage)
  function initBackupPage() {
    const root = ensureTabPanel('backup');
    if (!root) return;

    root.innerHTML = '';

    const header = el('div', { class: 'page-header' }, [
      el('h2', { class: 'page-title', text: '数据备份与恢复' }),
      el('div', { class: 'page-subtitle', text: '管理员功能: 创建备份、查看备份列表、恢复数据、清理过期备份' })
    ]);
    root.appendChild(header);

    // 状态卡片区
    const statCards = el('div', { class: 'stats-row' });
    statCards.appendChild(el('div', { class: 'stat-card', id: 'bk-status-card' }, [
      el('div', { class: 'val', id: 'bk-status-val', text: '...' }),
      el('div', { class: 'lbl', text: '备份启用状态' })
    ]));
    statCards.appendChild(el('div', { class: 'stat-card', id: 'bk-dir-card' }, [
      el('div', { class: 'val', id: 'bk-dir-val', text: '...' }),
      el('div', { class: 'lbl', text: '备份目录' })
    ]));
    statCards.appendChild(el('div', { class: 'stat-card', id: 'bk-time-card' }, [
      el('div', { class: 'val', id: 'bk-time-val', text: '...' }),
      el('div', { class: 'lbl', text: '每日备份时间' })
    ]));
    statCards.appendChild(el('div', { class: 'stat-card', id: 'bk-last-card' }, [
      el('div', { class: 'val', id: 'bk-last-val', text: '...' }),
      el('div', { class: 'lbl', text: '最近备份' })
    ]));
    root.appendChild(statCards);

    // 操作按钮
    const actionRow = el('div', { class: 'action-buttons' });
    const createBtn = el('button', { class: 'neo-btn primary', text: '立即创建备份', onclick: onBackupCreate });
    const refreshBtn = el('button', { class: 'neo-btn', text: '刷新列表', onclick: loadBackupList });
    const cleanupBtn = el('button', { class: 'neo-btn warning', text: '清理过期备份', onclick: onBackupCleanup });
    actionRow.appendChild(createBtn);
    actionRow.appendChild(refreshBtn);
    actionRow.appendChild(cleanupBtn);
    root.appendChild(actionRow);

    // 备份列表
    const listSection = el('div', { class: 'chart-section' });
    listSection.appendChild(el('h3', { class: 'section-title', text: '备份文件列表' }));
    const backupTable = el('table', { class: 'data-table', id: 'backup-table' });
    listSection.appendChild(backupTable);
    root.appendChild(listSection);

    // 先加载数据
    loadBackupList();
  }

  async function onBackupCreate() {
    try {
      const result = await QSDB.createBackup();
      Toast.show('备份成功: ' + (result.file || result.path || '已创建'), 'success');
      loadBackupList();
    } catch (e) {
      Toast.show('备份失败: ' + e.message, 'error');
    }
  }

  async function onBackupCleanup() {
    try {
      const ok = await new Promise(resolve => {
        Modal.confirm({
          title: '确认清理',
          body: '<div>确定要清理所有过期的备份文件吗？</div><div class="muted-text">将根据系统配置的保留策略保留最近备份</div>',
          confirmText: '确定清理',
          cancelText: '取消',
          onConfirm: () => { resolve(true); return true; },
          onCancel: () => { resolve(false); return true; }
        });
      });
      if (!ok) return;
      const result = await QSDB.cleanupBackups();
      Toast.show('清理完成: 已删除 ' + ((result && result.deleted) || 0) + ' 个备份', 'success');
      loadBackupList();
    } catch (e) {
      Toast.show('清理失败: ' + e.message, 'error');
    }
  }

  async function onBackupRestore(fileName) {
    try {
      const ok = await new Promise(resolve => {
        Modal.confirm({
          title: '⚠️ 确认恢复备份',
          danger: true,
          body: '<div>确定要从备份文件 <b>' + fileName + '</b> 恢复数据吗？</div><div class="muted-text">此操作将覆盖当前的商店数据和数据库记录。建议先手动创建一个备份。</div>',
          confirmText: '确定恢复',
          cancelText: '取消',
          onConfirm: () => { resolve(true); return true; },
          onCancel: () => { resolve(false); return true; }
        });
      });
      if (!ok) return;
      const result = await QSDB.restoreBackup(fileName);
      Toast.show('恢复完成: ' + ((result && (result.file || fileName)) || fileName), 'success');
      loadBackupList();
    } catch (e) {
      Toast.show('恢复失败: ' + e.message, 'error');
    }
  }

  async function onBackupDelete(fileName) {
    try {
      const ok = await new Promise(resolve => {
        Modal.confirm({
          title: '确认删除',
          danger: true,
          body: '<div>确定要删除备份文件 <b>' + fileName + '</b> 吗？</div><div class="muted-text">此操作不可恢复</div>',
          confirmText: '确定删除',
          cancelText: '取消',
          onConfirm: () => { resolve(true); return true; },
          onCancel: () => { resolve(false); return true; }
        });
      });
      if (!ok) return;
      await QSDB.deleteBackup(fileName);
      Toast.show('备份已删除', 'success');
      loadBackupList();
    } catch (e) {
      Toast.show('删除失败: ' + e.message, 'error');
    }
  }

  async function loadBackupList() {
    try {
      // 加载状态
      const statusData = await QSDB.getBackupStatus();
      if (statusData && statusData.config) {
        const cfg = statusData.config;
        const v1 = document.getElementById('bk-status-val');
        if (v1) v1.textContent = cfg.enabled ? '已启用' : '已停用';
        const v2 = document.getElementById('bk-dir-val');
        if (v2) v2.textContent = cfg.backup_dir ? cfg.backup_dir.substring(0, 30) + (cfg.backup_dir.length > 30 ? '...' : '') : '-';
        const v3 = document.getElementById('bk-time-val');
        if (v3) v3.textContent = cfg.backup_time || cfg.BACKUP_TIME || '-';
      }
      if (statusData && statusData.status && statusData.status.last_backup_at) {
        const v4 = document.getElementById('bk-last-val');
        if (v4) v4.textContent = new Date(statusData.status.last_backup_at).toLocaleString();
      }

      // 加载备份列表
      const listData = await QSDB.listBackups();
      const tbl = document.getElementById('backup-table');
      if (tbl) {
        let html = '<thead><tr><th>文件名</th><th>大小</th><th>创建时间</th><th>类型</th><th>商店数</th><th>操作</th></tr></thead><tbody>';
        const files = listData.files || [];
        if (files.length === 0) {
          html += '<tr><td colspan="6" style="text-align:center;padding:20px;color:#888;">暂无备份文件</td></tr>';
        } else {
          files.forEach(f => {
            const sizeStr = f.size_bytes !== undefined ? ((f.size_bytes / 1024).toFixed(1) + ' KB') : '-';
            const timeStr = f.created_at_display || (f.created_at ? new Date(f.created_at).toLocaleString() : '-');
            html += `<tr>
              <td><strong>${f.file_name || '-'}</strong></td>
              <td>${sizeStr}</td>
              <td>${timeStr}</td>
              <td>${f.backup_type || '-'}</td>
              <td>${f.shop_count !== undefined ? f.shop_count : '-'}</td>
              <td class="action-cell">
                <button class="neo-btn small primary" onclick="QS_APP.restoreBackup('${f.file_name}')">恢复</button>
                <button class="neo-btn small danger" onclick="QS_APP.deleteBackup('${f.file_name}')">删除</button>
              </td>
            </tr>`;
          });
        }
        html += '</tbody>';
        tbl.innerHTML = html;
      }
    } catch (e) {
      Toast.show('加载备份列表失败: ' + e.message, 'error');
    }
  }

  // 暴露到全局
  global.QS_APP = {
    switchTab: switchTab,
    showToast: (msg, type) => Toast.show(msg, type),
    showConfirm: (opts) => Modal.confirm(opts),
    getState: () => state,
    restoreBackup: (fileName) => onBackupRestore(fileName),
    deleteBackup: (fileName) => onBackupDelete(fileName)
  };
})(typeof window !== 'undefined' ? window : this);
