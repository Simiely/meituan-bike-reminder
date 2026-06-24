// 美团骑车 · 锁车提醒 — Service Worker
const CACHE_NAME = 'meituan-reminder-v1';

// 需要缓存的资源
const PRECACHE_URLS = [
  '/',
  'index.html',
  'manifest.json'
];

// 安装阶段：预缓存核心资源
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

// 激活阶段：清理旧缓存
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames
          .filter(name => name !== CACHE_NAME)
          .map(name => caches.delete(name))
      );
    }).then(() => self.clients.claim())
  );
});

// 拦截网络请求：缓存优先策略
self.addEventListener('fetch', event => {
  // 只缓存同源请求
  if (event.request.url.startsWith(self.location.origin)) {
    event.respondWith(
      caches.match(event.request)
        .then(cached => cached || fetch(event.request))
        .catch(() => fetch(event.request))
    );
  }
});

// 处理推送通知（如果通过 Push API 发送）
self.addEventListener('push', event => {
  const data = event.data ? event.data.text() : '该锁车了！';

  const options = {
    body: data,
    icon: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🚲</text></svg>',
    badge: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🚲</text></svg>',
    vibrate: [200, 100, 200, 100, 200],
    tag: 'meituan-reminder',
    requireInteraction: true,
    actions: [
      { action: 'locked', title: '✅ 已锁车' },
      { action: 'snooze', title: '⏰ 再骑 10 分钟' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification('🚲 美团骑车 — 该锁车了！', options)
  );
});

// 处理通知点击事件
self.addEventListener('notificationclick', event => {
  event.notification.close();

  if (event.action === 'locked') {
    // 用户点击了"已锁车"
    // 可以通知客户端页面更新状态
    self.clients.matchAll({ type: 'window' }).then(clients => {
      clients.forEach(client => {
        client.postMessage({ action: 'locked' });
      });
    });
  } else if (event.action === 'snooze') {
    // 用户点击了"再骑 10 分钟" — 设置 10 分钟后的提醒
    const snoozeUntil = Date.now() + 10 * 60 * 1000;
    // 注意：Service Worker 不支持 setTimeout 在后台长期存活
    // 真正的 snooze 功能需要在客户端实现
    self.clients.matchAll({ type: 'window' }).then(clients => {
      clients.forEach(client => {
        client.postMessage({ action: 'snooze', minutes: 10 });
      });
    });
  } else {
    // 点击通知本身 — 打开/聚焦页面
    self.clients.matchAll({ type: 'window' }).then(clients => {
      if (clients.length > 0) {
        clients[0].focus();
      } else {
        self.clients.openWindow('/');
      }
    });
  }
});
