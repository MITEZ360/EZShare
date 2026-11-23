(function () {
  var DIR = document.body.getAttribute('data-dir') || '';
  var CAN_UPLOAD = document.body.getAttribute('data-upload') === '1';
  var CAN_DELETE = document.body.getAttribute('data-delete') === '1';

  function $(sel) { return document.querySelector(sel); }
  function el(tag, cls) { var n = document.createElement(tag); if (cls) n.className = cls; return n; }

  var toastTimer = null;
  function toast(msg) {
    var t = $('#toast');
    if (!t) return;
    t.textContent = msg;
    t.classList.add('on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove('on'); }, 2200);
  }

  /* ---------- clipboard (HTTP 局域网兼容) ---------- */
  function legacyCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('contenteditable', 'true');
    ta.style.position = 'fixed';
    ta.style.top = '0';
    ta.style.left = '0';
    ta.style.width = '2px';
    ta.style.height = '2px';
    ta.style.padding = '0';
    ta.style.border = 'none';
    ta.style.opacity = '0';
    ta.style.fontSize = '16px';
    document.body.appendChild(ta);
    var ok = false;
    try {
      ta.focus();
      ta.select();
      ta.setSelectionRange(0, text.length);
      ok = document.execCommand('copy');
    } catch (err) { ok = false; }
    document.body.removeChild(ta);
    return ok;
  }

  function manualCopy(text) {
    openModal(
      '<h3>请手动复制</h3><p>当前浏览器不允许自动写入剪贴板，请长按下方文字选择“拷贝”。</p>' +
      '<textarea id="manual-copy" spellcheck="false"></textarea>' +
      '<div class="foot"><button class="btn ghost" data-cancel>关闭</button></div>',
      null
    );
    var ta = $('#manual-copy');
    if (ta) {
      ta.value = text;
      setTimeout(function () {
        ta.focus(); ta.select(); ta.setSelectionRange(0, text.length);
      }, 60);
    }
  }

  function copyText(text, okMsg) {
    var msg = okMsg || '已复制';
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(function () {
        toast(msg);
      }).catch(function () {
        if (legacyCopy(text)) toast(msg); else manualCopy(text);
      });
      return;
    }
    if (legacyCopy(text)) toast(msg); else manualCopy(text);
  }

  /* ---------- modal helpers ---------- */
  var modal = $('#modal');
  var resolver = null;

  function openModal(html, onOk) {
    modal.innerHTML = '';
    var box = el('div', 'dialog');
    box.innerHTML = html;
    modal.appendChild(box);
    modal.classList.add('on');
    var input = box.querySelector('input');
    if (input) { setTimeout(function () { input.focus(); input.select(); }, 30); }
    var ok = box.querySelector('[data-ok]');
    var cancel = box.querySelector('[data-cancel]');
    function close() { modal.classList.remove('on'); modal.innerHTML = ''; }
    function submit() { var v = input ? input.value : ''; close(); if (onOk) onOk(v); }
    if (ok) ok.addEventListener('click', submit);
    if (cancel) cancel.addEventListener('click', close);
    box.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') { e.preventDefault(); submit(); }
      if (e.key === 'Escape') close();
    });
    modal.addEventListener('click', function (e) { if (e.target === modal) close(); }, { once: true });
  }

  /* ---------- filter ---------- */
  var search = $('#filter');
  if (search) {
    search.addEventListener('input', function () {
      var q = search.value.trim().toLowerCase();
      var rows = document.querySelectorAll('tbody tr');
      var shown = 0;
      rows.forEach(function (row) {
        var name = row.getAttribute('data-name') || '';
        var hit = !q || name.toLowerCase().indexOf(q) >= 0;
        row.style.display = hit ? '' : 'none';
        if (hit) shown++;
      });
      var c = $('#count');
      if (c) c.textContent = q ? shown + ' 项匹配' : rows.length + ' 个项目';
    });
  }

  /* ---------- upload ---------- */
  var queue = [];
  var inFlight = 0;
  var pending = 0;
  var reloading = false;

  function scheduleReload() {
    if (reloading) return;
    reloading = true;
    setTimeout(function () { location.reload(); }, 700);
  }

  function dockEnsure() {
    var dock = $('#dock');
    if (dock) dock.classList.add('on');
  }

  function uploadOne(file, finished) {
    var item = el('div', 'up');
    var title = el('div', 't');
    var name = el('b'); name.textContent = file.name;
    var pct = el('span'); pct.textContent = '0%';
    title.appendChild(name); title.appendChild(pct);
    var bar = el('div', 'bar');
    var fill = el('i'); bar.appendChild(fill);
    item.appendChild(title); item.appendChild(bar);
    $('#dock-list').appendChild(item);

    var url = '/api/upload?dir=' + encodeURIComponent(DIR) + '&name=' + encodeURIComponent(file.name);
    var xhr = new XMLHttpRequest();
    xhr.open('PUT', url, true);
    xhr.upload.onprogress = function (e) {
      if (!e.lengthComputable) return;
      var p = Math.round((e.loaded / e.total) * 100);
      pct.textContent = p + '%';
      fill.style.width = p + '%';
    };
    function fail(text) {
      pct.textContent = text;
      fill.style.background = '#e5484d';
      fill.style.width = '100%';
      toast('上传失败：' + file.name);
    }
    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) {
        pct.textContent = '完成';
        fill.style.width = '100%';
        setTimeout(function () { item.remove(); }, 1500);
      } else {
        fail('失败');
      }
      finished();
    };
    xhr.onerror = function () {
      fail('中断');
      finished();
    };
    xhr.send(file);
  }

  function pump() {
    while (inFlight < 2 && queue.length) {
      var file = queue.shift();
      inFlight++;
      uploadOne(file, function () {
        inFlight--;
        pending--;
        if (pending <= 0) scheduleReload();
        pump();
      });
    }
  }

  function enqueue(files) {
    if (!CAN_UPLOAD) { toast('当前未开启上传'); return; }
    if (!files || !files.length) return;
    dockEnsure();
    for (var i = 0; i < files.length; i++) {
      queue.push(files[i]);
      pending++;
    }
    pump();
  }

  /* ---------- buttons ---------- */
  var picker = $('#picker');
  var btnUpload = $('#btn-upload');
  if (btnUpload && picker) {
    btnUpload.addEventListener('click', function () { picker.click(); });
    picker.addEventListener('change', function () {
      enqueue(picker.files);
      picker.value = '';
    });
  }

  var btnMkdir = $('#btn-mkdir');
  if (btnMkdir) {
    btnMkdir.addEventListener('click', function () {
      openModal('<h3>新建文件夹</h3><p>在当前目录中创建</p><input type="text" placeholder="文件夹名称">' +
        '<div class="foot"><button class="btn" data-cancel>取消</button><button class="btn primary" data-ok>创建</button></div>',
        function (value) {
          var name = (value || '').trim();
          if (!name) return;
          fetch('/api/mkdir', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ dir: DIR, name: name })
          }).then(function (r) { return r.json(); }).then(function (res) {
            if (res.ok) location.reload(); else toast(res.error || '创建失败');
          });
        });
    });
  }

  document.addEventListener('click', function (e) {
    var del = e.target.closest('[data-del]');
    if (del) {
      var path = del.getAttribute('data-del');
      var name = del.getAttribute('data-name-text') || path;
      var isDir = del.getAttribute('data-isdir') === '1';
      openModal('<h3>删除' + (isDir ? '文件夹' : '文件') + '</h3><p>' + name.replace(/</g, '&lt;') + '</p>' +
        '<div class="foot"><button class="btn" data-cancel>取消</button><button class="btn danger" data-ok>删除</button></div>',
        function () {
          fetch('/api/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: path })
          }).then(function (r) { return r.json(); }).then(function (res) {
            if (res.ok) location.reload(); else toast(res.error || '删除失败');
          });
        });
      return;
    }
    var copy = e.target.closest('[data-copy]');
    if (copy) {
      var text = copy.getAttribute('data-copy');
      copyText(text, '链接已复制');
    }
  });

  /* ---------- drag & drop ---------- */
  if (CAN_UPLOAD) {
    var overlay = $('#drop');
    var depth = 0;
    window.addEventListener('dragenter', function (e) {
      e.preventDefault(); depth++; if (overlay) overlay.classList.add('on');
    });
    window.addEventListener('dragover', function (e) { e.preventDefault(); });
    window.addEventListener('dragleave', function (e) {
      e.preventDefault(); depth--; if (depth <= 0 && overlay) { overlay.classList.remove('on'); depth = 0; }
    });
    window.addEventListener('drop', function (e) {
      e.preventDefault();
      depth = 0;
      if (overlay) overlay.classList.remove('on');
      var files = e.dataTransfer && e.dataTransfer.files;
      if (!files || !files.length) return;
      enqueue(files);
    });
  }
  /* ---------- text sharing ---------- */
  var textInput = $('#text-input');
  if (textInput) {
    var chars = $('#text-chars');
    textInput.addEventListener('input', function () {
      if (chars) chars.textContent = textInput.value.length + ' 字符';
    });
    textInput.addEventListener('keydown', function (e) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') { e.preventDefault(); sendText(); }
    });
    var sendBtn = $('#btn-text-send');
    if (sendBtn) sendBtn.addEventListener('click', sendText);
  }

  function sendText() {
    var input = $('#text-input');
    var btn = $('#btn-text-send');
    if (!input || !input.value.trim()) { toast('内容为空'); return; }
    if (btn) btn.disabled = true;
    fetch('/api/texts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: input.value })
    }).then(function (r) { return r.json(); }).then(function (res) {
      if (res.ok) location.reload();
      else { toast(res.error || '发送失败'); if (btn) btn.disabled = false; }
    }).catch(function () {
      toast('网络错误');
      if (btn) btn.disabled = false;
    });
  }

  var textRefresh = $('#btn-text-refresh');
  if (textRefresh) textRefresh.addEventListener('click', function () { location.reload(); });

  document.addEventListener('click', function (e) {
    var tc = e.target.closest('[data-textcopy]');
    if (tc) {
      var host = tc.closest('.snippet');
      var body = host ? host.querySelector('.s-body') : null;
      var visible = body ? body.textContent : '';
      if (visible.length < 20000) {
        // 同步复制，保留移动端浏览器的用户手势（HTTP 下 execCommand 仍可用）
        copyText(visible, '已复制到剪贴板');
      } else {
        // 页面只渲染了前 2 万字符，长文本需取全文
        var tid = tc.getAttribute('data-textcopy');
        fetch('/api/text?id=' + encodeURIComponent(tid))
          .then(function (r) { return r.json(); })
          .then(function (res) {
            if (res.ok) copyText(res.content, '已复制到剪贴板');
            else toast(res.error || '复制失败');
          })
          .catch(function () { toast('复制失败，请检查网络'); });
      }
      return;
    }
    var tdel = e.target.closest('[data-textdel]');
    if (tdel) {
      var delId = tdel.getAttribute('data-textdel');
      openModal('<h3>删除文本</h3><p>删除后无法恢复</p>' +
        '<div class="foot"><button class="btn" data-cancel>取消</button><button class="btn danger" data-ok>删除</button></div>',
        function () {
          fetch('/api/text/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: delId })
          }).then(function (r) { return r.json(); }).then(function (res) {
            if (res.ok) location.reload(); else toast(res.error || '删除失败');
          });
        });
    }
  });
})();
