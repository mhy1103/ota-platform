<template>
    <div class="toasts" id="toasts">
        <div class="toast" v-if="$h.t(m.msg)">
            <svg class="i"><use href="#i-check"></use></svg><span>{{ $h.s(m.msg) }}</span>
            <button type="button" data-toast-close aria-label="关闭">&times;</button>
        </div>
        <div class="toast err" v-if="$h.t(m.error)">
            <svg class="i"><use href="#i-alert"></use></svg><span>{{ $h.s(m.error) }}</span>
            <button type="button" data-toast-close aria-label="关闭">&times;</button>
        </div>
    </div>

    <dialog id="confirm-dialog">
        <div class="dlg-head" style="padding-bottom:22px"><h3 data-role="title">确认操作</h3><p data-role="body"></p></div>
        <div class="dlg-foot">
            <button type="button" class="btn btn-secondary" data-close>取消</button>
            <button type="button" class="btn" data-role="ok">确定</button>
        </div>
    </dialog>

    <dialog id="token-dialog" class="wide" data-busy data-countdown="10" v-if="$h.t(!$h.eq(m.newToken, null))">
        <div class="dlg-head">
            <h3><svg class="i"><use href="#i-key"></use></svg><span>{{ $h.s($h.cat('令牌「', m.newTokenName, '」已创建')) }}</span></h3>
            <p>令牌只显示这一次，关掉就再也看不到了。现在复制，存到密码管理器或 CI 的密钥里。</p>
        </div>
        <div class="dlg-body">
            <div class="token-box">
                <code data-token-value>{{ $h.s(m.newToken) }}</code>
                <button class="btn btn-sm" type="button" data-token-copy :data-copy.attr="$h.av(m.newToken)" data-copy-label="已复制令牌"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制</button>
            </div>
            <div class="section-label" style="margin-top:18px">怎么用</div>
            <div class="code" style="margin:0" v-if="$h.t($h.eq(m.newTokenScope, 'publish'))">
                <div class="code-bar"><span>终端（在 App 项目目录里）</span></div>
<pre>ota login --server <span>{{ $h.s(m.newTokenServer) }}</span> --token <span>{{ $h.s(m.newToken) }}</span>
ota publish --app <span>{{ $h.s(m.newTokenApp) }}</span></pre>
            </div>
            <div class="code" style="margin:0" v-if="$h.t($h.eq(m.newTokenScope, 'admin'))">
                <div class="code-bar"><span>管理接口</span></div>
