const peersEl = document.getElementById('peers');
const discoverBtn = document.getElementById('discoverBtn');
const pickBtn = document.getElementById('pickBtn');
const sendBtn = document.getElementById('sendBtn');
const hostInput = document.getElementById('hostInput');
const fileInfo = document.getElementById('fileInfo');
const progressBar = document.getElementById('progress');
const progressText = document.getElementById('progressText');
const preview = document.getElementById('preview');
const toast = document.getElementById('toast');

let selectedMeta = null;

discoverBtn.onclick = () => { Android.discover(); showToast('Discovering peers...'); };
pickBtn.onclick = () => { Android.pickFile(); showToast('Opening file picker...'); };
sendBtn.onclick = () => {
  const host = hostInput.value.trim();
  if (!host) return showModal('Error','Please provide peer IP address.');
  Android.sendTo(host);
  showToast('Sending to '+host);
};

window.onPeers = (data) => {
  peersEl.innerHTML = '';
  if (!data) return;
  const items = data.split(';;').filter(Boolean);
  for (let it of items) {
    const [name, addr] = it.split('::');
    const li = document.createElement('li');
    li.innerHTML = `<div><strong>${escapeHtml(name||'Unknown')}</strong><div class="muted">${escapeHtml(addr||'')}</div></div><div><button class="btn" onclick="pasteAddr('${addr}')">Use</button></div>`;
    peersEl.appendChild(li);
  }
};

function pasteAddr(a){ hostInput.value = a; showToast('IP filled'); }

window.onStatus = (s) => { showToast(s); };
window.onConnectionInfo = (addr) => { if (addr) { hostInput.value = addr; showToast('Connected: '+addr); } };
window.onProgress = (p) => { progressBar.style.width = Math.min(100,p)+'%'; progressText.textContent = Math.min(100,p)+'%'; };
window.onIncoming = (path) => { showModal('Received','Saved to: '+path); };
window.onTransferDone = (s) => { showToast('Transfer '+s); progressBar.style.width='0%'; progressText.textContent='0%'; };

// Called from Android when a file is selected; small helper (MainActivity passes URI string as message)
window.onFileSelected = (info) => {
  // `info` can be a short text describing selection; show it
  fileInfo.textContent = info || 'File selected';
};

function showToast(msg, time=2400){ toast.textContent = msg; toast.classList.remove('hidden'); setTimeout(()=>toast.classList.add('hidden'), time); }
function showModal(title, body){ document.getElementById('modalTitle').textContent = title; document.getElementById('modalBody').textContent = body; document.getElementById('modal').classList.remove('hidden'); }
document.getElementById('modalClose').onclick = () => document.getElementById('modal').classList.add('hidden');

function escapeHtml(s){ if(!s) return ''; return s.replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":"&#39;"})[c]); }
