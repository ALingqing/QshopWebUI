
(function (global) {
  'use strict';

  const API_BASE = '/api';

  const memoryCache = {
    allShops: null,
    lastUpdate: 0
  };

  // 对 GET 请求的响应做 30 秒缓存，大幅减少网络往返和页面切换卡顿
  const apiTTL = 30 * 1000; // 30 秒
  const apiCache = new Map(); // key -> { data, timestamp }
  const apiCacheMax = 60;

  function cacheGet(path) {
    const entry = apiCache.get(path);
    if (!entry) return null;
    if (Date.now() - entry.timestamp > apiTTL) {
      apiCache.delete(path);
      return null;
    }
    return entry.data;
  }
  function cachePut(path, data) {
    if (apiCache.size >= apiCacheMax) {
      // FIFO 清理
      const firstKey = apiCache.keys().next().value;
      apiCache.delete(firstKey);
    }
    apiCache.set(path, { data: data, timestamp: Date.now() });
  }
  function cacheClear() { apiCache.clear(); }

  const authState = {
    sessionId: null,
    username: null,
    role: 'guest',
    expiresAt: 0
  };

  function httpErrorText(status) {
    const map = {
      400: '请求格式错误',
      401: '需要认证',
      403: '权限不足',
      404: '资源不存在',
      429: '请求过于频繁',
      500: '服务器内部错误',
      503: 'PostgreSQL 不可用'
    };
    return map[status] || 'HTTP ' + status;
  }

  function restoreLocalSession() {
    try {
      const saved = (typeof localStorage !== 'undefined') ? localStorage.getItem('qsdb_session') : null;
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed && parsed.sessionId && (!parsed.expiresAt || parsed.expiresAt > Date.now())) {
          authState.sessionId = parsed.sessionId;
          authState.username = parsed.username || null;
          authState.role = parsed.role || 'user';
          authState.expiresAt = parsed.expiresAt || 0;
        }
      }
    } catch (e) {}
  }

  function saveLocalSession() {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('qsdb_session', JSON.stringify({
          sessionId: authState.sessionId,
          username: authState.username,
          role: authState.role,
          expiresAt: authState.expiresAt
        }));
      }
    } catch (e) {}
  }

  function clearLocalSession() {
    try {
      if (typeof localStorage !== 'undefined') localStorage.removeItem('qsdb_session');
    } catch (e) {}
  }

  async function apiCall(path, opts) {
    opts = opts || {};
    opts.headers = opts.headers || {};
    const method = (opts.method || 'GET').toUpperCase();
    const t0 = Date.now();
    // 前端控制台日志（浏览器 devtools 可查看）
    console.log(`[${new Date().toISOString()}] → ${method} ${path}` + (opts.body ? ` body=${String(JSON.stringify(opts.body)).substring(0, 200)}` : ''));

    // 非 GET 请求：清除缓存，立即发起请求
    if (method !== 'GET') {
      cacheClear();
    } else {
      // GET 请求：命中缓存直接返回，跳过网络往返
      const cached = cacheGet(path);
      if (cached) {
        console.log(`[${new Date().toISOString()}] ← ${method} ${path} (CACHED) ${Date.now() - t0}ms`);
        // 返回一个新对象，避免调用方污染缓存
        return cached;
      }
    }

    // 确保会话状态恢复
    if (!authState.sessionId) restoreLocalSession();

    // 自动带上 session id (如已登录)
    if (authState.sessionId) {
      opts.headers['x-session'] = authState.sessionId;
    }

    try {
      const res = await fetch(API_BASE + path, {
        method: method,
        headers: Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {}),
        body: opts.body ? JSON.stringify(opts.body) : undefined
      });
      if (!res.ok) {
        let errText = httpErrorText(res.status);
        try {
          const errData = await res.json();
          if (errData && errData.error) errText = errData.error;
        } catch (e) {}
        console.warn(`[${new Date().toISOString()}] ← ${method} ${path} HTTP ${res.status} (${Date.now() - t0}ms) — ${errText}`);
        throw new Error(errText + ' (API: ' + path + ')');
      }
      const data = await res.json();
      // GET 成功：写入缓存
      if (method === 'GET') cachePut(path, data);
      console.log(`[${new Date().toISOString()}] ← ${method} ${path} HTTP ${res.status} OK (${Date.now() - t0}ms)`);
      return data;
    } catch (err) {
      console.error(`[${new Date().toISOString()}] ✗ ${method} ${path} ERROR: ${err.message}`);
      if (err instanceof Error && err.message.indexOf('API:') > -1) throw err;
      throw new Error('网络错误: 无法连接到后端服务器 (' + path + ') — ' + (err.message || ''));
    }
  }

  async function searchShops(keyword, opts) {
    opts = opts || {};
    const params = new URLSearchParams();
    if (keyword) params.append('q', keyword);
    if (opts.material) params.append('material', opts.material);
    if (opts.shop_type) params.append('shop_type', opts.shop_type);
    if (opts.owner) params.append('owner', opts.owner);
    if (opts.world) params.append('world', opts.world);
    if (opts.min_price !== undefined) params.append('min_price', opts.min_price);
    if (opts.max_price !== undefined) params.append('max_price', opts.max_price);
    if (opts.sort) params.append('sort', opts.sort);
    if (opts.page) params.append('page', opts.page);
    if (opts.pageSize) params.append('pageSize', opts.pageSize);

    try {
      const data = await apiCall('/shops?' + params.toString());
      if (!data.success) throw new Error(data.error || '查询失败');
      return {
        success: true,
        results: data.results || [],
        total: data.total || 0,
        page: data.page || 1,
        page_size: data.page_size || 12,
        total_pages: data.total_pages || 1,
        elapsed_ms: data.elapsed_ms || 0
      };
    } catch (err) {
      console.warn('商店查询失败:', err.message);
      return { success: false, results: [], total: 0, page: 1, page_size: 12, total_pages: 1, elapsed_ms: 0, error: err.message };
    }
  }

  async function getAllShops() {
    try {
      const pageSize = 5000;
      const firstPage = await apiCall('/shops?pageSize=' + pageSize + '&sort=newest');
      if (!firstPage || !firstPage.success) throw new Error(firstPage.error || '读取失败');
      let allResults = firstPage.results || [];
      const total = firstPage.total || allResults.length;

      const totalPages = Math.max(1, Math.ceil(total / pageSize));
      for (let page = 2; page <= totalPages; page++) {
        try {
          const nextPage = await apiCall('/shops?page=' + page + '&pageSize=' + pageSize + '&sort=newest');
          if (nextPage && nextPage.success && nextPage.results && nextPage.results.length > 0) {
            allResults = allResults.concat(nextPage.results);
          } else {
            break;
          }
        } catch (e) {
          console.warn('getAllShops 分页读取第 ' + page + ' 页失败:', e.message);
          break;
        }
      }

      memoryCache.allShops = allResults;
      memoryCache.lastUpdate = Date.now();
      return allResults;
    } catch (err) {
      console.warn('获取全部商店失败:', err.message);
      return [];
    }
  }

  async function getShopById(id) {
    try {
      const data = await apiCall('/shops/' + encodeURIComponent(id));
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.shop;
    } catch (err) {
      console.warn('获取商店详情失败:', err.message);
      return null;
    }
  }

  async function getShopsByMaterial() {
    try {
      const data = await apiCall('/materials');
      if (!data.success) throw new Error(data.error || '读取失败');
      const result = {};
      for (const m of data.materials || []) result[m.material] = [];
      try {
        const all = await searchShops('', { pageSize: 10000 });
        for (const s of all.results || []) {
          if (!result[s.material]) result[s.material] = [];
          result[s.material].push(s);
        }
      } catch (e) {}
      return result;
    } catch (err) {
      console.warn('获取物品分类失败:', err.message);
      return {};
    }
  }

  async function getMaterials() {
    try {
      const data = await apiCall('/materials');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.materials || [];
    } catch (err) { return []; }
  }

  async function getOwners() {
    try {
      const data = await apiCall('/owners');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.owners || [];
    } catch (err) { return []; }
  }

  async function getWorlds() {
    try {
      const data = await apiCall('/worlds');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.worlds || [];
    } catch (err) { return []; }
  }

  async function getStats() {
    try {
      const data = await apiCall('/stats');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.stats || {
        total_shops: 0, total_materials: 0, total_owners: 0,
        total_worlds: 0, total_requests: 0, total_activity: 0,
        selling_shops: 0, buying_shops: 0
      };
    } catch (err) {
      return {
        total_shops: 0, total_materials: 0, total_owners: 0,
        total_worlds: 0, total_requests: 0, total_activity: 0,
        selling_shops: 0, buying_shops: 0
      };
    }
  }

  async function getTopShops(limit, material) {
    try {
      const params = [];
      params.push('limit=' + (limit || 12));
      if (material) params.push('material=' + encodeURIComponent(material));
      const data = await apiCall('/shops/top?' + params.join('&'));
      if (!data.success) throw new Error(data.error || '读取失败');
      return { success: true, results: data.results || [], total: data.total || 0 };
    } catch (err) {
      return { success: false, results: [], total: 0, error: err.message };
    }
  }

  async function getItemList(options) {
    try {
      options = options || {};
      const params = [];
      if (options.q) params.push('q=' + encodeURIComponent(options.q));
      if (options.shop_type) params.push('shop_type=' + encodeURIComponent(options.shop_type));
      if (options.world) params.push('world=' + encodeURIComponent(options.world));
      if (options.min_price !== undefined && options.min_price !== '' && !isNaN(Number(options.min_price)))
        params.push('min_price=' + Number(options.min_price));
      if (options.max_price !== undefined && options.max_price !== '' && !isNaN(Number(options.max_price)))
        params.push('max_price=' + Number(options.max_price));
      params.push('sort=' + (options.sort || 'shops_desc'));
      params.push('page=' + (options.page || 1));
      params.push('pageSize=' + (options.pageSize || 30));
      const data = await apiCall('/items?' + params.join('&'));
      if (!data.success) throw new Error(data.error || '读取失败');
      return { success: true, results: data.results || [], total: data.total || 0, page: data.page || 1, page_size: data.page_size || 30, total_pages: data.total_pages || 1 };
    } catch (err) {
      return { success: false, results: [], total: 0, page: 1, page_size: 30, total_pages: 1, error: err.message };
    }
  }

  async function getItemDetail(material, options) {
    try {
      if (!material) throw new Error('物品名称不能为空');
      options = options || {};
      const params = [];
      if (options.shop_type) params.push('shop_type=' + encodeURIComponent(options.shop_type));
      if (options.world) params.push('world=' + encodeURIComponent(options.world));
      if (options.owner) params.push('owner=' + encodeURIComponent(options.owner));
      if (options.min_price !== undefined && options.min_price !== '' && !isNaN(Number(options.min_price)))
        params.push('min_price=' + Number(options.min_price));
      if (options.max_price !== undefined && options.max_price !== '' && !isNaN(Number(options.max_price)))
        params.push('max_price=' + Number(options.max_price));
      params.push('sort=' + (options.sort || 'price_asc'));
      params.push('page=' + (options.page || 1));
      params.push('pageSize=' + (options.pageSize || 25));
      const data = await apiCall('/items/' + encodeURIComponent(material) + '?' + params.join('&'));
      if (!data.success) throw new Error(data.error || '读取失败');
      return {
        success: true,
        material: data.material,
        item_name: data.item_name,
        stats: data.stats || {},
        shops: data.shops || [],
        total: data.total || 0,
        page: data.page || 1,
        page_size: data.page_size || 25,
        total_pages: data.total_pages || 1
      };
    } catch (err) {
      return { success: false, shops: [], total: 0, page: 1, page_size: 25, total_pages: 1, error: err.message };
    }
  }

  async function ingestShops(rawDataArray, opts) {
    if (!Array.isArray(rawDataArray)) rawDataArray = [rawDataArray];
    const CHUNK = 500;
    let added = 0, updated = 0, failed = 0;
    const errors = [];

    for (let start = 0; start < rawDataArray.length; start += CHUNK) {
      const chunk = rawDataArray.slice(start, start + CHUNK);
      try {
        const data = await apiCall('/shops', { method: 'POST', body: chunk });
        if (!data.success) throw new Error(data.error || '写入失败');
        added += data.added || 0;
        updated += data.updated || 0;
        failed += data.failed || 0;
        if (data.errors) for (const e of data.errors) errors.push(e);
      } catch (err) {
        failed += chunk.length;
        errors.push('第 ' + (start + 1) + '-' + Math.min(start + CHUNK, rawDataArray.length) + ' 条: ' + err.message);
      }
    }

    memoryCache.lastUpdate = 0;
    return { added, updated, failed, total: added + updated, errors };
  }

  async function updateShop(id, shopData) {
    try {
      const data = await apiCall('/shops/' + encodeURIComponent(id), { method: 'PUT', body: shopData });
      if (!data.success) throw new Error(data.error || '更新失败');
      memoryCache.lastUpdate = 0;
      return { success: true, shop: data.shop };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function deleteShop(id) {
    try {
      const data = await apiCall('/shops/' + encodeURIComponent(id), { method: 'DELETE' });
      if (!data.success) throw new Error(data.error || '删除失败');
      memoryCache.lastUpdate = 0;
      return { success: true, deleted: data.deleted };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function seedShops(count, mode) {
    try {
      const data = await apiCall('/shops/seed', {
        method: 'POST',
        body: { count: count || 10000, mode: mode || 'append' }
      });
      if (!data.success) throw new Error(data.error || '生成失败');
      memoryCache.lastUpdate = 0;
      return { success: true, inserted: data.inserted, count: data.count, mode: data.mode, elapsed_ms: data.elapsed_ms, cancelled: data.cancelled, errors: data.errors };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function cancelSeed() {
    try {
      const data = await apiCall('/shops/seed/cancel', { method: 'POST', body: {} });
      return { success: true, cancelled: data.cancelled, message: data.message };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function terminateSeed() {
    try {
      const data = await apiCall('/shops/seed/terminate', {
        method: 'POST',
        body: {}
      });
      if (!data.success) throw new Error(data.error || '终止失败');
      memoryCache.lastUpdate = 0;
      return {
        success: true,
        terminated: !!data.terminated,
        was_running: !!data.was_running,
        deleted: data.deleted || 0,
        elapsed_ms: data.elapsed_ms || 0,
        message: data.message || ''
      };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function seedProgress() {
    try {
      const data = await apiCall('/shops/seed/progress');
      return data;
    } catch (err) {
      return { running: false, progress: 0, inserted: 0, error: err.message };
    }
  }

  async function clearAllShops(reason) {
    try {
      const data = await apiCall('/shops', {
        method: 'DELETE',
        body: { confirm: true, reason: reason || '用户操作' }
      });
      if (!data.success) throw new Error(data.error || '清空失败');
      memoryCache.lastUpdate = 0;
      return { success: true, cleared: data.cleared };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function getSettings() {
    try {
      const data = await apiCall('/settings');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.settings || {};
    } catch (err) {
      console.warn('读取系统设置失败:', err.message);
      return {};
    }
  }

  async function getSetting(key) {
    try {
      const data = await apiCall('/settings/' + encodeURIComponent(key));
      if (!data.success) return null;
      return data.setting ? data.setting.value : null;
    } catch (err) { return null; }
  }

  async function updateSetting(key, value) {
    try {
      const data = await apiCall('/settings/' + encodeURIComponent(key), {
        method: 'PUT', body: { value: value }
      });
      if (!data.success) throw new Error(data.error || '更新失败');
      return { success: true, action: data.action, key: data.key, value: data.value };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function updateSettingsBatch(settingsObj) {
    try {
      const data = await apiCall('/settings', { method: 'PUT', body: settingsObj });
      if (!data.success) throw new Error(data.error || '批量更新失败');
      return { success: true, inserted: data.inserted, updated: data.updated, errors: data.errors };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function getHarbor() {
    try {
      const data = await apiCall('/harbor');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.harbor || { world: 'world', x: 0, y: 64, z: 0 };
    } catch (err) {
      return { world: 'world', x: 0, y: 64, z: 0 };
    }
  }

  async function setHarbor(harbor) {
    try {
      const data = await apiCall('/harbor', { method: 'PUT', body: harbor || { world: 'world', x: 0, y: 64, z: 0 } });
      if (!data.success) throw new Error(data.error || '更新失败');
      return { success: true, harbor: data.harbor };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function recordActivity(material) {
    if (!material) return { success: false };
    try {
      const data = await apiCall('/activity/' + encodeURIComponent(material), { method: 'PUT' });
      return data || { success: true };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function getActivity(limit) {
    try {
      const params = new URLSearchParams();
      if (limit) params.append('limit', limit);
      const data = await apiCall('/activity' + (params.toString() ? '?' + params.toString() : ''));
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.activity || [];
    } catch (err) { return []; }
  }

  async function exportAll() {
    try {
      const shops = await getAllShops();
      const harbor = await getHarbor();
      return {
        version: '4.0', exported_at: new Date().toISOString(),
        shop_count: shops.length, shops: shops, harbor: harbor
      };
    } catch (err) {
      return { version: '4.0', exported_at: new Date().toISOString(), shop_count: 0, shops: [], harbor: { world: 'world', x: 0, y: 64, z: 0 } };
    }
  }

  async function importAll(data) {
    if (!data || (!Array.isArray(data.shops) && !Array.isArray(data))) {
      return { success: false, error: '数据格式无效 (需要 { shops: [...] } 或数组)' };
    }
    const shops = Array.isArray(data) ? data : (data.shops || []);
    if (shops.length === 0) return { success: false, error: '没有可导入的商店数据' };

    const CHUNK = 500;
    let added = 0, updated = 0, failed = 0;
    const errors = [];

    for (let start = 0; start < shops.length; start += CHUNK) {
      const chunk = shops.slice(start, start + CHUNK);
      try {
        const data = await apiCall('/shops/import', { method: 'POST', body: chunk });
        if (!data.success) throw new Error(data.error || '导入失败');
        added += data.added || 0;
        updated += data.updated || 0;
        failed += data.failed || 0;
        if (data.errors) for (const e of data.errors) errors.push(e);
      } catch (err) {
        failed += chunk.length;
        errors.push('批量 ' + (start + 1) + '-' + Math.min(start + CHUNK, shops.length) + ': ' + err.message);
      }
    }

    if (data.harbor) {
      try { await setHarbor(data.harbor); } catch (e) {}
    }

    memoryCache.lastUpdate = 0;
    return { success: true, added, updated, failed, total: added + updated, errors: errors.slice(0, 20) };
  }

  async function getFetchLog(limit) {
    try {
      const params = new URLSearchParams();
      if (limit) params.append('limit', limit);
      const data = await apiCall('/log' + (params.toString() ? '?' + params.toString() : ''));
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.logs || [];
    } catch (err) { return []; }
  }

  async function getHealth() {
    try {
      const data = await apiCall('/health');
      return data || { success: true, status: 'online' };
    } catch (err) {
      return { success: false, status: 'offline', error: err.message };
    }
  }

  async function getConfig() {
    try {
      const data = await apiCall('/config');
      if (!data.success) throw new Error(data.error || '读取失败');
      return data.config || {};
    } catch (err) {
      return {
        app_name: 'QshopWebUI', default_page_size: 12, max_page_size: 5000,
        search_min_length: 2, max_batch_size: 10000,
        enable_activity: true, enable_search: true, enable_filter: true,
        require_auth: false, api_rate_limit: 1000, session_timeout: 3600
      };
    }
  }

  async function login(username, password) {
    try {
      const data = await apiCall('/auth/login', {
        method: 'POST',
        body: { username: String(username || 'admin').trim(), password: String(password || '') }
      });
      if (!data.success) throw new Error(data.error || '登录失败');
      authState.sessionId = data.session_id;
      authState.username = data.username || username;
      authState.role = data.role || 'user';
      authState.expiresAt = Date.now() + (data.expires_in || 3600) * 1000;
      saveLocalSession();
      return {
        success: true,
        session_id: data.session_id,
        expires_in: data.expires_in,
        username: authState.username,
        role: authState.role,
        is_admin: !!data.is_admin || authState.role === 'admin'
      };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function logout() {
    try {
      if (authState.sessionId) {
        await apiCall('/auth/logout', { method: 'POST', body: { session_id: authState.sessionId } });
      }
    } catch (e) {}
    authState.sessionId = null;
    authState.username = null;
    authState.role = 'guest';
    authState.expiresAt = 0;
    clearLocalSession();
    return { success: true };
  }

  async function registerUser(username, password, email) {
    try {
      const data = await apiCall('/auth/register', {
        method: 'POST',
        body: { username: String(username || '').trim(), password: String(password || ''), email: email ? String(email).trim() : '' }
      });
      if (!data.success) throw new Error(data.error || '注册失败');
      return { success: true, username: data.username, role: data.role, message: data.message };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  async function getAdminConfig() { return await apiCall('/admin/config', { method: 'GET' }); }
  async function setAdminConfig(updates) { return await apiCall('/admin/config', { method: 'POST', body: updates }); }
  async function restartServer() { return await apiCall('/server/restart', { method: 'POST' }); }
  async function getRealtimeStats() { return await apiCall('/stats/realtime', { method: 'GET' }); }

  async function checkAuth() {
    // 1) 先从本地会话恢复
    if (!authState.sessionId) restoreLocalSession();
    // 2) 会话过期检查
    if (authState.expiresAt && Date.now() > authState.expiresAt) {
      authState.sessionId = null;
      authState.role = 'guest';
    }
    // 3) 尝试调用后端 /api/auth/status 校验
    if (authState.sessionId) {
      try {
        const data = await apiCall('/auth/status', { method: 'GET' });
        if (data && data.success) {
          authState.username = data.username || authState.username;
          authState.role = data.role || authState.role;
          if (data.authenticated) {
            return {
              authenticated: true,
              isAdmin: !!data.is_admin || authState.role === 'admin',
              username: authState.username,
              role: authState.role
            };
          }
        }
      } catch (e) {
        // 后端不可用或接口不存在：降级使用本地会话判断
      }
    }
    // 降级判断
    if (authState.sessionId) {
      const isAdmin = authState.role === 'admin';
      return { isAdmin: isAdmin, username: authState.username, role: authState.role, authenticated: true };
    }
    return { isAdmin: false, authenticated: false, role: 'guest', username: null };
  }

  function isLoggedIn() {
    if (!authState.sessionId) restoreLocalSession();
    if (authState.expiresAt && Date.now() > authState.expiresAt) return false;
    return !!authState.sessionId;
  }

  function isAdmin() {
    if (!isLoggedIn()) return false;
    return authState.role === 'admin';
  }

  function getSession() {
    if (!authState.sessionId) restoreLocalSession();
    return { sessionId: authState.sessionId, username: authState.username, role: authState.role, expiresAt: authState.expiresAt };
  }

  global.QSDB = {
    search: searchShops,
    getAll: getAllShops,
    getById: getShopById,
    getByMaterial: getShopsByMaterial,
    getTopShops: getTopShops,
    getMaterials: getMaterials,
    getOwners: getOwners,
    getWorlds: getWorlds,
    getStats: getStats,

    // 物品堆叠列表 / 物品详情
    getItemList: getItemList,
    getItemDetail: getItemDetail,

    ingest: ingestShops,
    seed: seedShops,
    seedProgress: seedProgress,
    terminateSeed: terminateSeed,
    cancelSeed: cancelSeed,
    update: updateShop,
    deleteShop: deleteShop,
    clear: clearAllShops,

    getSettings: getSettings,
    getSetting: getSetting,
    updateSetting: updateSetting,
    updateSettings: updateSettingsBatch,

    // 管理员：.env 配置编辑 + 重启 + 实时统计
    getAdminConfig: getAdminConfig,
    setAdminConfig: setAdminConfig,
    restartServer: restartServer,
    getRealtimeStats: getRealtimeStats,

    getHarbor: getHarbor,
    setHarbor: setHarbor,

    recordActivity: recordActivity,
    getActivity: getActivity,

    exportAll: exportAll,
    importAll: importAll,

    getLog: getFetchLog,
    getHealth: getHealth,
    getConfig: getConfig,

    login: login,
    logout: logout,
    register: registerUser,
    checkAuth: checkAuth,
    isLoggedIn: isLoggedIn,
    isAdmin: isAdmin,
    getSession: getSession,

    // 查询连接状态
    getQsFilterStatus: async function () {
      try {
        const data = await apiCall('/qsfilter/status', { method: 'GET' });
        if (data && data.success) {
          return data;
        }
        return { success: false, enabled: false, connected: false, qs_available: false, error: data.error || '查询失败' };
      } catch (e) {
        return { success: false, error: e.message || '网络错误', connected: false };
      }
    },
    // 强制重新连接（需要管理员权限）
    reconnectQsFilter: async function () {
      try {
        const data = await apiCall('/qsfilter/reconnect', {
          method: 'POST',
          body: {}
        });
        return data;
      } catch (e) {
        return { success: false, error: e.message || '重连失败', connected: false };
      }
    },

    clearCache: function () {
      memoryCache.allShops = null;
      memoryCache.lastUpdate = 0;
    },
    forceRefresh: async function () {
      memoryCache.lastUpdate = 0;
      return await getAllShops();
    },

    getSyncStatus: async function () {
      try {
        const data = await apiCall('/sync/status', { method: 'GET' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    triggerSyncNow: async function () {
      try {
        const data = await apiCall('/sync/now', { method: 'POST', body: {} });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },

    getStatsRequests: async function () {
      try {
        const data = await apiCall('/stats/requests', { method: 'GET' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },

    getBackupStatus: async function () {
      try {
        const data = await apiCall('/backup/status', { method: 'GET' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    listBackups: async function () {
      try {
        const data = await apiCall('/backup/list', { method: 'GET' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    createBackup: async function (operator) {
      try {
        const data = await apiCall('/backup/now', { method: 'POST', body: { operator: operator || 'admin' } });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    restoreBackup: async function (file, operator) {
      try {
        const data = await apiCall('/backup/restore', { method: 'POST', body: { file: file, operator: operator || 'admin' } });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    deleteBackup: async function (file) {
      try {
        const data = await apiCall('/backup/' + encodeURIComponent(file), { method: 'DELETE' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    cleanupBackups: async function () {
      try {
        const data = await apiCall('/backup/cleanup', { method: 'POST', body: {} });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },

    adminSearchShops: async function (opts) {
      opts = opts || {};
      const params = new URLSearchParams();
      if (opts.q) params.append('q', opts.q);
      if (opts.material) params.append('material', opts.material);
      if (opts.owner) params.append('owner', opts.owner);
      if (opts.world) params.append('world', opts.world);
      if (opts.shop_type) params.append('shop_type', opts.shop_type);
      if (opts.min_price !== undefined && opts.min_price !== '') params.append('min_price', opts.min_price);
      if (opts.max_price !== undefined && opts.max_price !== '') params.append('max_price', opts.max_price);
      if (opts.min_activity !== undefined && opts.min_activity !== '') params.append('min_activity', opts.min_activity);
      if (opts.reasonable) params.append('reasonable', 'true');
      if (opts.page) params.append('page', opts.page);
      if (opts.pageSize) params.append('pageSize', opts.pageSize);
      try {
        const data = await apiCall('/admin/shops/search?' + params.toString());
        return data || { success: false, shops: [], total: 0 };
      } catch (e) {
        return { success: false, error: e.message, shops: [], total: 0 };
      }
    },
    adminBatch: async function (ids, action, extra) {
      try {
        const body = Object.assign({ ids: ids || [], action: action || 'delete' }, extra || {});
        const data = await apiCall('/admin/shops/batch', { method: 'POST', body: body });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    exportShops: async function (format, opts) {
      opts = opts || {};
      const params = new URLSearchParams();
      params.append('format', format || 'json');
      if (opts.q) params.append('q', opts.q);
      if (opts.material) params.append('material', opts.material);
      if (opts.owner) params.append('owner', opts.owner);
      if (opts.world) params.append('world', opts.world);
      if (opts.shop_type) params.append('shop_type', opts.shop_type);
      try {
        const url = API_BASE + '/export/shops?' + params.toString();
        // 直接下载：使用浏览器原生方式
        if (format === 'csv' || format === 'json') {
          window.open(url, '_blank');
          return { success: true };
        }
        const data = await apiCall('/export/shops?' + params.toString());
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    importShops: async function (format, data, conflictMode) {
      try {
        const body = { format: format || 'json', conflict_mode: conflictMode || 'skip', data: data };
        const result = await apiCall('/import/shops', { method: 'POST', body: body });
        return result || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    getAdminDashboard: async function () {
      try {
        const data = await apiCall('/admin/dashboard', { method: 'GET' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },

    getAnnouncements: async function () {
      try {
        const data = await apiCall('/announcements', { method: 'GET' });
        return data || { success: false, results: [], total: 0 };
      } catch (e) {
        return { success: false, error: e.message, results: [], total: 0 };
      }
    },
    createAnnouncement: async function (ann) {
      try {
        const data = await apiCall('/announcements', { method: 'POST', body: ann });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    updateAnnouncement: async function (id, ann) {
      try {
        const data = await apiCall('/announcements/' + encodeURIComponent(id), { method: 'PUT', body: ann });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    },
    deleteAnnouncement: async function (id) {
      try {
        const data = await apiCall('/announcements/' + encodeURIComponent(id), { method: 'DELETE' });
        return data || { success: false };
      } catch (e) {
        return { success: false, error: e.message };
      }
    }
  };

  // 启动时执行健康检查 (非阻塞)
  getHealth().then(status => {
    if (status.success && status.status === 'online') {
      console.log('✅ QshopWebUI 已就绪 - 后端正常, 数据库: ' + (status.database || 'connected') + ', 版本: ' + (status.version || '4.0'));
    } else {
      console.warn('⚠️  后端服务器不可用 - 请确认 Node.js 服务器已启动');
    }
  }).catch(() => {});
})(typeof window !== 'undefined' ? window : this);