<pre>curl <span>{{ $h.s(m.newTokenServer) }}</span>/api/v1/apps \
  -H "Authorization: Bearer <span>{{ $h.s(m.newToken) }}</span>"</pre>
            </div>
            <div class="code" style="margin:0" v-if="$h.t($h.eq(m.newTokenScope, 'metrics'))">
                <div class="code-bar"><span>Prometheus 采集配置（prometheus.yml）</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre>scrape_configs:
  - job_name: ota
    scheme: <span>{{ $h.s(m.newTokenScheme) }}</span>
    metrics_path: <span>{{ $h.s(m.newTokenPath) }}</span>
    authorization:
      credentials: <span>{{ $h.s(m.newToken) }}</span>
    static_configs:
      - targets: ['<span>{{ $h.s(m.newTokenHost) }}</span>']</pre>
            </div>
        </div>
        <div class="dlg-foot">
            <span class="hint" data-role="hint">请先复制保存，倒计时结束后才能关闭</span>
            <button class="btn" type="button" data-role="close" disabled>我已保存（<span data-role="left">10</span>）</button>
        </div>
    </dialog>

    <svg width="0" height="0" style="position:absolute" aria-hidden="true">
        <defs><mask id="logo-cut"><rect width="24" height="24" fill="#fff"></rect><path d="M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95" fill="none" stroke="#000" stroke-width="2.2" stroke-linecap="round"></path><circle cx="16.6" cy="9.3" r="1.6" fill="#000"></circle></mask></defs>
        <symbol id="i-logo" viewBox="0 0 24 24"><rect x="1" y="1" width="22" height="22" rx="7" fill="currentColor" stroke="none" mask="url(#logo-cut)"></rect></symbol>
        <symbol id="i-grid" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1.5"></rect><rect x="14" y="3" width="7" height="7" rx="1.5"></rect><rect x="3" y="14" width="7" height="7" rx="1.5"></rect><rect x="14" y="14" width="7" height="7" rx="1.5"></rect></symbol>
        <symbol id="i-key" viewBox="0 0 24 24"><circle cx="7.5" cy="15.5" r="5.5"></circle><path d="m21 2-9.6 9.6"></path><path d="m15.5 7.5 3 3L22 7l-3-3"></path></symbol>
        <symbol id="i-activity" viewBox="0 0 24 24"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></symbol>
        <symbol id="i-layers" viewBox="0 0 24 24"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></symbol>
        <symbol id="i-file" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></symbol>
        <symbol id="i-user" viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"></circle><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"></path></symbol>
        <symbol id="i-logout" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></symbol>
        <symbol id="i-menu" viewBox="0 0 24 24"><line x1="4" y1="6" x2="20" y2="6"></line><line x1="4" y1="12" x2="20" y2="12"></line><line x1="4" y1="18" x2="20" y2="18"></line></symbol>
        <symbol id="i-shield" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path><path d="m9 12 2 2 4-4"></path></symbol>
        <symbol id="i-alert" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></symbol>
        <symbol id="i-check" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></symbol>
        <symbol id="i-plus" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></symbol>
        <symbol id="i-upload" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></symbol>
        <symbol id="i-apple" viewBox="0 0 24 24"><path fill="currentColor" stroke="none" d="M16.37 12.67c-.02-2.16 1.77-3.2 1.85-3.25-1.01-1.47-2.58-1.67-3.13-1.7-1.33-.13-2.6.79-3.28.79-.68 0-1.72-.77-2.83-.75-1.45.02-2.8.85-3.55 2.15-1.52 2.63-.39 6.52 1.09 8.66.73 1.04 1.59 2.21 2.72 2.17 1.09-.04 1.5-.7 2.82-.7 1.31 0 1.68.7 2.83.68 1.17-.02 1.91-1.06 2.62-2.11.83-1.2 1.17-2.37 1.19-2.43-.03-.01-2.28-.87-2.33-3.51zM14.2 6.33c.6-.73 1-1.73.89-2.74-.86.04-1.91.58-2.53 1.3-.55.64-1.04 1.67-.91 2.65.96.08 1.94-.49 2.55-1.21z"></path></symbol>
        <symbol id="i-android" viewBox="0 0 24 24"><path d="M6.5 10.5h11v6.5a1.5 1.5 0 0 1-1.5 1.5H8a1.5 1.5 0 0 1-1.5-1.5z"></path><path d="M6.5 9.5a5.5 5.5 0 0 1 11 0"></path><path d="M8.5 5 7.3 3.2M15.5 5l1.2-1.8"></path><path d="M9.5 18.5V21M14.5 18.5V21M4 11v4M20 11v4"></path></symbol>
        <symbol id="i-send" viewBox="0 0 24 24"><path d="m22 2-7 20-4-9-9-4z"></path><path d="M22 2 11 13"></path></symbol>
        <symbol id="i-copy" viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></symbol>
        <symbol id="i-trash" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path><path d="M10 11v6"></path><path d="M14 11v6"></path><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"></path></symbol>
        <symbol id="i-search" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></symbol>
        <symbol id="i-arrow" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></symbol>
        <symbol id="i-left" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"></polyline></symbol>
        <symbol id="i-right" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"></polyline></symbol>
        <symbol id="i-inbox" viewBox="0 0 24 24"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"></polyline><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path></symbol>
        <symbol id="i-package" viewBox="0 0 24 24"><path d="M16.5 9.4 7.55 4.24"></path><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></symbol>
        <symbol id="i-share" viewBox="0 0 24 24"><circle cx="18" cy="5" r="2.6"></circle><circle cx="6" cy="12" r="2.6"></circle><circle cx="18" cy="19" r="2.6"></circle><path d="m8.3 10.8 7.4-4.2M8.3 13.2l7.4 4.2"></path></symbol>
        <symbol id="i-external" viewBox="0 0 24 24"><path d="M15 3h6v6"></path><path d="M10 14 21 3"></path><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path></symbol>
        <symbol id="i-eye-off" viewBox="0 0 24 24"><path d="M9.9 9.9a3 3 0 1 0 4.2 4.2"></path><path d="M10.7 5.1A10.4 10.4 0 0 1 12 5c7 0 10 7 10 7a13.2 13.2 0 0 1-1.7 2.7"></path><path d="M6.6 6.6A13.5 13.5 0 0 0 2 12s3 7 10 7a9.7 9.7 0 0 0 5.4-1.6"></path><path d="m2 2 20 20"></path></symbol>
        <symbol id="i-link" viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></symbol>
        <symbol id="i-users" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></symbol>
        <symbol id="i-eye" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></symbol>
        <symbol id="i-refresh" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></symbol>
        <symbol id="i-edit" viewBox="0 0 24 24"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></symbol>
        <symbol id="i-clock" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></symbol>
        <symbol id="i-chart" viewBox="0 0 24 24"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></symbol>
        <symbol id="i-gear" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></symbol>
        <symbol id="i-check-file" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><polyline points="9 15 11 17 15 13"></polyline></symbol>
        <symbol id="i-tick" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></symbol>
        <symbol id="i-code" viewBox="0 0 24 24"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></symbol>
        <symbol id="i-x" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></symbol>
        <symbol id="i-download" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></symbol>
        <symbol id="i-info" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></symbol>
        <symbol id="i-phone" viewBox="0 0 24 24"><rect x="5" y="2" width="14" height="20" rx="2.5"></rect><line x1="11" y1="18" x2="13" y2="18"></line></symbol>
        <symbol id="i-sliders" viewBox="0 0 24 24"><line x1="4" y1="21" x2="4" y2="14"></line><line x1="4" y1="10" x2="4" y2="3"></line><line x1="12" y1="21" x2="12" y2="12"></line><line x1="12" y1="8" x2="12" y2="3"></line><line x1="20" y1="21" x2="20" y2="16"></line><line x1="20" y1="12" x2="20" y2="3"></line><line x1="1" y1="14" x2="7" y2="14"></line><line x1="9" y1="8" x2="15" y2="8"></line><line x1="17" y1="16" x2="23" y2="16"></line></symbol>
        <symbol id="i-database" viewBox="0 0 24 24"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></symbol>
        <symbol id="i-zap" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></symbol>
        <symbol id="i-user-cog" viewBox="0 0 24 24"><circle cx="9" cy="8" r="4"></circle><path d="M2 21v-1a6 6 0 0 1 6-6h2.5"></path><circle cx="18" cy="17" r="2.5"></circle><path d="M18 12.5v2M18 19.5v2M22.5 17h-2M15.5 17h-2M21.2 13.8l-1.4 1.4M16.2 18.8l-1.4 1.4M21.2 20.2l-1.4-1.4M16.2 15.2l-1.4-1.4"></path></symbol>
        <symbol id="i-bell" viewBox="0 0 24 24"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></symbol>
        <symbol id="i-flask" viewBox="0 0 24 24"><path d="M9 3h6"></path><path d="M10 3v6L4.5 19a1.5 1.5 0 0 0 1.3 2.2h12.4a1.5 1.5 0 0 0 1.3-2.2L14 9V3"></path><path d="M7.5 15h9"></path></symbol>
        <symbol id="i-stethoscope" viewBox="0 0 24 24"><path d="M5 3v5a5 5 0 0 0 10 0V3"></path><path d="M10 13v2a5 5 0 0 0 10 0v-1"></path><circle cx="20" cy="12" r="2"></circle></symbol>
        <symbol id="i-lock" viewBox="0 0 24 24"><rect x="4" y="11" width="16" height="10" rx="2"></rect><path d="M8 11V7a4 4 0 0 1 8 0v4"></path></symbol>
        <symbol id="i-updown" viewBox="0 0 24 24"><polyline points="7 15 12 20 17 15"></polyline><polyline points="7 9 12 4 17 9"></polyline></symbol>
        <symbol id="i-chevron-down" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"></polyline></symbol>
        <symbol id="i-keyboard" viewBox="0 0 24 24"><rect x="2" y="5" width="20" height="14" rx="2"></rect><path d="M6 9h.01M10 9h.01M14 9h.01M18 9h.01M6 13h.01M18 13h.01M10 13h4M7 16h10"></path></symbol>
        <symbol id="i-sun" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"></path></symbol>
        <symbol id="i-moon" viewBox="0 0 24 24"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></symbol>
        <symbol id="i-monitor" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></symbol>
        <symbol id="i-expand" viewBox="0 0 24 24"><polyline points="15 3 21 3 21 9"></polyline><polyline points="9 21 3 21 3 15"></polyline><line x1="21" y1="3" x2="14" y2="10"></line><line x1="3" y1="21" x2="10" y2="14"></line></symbol>
        <symbol id="i-shrink" viewBox="0 0 24 24"><polyline points="4 14 10 14 10 20"></polyline><polyline points="20 10 14 10 14 4"></polyline><line x1="14" y1="10" x2="21" y2="3"></line><line x1="3" y1="21" x2="10" y2="14"></line></symbol>
        <symbol id="i-help" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path><line x1="12" y1="17" x2="12.01" y2="17"></line></symbol>
        <symbol id="i-compass" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"></polygon></symbol>
        <symbol id="i-sparkle" viewBox="0 0 24 24"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"></path><path d="M19 15l.7 1.8L21.5 17.5l-1.8.7L19 20l-.7-1.8-1.8-.7 1.8-.7z"></path></symbol>
        <symbol id="i-command" viewBox="0 0 24 24"><path d="M18 3a3 3 0 0 0-3 3v12a3 3 0 0 0 3 3 3 3 0 0 0 3-3 3 3 0 0 0-3-3H6a3 3 0 0 0-3 3 3 3 0 0 0 3 3 3 3 0 0 0 3-3V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3 3 3 0 0 0 3 3h12a3 3 0 0 0 3-3 3 3 0 0 0-3-3z"></path></symbol>
        <symbol id="i-palette" viewBox="0 0 24 24"><circle cx="13.5" cy="6.5" r="1.2"></circle><circle cx="17.5" cy="10.5" r="1.2"></circle><circle cx="8.5" cy="7.5" r="1.2"></circle><circle cx="6.5" cy="12.5" r="1.2"></circle><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.93 0 1.65-.75 1.65-1.69 0-.44-.18-.84-.44-1.13-.29-.29-.44-.65-.44-1.13a1.64 1.64 0 0 1 1.67-1.67h2c3.05 0 5.56-2.5 5.56-5.55C21.97 6.01 17.46 2 12 2z"></path></symbol>
        <symbol id="i-side-dark" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"></rect><path d="M5 4h4v16H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" fill="currentColor"></path></symbol>
        <symbol id="i-trend-up" viewBox="0 0 24 24"><polyline points="4 16 10 10 14 14 20 8"></polyline><polyline points="15 8 20 8 20 13"></polyline></symbol>
        <symbol id="i-trend-down" viewBox="0 0 24 24"><polyline points="4 8 10 14 14 10 20 16"></polyline><polyline points="15 16 20 16 20 11"></polyline></symbol>
        <symbol id="i-trend-flat" viewBox="0 0 24 24"><line x1="4" y1="12" x2="20" y2="12"></line><polyline points="16 8 20 12 16 16"></polyline></symbol>
        <symbol id="i-side-theme" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"></rect><path d="M5 4h4v16H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" style="fill:var(--accent);stroke:var(--accent)"></path></symbol>
        <symbol id="i-side-light" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"></rect><line x1="9" y1="4" x2="9" y2="20"></line></symbol>
        <symbol id="i-rows" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"></rect><line x1="3" y1="9.5" x2="21" y2="9.5"></line><line x1="3" y1="14.5" x2="21" y2="14.5"></line></symbol>
        <symbol id="i-rows-tight" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"></rect><line x1="3" y1="8" x2="21" y2="8"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="16" x2="21" y2="16"></line></symbol>
        <symbol id="i-more" viewBox="0 0 24 24"><circle cx="5" cy="12" r="1"></circle><circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle></symbol>
        <symbol id="i-megaphone" viewBox="0 0 24 24"><path d="m3 11 18-5v12L3 14v-3z"></path><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"></path></symbol>
        <symbol id="i-calendar" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></symbol>
        <symbol id="i-archive" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="5" rx="1"></rect><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"></path><line x1="10" y1="12" x2="14" y2="12"></line></symbol>
        <symbol id="i-globe" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></symbol>
        <symbol id="i-play" viewBox="0 0 24 24"><polygon points="6 3 20 12 6 21 6 3"></polygon></symbol>
        <symbol id="i-pin" viewBox="0 0 24 24"><line x1="12" y1="17" x2="12" y2="22"></line><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"></path></symbol>
        <symbol id="i-history" viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path><path d="M3 3v5h5"></path><path d="M12 7v5l4 2"></path></symbol>
        <symbol id="i-target" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle></symbol>
        <symbol id="i-flame" viewBox="0 0 24 24"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z"></path></symbol>
        <symbol id="i-funnel" viewBox="0 0 24 24"><path d="M3 4h18l-7 8.5V19l-4 2v-8.5Z"></path></symbol>
        <symbol id="i-server" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="8" rx="2"></rect><rect x="2" y="13" width="20" height="8" rx="2"></rect><line x1="6" y1="7" x2="6.01" y2="7"></line><line x1="6" y1="17" x2="6.01" y2="17"></line></symbol>
        <symbol id="i-grip" viewBox="0 0 24 24"><circle cx="9" cy="6" r="1"></circle><circle cx="15" cy="6" r="1"></circle><circle cx="9" cy="12" r="1"></circle><circle cx="15" cy="12" r="1"></circle><circle cx="9" cy="18" r="1"></circle><circle cx="15" cy="18" r="1"></circle></symbol>
        <symbol id="i-resize" viewBox="0 0 24 24"><path d="M15 3h6v6"></path><path d="M9 21H3v-6"></path><path d="M21 3l-7 7"></path><path d="M3 21l7-7"></path></symbol>
        <symbol id="i-pie" viewBox="0 0 24 24"><path d="M21.21 15.89A10 10 0 1 1 8 2.83"></path><path d="M22 12A10 10 0 0 0 12 2v10z"></path></symbol>
        <symbol id="i-note" viewBox="0 0 24 24"><path d="M15.5 3H5a2 2 0 0 0-2 2v14c0 1.1.9 2 2 2h14a2 2 0 0 0 2-2V8.5L15.5 3Z"></path><path d="M15 3v6h6"></path><line x1="7" y1="13" x2="13" y2="13"></line><line x1="7" y1="17" x2="11" y2="17"></line></symbol>
    </svg>

    
</template>

<script setup>
defineProps({ m: Object, c: Object });
</script>
