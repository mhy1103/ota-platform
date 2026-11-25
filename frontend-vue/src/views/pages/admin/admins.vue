<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'admins'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'管理账号'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>管理账号</h1>
                <p>成员只看得到分配给 TA 的 App，每个 App 单独选权限</p>
            </div></div>
            <div class="page-actions">
                <button class="btn" type="button" data-admin-new data-hash-click="new"><svg class="i"><use href="#i-plus"></use></svg>新建成员</button>
            </div>
        </div>

        <template v-for="L_s in [m.summary]"><section class="metrics">
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-user-cog"></use></svg></span>管理员</div>
                <div class="metric-value"><span>{{ $h.s($h.z(L_s, 'total')) }}</span><small>位</small></div>
                <div class="metric-foot">{{ $h.s($h.cat('超级管理员 ', $h.z(L_s, 'owners'), ' · 成员 ', $h.z(L_s, 'members'))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-shield"></use></svg></span>两步验证</div>
                <div class="metric-value"><span>{{ $h.s($h.z(L_s, 'tfa')) }}</span><small>{{ $h.s($h.cat('/ ', $h.z(L_s, 'total'))) }}</small></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.eq($h.z(L_s, 'tfa'), $h.z(L_s, 'total'))) ? '全部开启了' : $h.cat('还有 ', $h.sub($h.z(L_s, 'total'), $h.z(L_s, 'tfa')), ' 位没开'))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-clock"></use></svg></span>近 7 天登录过</div>
                <div class="metric-value"><span>{{ $h.s($h.z(L_s, 'recent')) }}</span><small>位</small></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.gt($h.z(L_s, 'never'), 0)) ? $h.cat($h.z(L_s, 'never'), ' 位还没登录过') : '每位都登录过')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-lock"></use></svg></span>停用</div>
                <div class="metric-value"><span>{{ $h.s($h.z(L_s, 'off')) }}</span><small>位</small></div>
                <div class="metric-foot">停用的账号不能登录，操作日志保留</div>
            </div>
        </section></template>

        <section class="card section-gap" data-guide="adm-table">
            <div class="toolbar">
                <div class="seg seg-sm" data-admin-filter>
                    <button type="button" class="on" data-f="all">全部 <b>{{ $h.s($h.lists.size(m.rows)) }}</b></button>
                    <button type="button" data-f="member">成员</button>
                    <button type="button" data-f="off">停用</button>
                </div>
                <label class="search"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索名字、账号或 App" aria-label="搜索管理员" data-admin-search></label>
            </div>
            <div class="adm-grid" id="admin-list">
                <template v-for="[L_r, L_rStat] in $h.each(m.rows)"><template v-if="L_rStat.index">
                </template><template v-for="L_u in [$h.z(L_r, 'user')]"><template v-for="L_me in [($h.t(!$h.eq(m.me, null)) &amp;&amp; $h.t($h.eq($h.z(m.me, 'id'), $h.p(L_u, 'id'))))]"><article :class="['adm-card', $h.av(($h.t($h.p(L_u, 'enabled')) ? null : 'is-off'))]" :data-kind.attr="$h.av(($h.t($h.p(L_u, 'owner')) ? 'owner' : 'member'))" :data-on.attr="$h.av(($h.t($h.p(L_u, 'enabled')) ? '1' : '0'))" :data-hash-target.attr="$h.av($h.add('admin-', $h.p(L_u, 'id')))">
                    <div class="adm-head">
                        <span :class="['who-ava', $h.av(($h.t($h.p(L_u, 'owner')) ? 'owner' : null))]">{{ $h.s($h.strings.substring($h.p(L_u, 'name'), 0, 1)) }}</span>
                        <div class="adm-name">
                            <b><span>{{ $h.s($h.p(L_u, 'name')) }}</span><span class="me-tag" v-if="$h.t(L_me)">我</span><span class="tfa-mark" title="已开启两步验证" aria-label="已开启两步验证" v-if="$h.t($h.p(L_u, 'totpEnabled'))"><svg class="i"><use href="#i-shield"></use></svg></span></b>
                            <small class="mono">{{ $h.s($h.p(L_u, 'username')) }}</small>
                        </div>
                        <span :class="['role-badge', $h.av(($h.t($h.p(L_u, 'owner')) ? '' : 'member'))]">{{ $h.s(($h.t($h.p(L_u, 'owner')) ? '超级管理员' : '成员')) }}</span>
                    </div>
                    <div class="adm-body">
                        <div class="adm-label">能操作</div>
                        <div class="adm-all" v-if="$h.t($h.p(L_u, 'owner'))"><svg class="i i-sm"><use href="#i-check"></use></svg>全部 App · 全部权限，还能管成员和系统配置</div>
                        <div class="adm-none" v-if="$h.t(($h.t(!$h.t($h.p(L_u, 'owner'))) &amp;&amp; $h.t($h.lists.isEmpty($h.z(L_r, 'grants')))))">{{ $h.s(($h.t(($h.t($h.p(L_u, 'manageAgents')) &amp;&amp; $h.t($h.p(L_u, 'manageCodes')))) ? '没有分配 App，只能管理口令和代理' : ($h.t($h.p(L_u, 'manageCodes')) ? '没有分配 App，只能管理口令' : ($h.t($h.p(L_u, 'manageAgents')) ? '没有分配 App，只能管理代理' : '还没有分配 App，登录后什么都看不到')))) }}</div>
                        <template v-for="L_groups in [$h.z(L_r, 'groups')]">
                            <template v-for="[L_g, L_it] in $h.each(L_groups)"><template v-if="L_it.index">
                            </template><div class="grant-line" :title.attr="$h.av($h.add($h.add($h.z(L_g, 'summary'), '：'), $h.z(L_g, 'names')))" v-if="$h.t($h.lt($h.p(L_it, 'index'), 4))">
                                <span class="grant-perm">{{ $h.s($h.z(L_g, 'summary')) }}</span>
                                <span class="grant-apps">{{ $h.s($h.z(L_g, 'preview')) }}</span>
                                <span class="grant-more" v-if="$h.t($h.gt($h.z(L_g, 'more'), 0))">{{ $h.s($h.add('+', $h.z(L_g, 'more'))) }}</span>
                            </div></template>
                            <div class="muted-line" v-if="$h.t(($h.t($h.gt($h.lists.size(L_groups), 4)) || $h.t($h.gt($h.lists.size($h.z(L_r, 'grants')), 3))))">{{ $h.s($h.add($h.add($h.add('共 ', $h.lists.size($h.z(L_r, 'grants'))), ' 个 App'), ($h.t($h.gt($h.lists.size(L_groups), 4)) ? $h.add($h.add('，另有 ', $h.sub($h.lists.size(L_groups), 4)), ' 种权限组合，点编辑查看') : ''))) }}</div>
                        </template>
                    </div>
                    <div class="adm-facts">
                        <span :class="$h.av(($h.t(($h.t($h.p(L_u, 'owner')) || $h.t($h.p(L_u, 'manageCodes')))) ? null : 'off'))"><svg class="i i-sm"><use href="#i-key"></use></svg>{{ $h.s(($h.t(($h.t($h.p(L_u, 'owner')) || $h.t($h.p(L_u, 'manageCodes')))) ? '可以管理口令' : '不管口令')) }}</span>
                        <span :class="$h.av(($h.t(($h.t($h.p(L_u, 'owner')) || $h.t($h.p(L_u, 'manageAgents')))) ? null : 'off'))"><svg class="i i-sm"><use href="#i-users"></use></svg>{{ $h.s(($h.t(($h.t($h.p(L_u, 'owner')) || $h.t($h.p(L_u, 'manageAgents')))) ? '可以管理代理' : '不管代理')) }}</span>
                        <span :class="$h.av(($h.t($h.p(L_u, 'totpEnabled')) ? null : 'off'))"><svg class="i i-sm"><use href="#i-shield"></use></svg>{{ $h.s(($h.t($h.p(L_u, 'totpEnabled')) ? '两步验证已开' : '没开两步验证')) }}</span>
                        <span :title.attr="$h.av($h.ui.full($h.p(L_u, 'lastLoginAt')))"><svg class="i i-sm"><use href="#i-clock"></use></svg>{{ $h.s(($h.t(!$h.eq($h.p(L_u, 'lastLoginAt'), null)) ? $h.cat($h.ui.ago($h.p(L_u, 'lastLoginAt')), ' 登录') : '从未登录')) }}</span>
                    </div>
                    <div class="adm-foot">
                        <span :class="['badge', $h.av(($h.t($h.p(L_u, 'enabled')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(L_u, 'enabled')) ? '启用' : '停用')) }}</span></span>
                        <span class="muted small adm-remark" :title.attr="$h.av($h.p(L_u, 'remark'))" v-if="!$h.t($h.strings.isEmpty($h.p(L_u, 'remark')))">{{ $h.s($h.p(L_u, 'remark')) }}</span>
                        <span class="grow"></span>
                        <button class="btn btn-secondary btn-sm" type="button" data-admin-edit :data-id.attr="$h.av($h.p(L_u, 'id'))" :data-name.attr="$h.av($h.p(L_u, 'name'))" :data-username.attr="$h.av($h.p(L_u, 'username'))" :data-owner.attr="$h.av($h.p(L_u, 'owner'))" :data-me.attr="$h.av(L_me)" :data-agents.attr="$h.av($h.p(L_u, 'manageAgents'))" :data-codes.attr="$h.av($h.p(L_u, 'manageCodes'))" :data-enabled.attr="$h.av($h.p(L_u, 'enabled'))" :data-remark.attr="$h.av($h.p(L_u, 'remark'))" :data-grants.attr="$h.av($h.z(L_r, 'grantsJson'))"><svg class="i i-sm"><use href="#i-edit"></use></svg>编辑</button>
                        <div class="row-menu" data-pop>
                            <button class="icon-btn" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                            <div class="menu menu-float" data-pop-menu hidden>
                                <button class="menu-item" type="button" :data-copy.attr="$h.av($h.cat('登录地址：{origin}/login\n账号：', $h.p(L_u, 'username')))" data-copy-label="已复制登录信息"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制登录信息</button>
                                <a class="menu-item" :href.attr="$h.ak($h.ui.url('/admin/logs', 'q', $h.p(L_u, 'name')))"><svg class="i i-sm"><use href="#i-history"></use></svg>看 TA 的操作日志</a>
                                <form method="post" :action.attr="$h.ak($h.link('/admin/admins/{id}/toggle', [['id', $h.p(L_u, 'id')]]))" :data-confirm.attr="$h.av(($h.t($h.p(L_u, 'enabled')) ? $h.cat('停用 ', $h.p(L_u, 'name'), '？') : null))" data-confirm-body="停用后 TA 不能登录，已登录的后台会马上退出。随时可以再启用，权限不变。" data-confirm-ok="停用" data-confirm-danger v-if="!$h.t(L_me)">
                                    <button class="menu-item" type="submit"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.p(L_u, 'enabled')) ? '#i-lock' : '#i-check'))"></use></svg>{{ $h.s(($h.t($h.p(L_u, 'enabled')) ? '停用' : '启用')) }}</button>
                                </form>
                                <form method="post" :action.attr="$h.ak($h.link('/admin/admins/{id}/signout', [['id', $h.p(L_u, 'id')]]))" :data-confirm.attr="$h.av($h.cat('让 ', $h.p(L_u, 'name'), ' 退出所有设备？'))" data-confirm-body="TA 已登录的后台都会回到登录页，密码不变，重新登录就行。电脑借给了别人、怀疑密码泄露时用。" data-confirm-ok="退出所有设备" v-if="!$h.t(L_me)">
                                    <button class="menu-item" type="submit"><svg class="i i-sm"><use href="#i-logout"></use></svg>退出所有设备</button>
                                </form>
                                <form method="post" :action.attr="$h.ak($h.link('/admin/admins/{id}/2fa/reset', [['id', $h.p(L_u, 'id')]]))" :data-confirm.attr="$h.av($h.cat('重置 ', $h.p(L_u, 'name'), ' 的两步验证？'))" data-confirm-body="TA 的手机丢了或换了时用。重置后 TA 只用密码就能登录，建议登录后在「我的账号」里重新开启。" data-confirm-ok="重置" v-if="$h.t(($h.t($h.p(L_u, 'totpEnabled')) &amp;&amp; !$h.t(L_me)))">
                                    <button class="menu-item" type="submit"><svg class="i i-sm"><use href="#i-shield"></use></svg>重置两步验证</button>
                                </form>
                                <form method="post" v-if="!$h.t(L_me)" :action.attr="$h.ak($h.link('/admin/admins/{id}/delete', [['id', $h.p(L_u, 'id')]]))" :data-confirm.attr="$h.av($h.cat('删除管理员 ', $h.p(L_u, 'name'), '？'))" data-confirm-body="删除后不能再登录，已登录的会马上退出，操作日志保留。" data-confirm-ok="删除" data-confirm-danger>
                                    <button class="menu-item danger" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除管理员</button>
                                </form>
                            </div>
                        </div>
                    </div>
                </article></template></template></template>
                <button class="app-new-tile adm-new-tile" type="button" data-admin-new>
                    <span class="ant-ic"><svg class="i"><use href="#i-plus"></use></svg></span>
                    <b>新建成员</b>
                    <small>给同事开账号，按 App 勾选能做什么</small>
                </button>
            </div>
            <div class="list-none" data-admin-none hidden>没有匹配的管理员</div>
        </section>

        <section class="card">
            <div class="card-head"><h2>权限说明</h2><span class="muted small">成员在每个 App 上有授权就能查看，下面这些要单独勾选</span></div>
            <div class="perm-tiles">
                <div class="pt"><span class="pt-ic"><svg class="i"><use href="#i-eye"></use></svg></span><div><b>查看</b><small>有授权就有：概览、版本、设备统计、签名状态、App 设置（密钥不显示），这个 App 的激活记录和操作日志，还能看口令列表</small></div></div>
                <template v-for="[L_p, L_pStat] in $h.each(m.perms)"><template v-if="L_pStat.index">
                </template><div class="pt"><span class="pt-ic"><svg class="i"><use :href.attr="$h.av(($h.t($h.eq($h.p(L_p, 'key'), 'release')) ? '#i-layers' : ($h.t($h.eq($h.p(L_p, 'key'), 'mode')) ? '#i-refresh' : ($h.t($h.eq($h.p(L_p, 'key'), 'settings')) ? '#i-sliders' : '#i-shield'))))"></use></svg></span><div><b>{{ $h.s($h.p(L_p, 'label')) }}</b><small>{{ $h.s($h.p(L_p, 'desc')) }}</small></div></div></template>
                <div class="pt"><span class="pt-ic"><svg class="i"><use href="#i-key"></use></svg></span><div><b>口令与激活</b><small>新建、编辑、删除、导入口令，导出激活记录，同意代理的口令申请（口令所有 App 通用）</small></div></div>
                <div class="pt"><span class="pt-ic"><svg class="i"><use href="#i-users"></use></svg></span><div><b>管理代理</b><small>新建、编辑、停用代理，审核口令申请，设置扣量（代理所有 App 共用）</small></div></div>
                <div class="pt pt-owner"><span class="pt-ic"><svg class="i"><use href="#i-lock"></use></svg></span><div><b>只有超级管理员</b><small>超级管理员只有一个。新建、删除 App，从备份新建 App，管理账号，管理令牌，系统配置（默认外观、登录安全、存储），清空日志。外观每个人可以自己选</small></div></div>
            </div>
        </section>

        <dialog id="admin-dialog" class="xl">
            <form method="post" :action.attr="$h.ak('/admin/admins/create')" id="admin-form" autocomplete="off" :data-create.attr="$h.av('/admin/admins/create')">
                <div class="dlg-head">
                    <h3 data-role="title">新建成员</h3>
                    <p>成员只能看到勾选的 App。权限改了立即生效，不用重新登录。</p>
                </div>
                <div class="dlg-body scroll">
                    <div class="row-2">
                        <div class="field">
                            <label for="ad-name">名字</label>
                            <input class="input" id="ad-name" name="name" maxlength="50" placeholder="后台显示的名字，例如 张三">
                        </div>
                        <div class="field">
                            <label for="ad-username">登录账号</label>
                            <input class="input mono" id="ad-username" name="username" required maxlength="32" pattern="[A-Za-z0-9_.\-]{3,32}" placeholder="3–32 位字母、数字或 _ . -">
                        </div>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="ad-password">登录密码</label>
                            <input class="input" id="ad-password" name="password" type="password" minlength="8" autocomplete="new-password" placeholder="至少 8 位">
                            <span class="hint" data-role="pw-hint">至少 8 位，建好后把账号和密码告诉 TA</span>
                            <label class="check" style="margin-top:6px"><input type="checkbox" name="forceChange" :value.attr="'true'" :checked.attr="''">下次登录时要求修改密码</label>
                        </div>
                        <div class="field">
                            <label for="ad-remark">备注</label>
                            <input class="input" id="ad-remark" name="remark" maxlength="200" placeholder="可不填，例如 负责发版">
                        </div>
                    </div>
                    <input type="hidden" name="owner" :value.attr="'false'" data-role="owner">
                    <div class="owner-note" data-role="owner-note" hidden>
                        <span class="owner-ic"><svg class="i"><use href="#i-shield"></use></svg></span>
                        <div>
                            <b>超级管理员</b>
                            <small data-role="owner-text">所有 App、全部权限，还能管理账号和系统配置。超级管理员只能有一个</small>
                            <label class="check" data-role="demote" hidden><input type="checkbox" data-role="demote-box">改成成员，按下面勾选的 App 和权限</label>
                        </div>
                    </div>
                    <div data-role="member">
                        <div class="field">
                            <label>App 权限</label>
                            <div class="perm-tools" v-if="$h.t($h.gt($h.lists.size(m.allApps), 1))">
                                <label class="search">
                                    <svg class="i"><use href="#i-search"></use></svg>
                                    <input class="input" type="search" data-role="perm-search" placeholder="搜索 App 名称或标识" aria-label="搜索 App">
                                </label>
                                <div class="seg" data-role="perm-filter">
                                    <a href="#" class="on" data-filter="all">全部</a>
                                    <a href="#" data-filter="on">已勾选</a>
                                </div>
                                <span class="muted small" data-role="perm-count"></span>
                            </div>
                            <div class="perm-presets">
                                <span>一键勾选（作用于下面列出的 App）：</span>
                                <button class="btn btn-secondary btn-sm" type="button" data-preset="view">全部只读</button>
                                <button class="btn btn-secondary btn-sm" type="button" data-preset="ops">运营（切换 A/B 面）</button>
                                <button class="btn btn-secondary btn-sm" type="button" data-preset="dev">发版（发布版本）</button>
                                <button class="btn btn-secondary btn-sm" type="button" data-preset="all">全部权限</button>
                                <button class="btn btn-ghost btn-sm" type="button" data-preset="none">清空</button>
                            </div>
                            <div class="perm-table">
                                <table>
                                    <thead><tr><th>App</th><th data-col="access" title="点击给列出的 App 全选或取消">可以访问</th><template v-for="[L_p, L_pStat] in $h.each(m.perms)"><th :data-col.attr="$h.av($h.p(L_p, 'key'))" :title.attr="$h.av($h.add($h.p(L_p, 'desc'), '。点击给列出的 App 全选或取消'))">{{ $h.s($h.p(L_p, 'label')) }}</th></template></tr></thead>
                                    <tbody>
                                    <template v-for="[L_a, L_aStat] in $h.each(m.allApps)"><template v-if="L_aStat.index">
                                    </template><tr :data-app.attr="$h.av($h.p(L_a, 'id'))" class="off">
                                        <td class="two-line"><b>{{ $h.s($h.p(L_a, 'name')) }}</b><small class="mono">{{ $h.s($h.p(L_a, 'slug')) }}</small></td>
                                        <td><input type="checkbox" :name.attr="$h.ak($h.add('access_', $h.p(L_a, 'id')))" data-access :aria-label.attr="$h.av($h.add('可以访问 ', $h.p(L_a, 'name')))"></td>
                                        <template v-for="[L_p, L_pStat] in $h.each(m.perms)"><template v-if="L_pStat.index">
                                        </template><td><input type="checkbox" :name.attr="$h.ak($h.add('perm_', $h.p(L_a, 'id')))" :value.attr="$h.ak($h.p(L_p, 'key'))" data-perm :aria-label.attr="$h.av($h.add($h.add($h.p(L_a, 'name'), ' '), $h.p(L_p, 'label')))"></td></template>
                                    </tr></template>
                                    </tbody>
                                </table>
                                <div class="perm-empty" data-role="perm-empty" hidden>没有匹配的 App</div>
                            </div>
                            <span class="hint" v-if="$h.t($h.lists.isEmpty(m.allApps))">还没有 App，建好 App 后再来分配</span>
                        </div>
                        <div class="perm-globals">
                            <div class="pgl-h">不分 App 的权限</div>
                            <label class="check"><input type="checkbox" name="manageCodes" :value.attr="'true'">可以管理口令和激活记录（所有 App 通用）</label>
                            <label class="check"><input type="checkbox" name="manageAgents" :value.attr="'true'">可以管理代理（新建、编辑、扣量，所有 App 共用）</label>
                        </div>
                    </div>
                    <label class="check" data-role="enabled" style="margin-top:12px" hidden><input type="checkbox" name="enabled" :value.attr="'true'" :checked.attr="''">启用（停用后不能登录，已登录的立即退出）</label>
                    <input type="hidden" name="enabled" :value.attr="'false'" disabled data-role="enabled-off">
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit">新建</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('管理账号'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
