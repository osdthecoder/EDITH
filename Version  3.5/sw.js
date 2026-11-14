const V="edith-v3";
const CORE=["./","index.html","manifest.json","icon-192.png","icon-512.png","privacy.html","lib/pdf-lib.min.js","lib/pdf.min.js","lib/pdf.worker.min.js","lib/jszip.min.js"];
const OPT=["lib/icons.json","lib/fonts/inter-400.woff2","lib/fonts/inter-600.woff2","lib/fonts/spacegrotesk-600.woff2","lib/fonts/spacegrotesk-700.woff2"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(V).then(async c=>{await c.addAll(CORE);await Promise.allSettled(OPT.map(u=>c.add(u)))}).then(()=>self.skipWaiting()))});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==V).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener("fetch",e=>{const r=e.request;if(r.method!=="GET"||new URL(r.url).origin!==location.origin)return;
e.respondWith(fetch(r).then(res=>{const cp=res.clone();if(res.ok)caches.open(V).then(c=>c.put(r,cp));return res}).catch(()=>caches.match(r).then(m=>m||caches.match("index.html"))))});
