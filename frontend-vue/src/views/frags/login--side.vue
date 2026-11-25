<template><aside class="side">
        <div class="brand"><svg class="logo"><use href="#i-logo"></use></svg>{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
        <div>
            <div class="show" aria-hidden="true">
                <div class="glass g1">
                    <div class="dots"><i></i><i></i><i></i></div>
                    <div class="metric"><span class="ln hi"></span><span class="chip"></span></div>
                    <div class="bars">
                        <i style="--h:34%;--n:0"></i><i style="--h:46%;--n:1"></i><i style="--h:40%;--n:2"></i><i style="--h:58%;--n:3"></i>
                        <i style="--h:52%;--n:4"></i><i style="--h:66%;--n:5"></i><i style="--h:61%;--n:6"></i><i style="--h:74%;--n:7"></i>
                        <i style="--h:70%;--n:8"></i><i style="--h:83%;--n:9"></i><i style="--h:78%;--n:10"></i><i style="--h:96%;--n:11"></i>
                    </div>
                </div>
                <div class="glass g2">
                    <div class="row"><span class="dot"></span><span class="ln" style="width:70%"></span></div>
                    <div class="row"><span class="dot"></span><span class="ln" style="width:52%"></span></div>
                    <div class="row"><span class="dot w"></span><span class="ln" style="width:62%"></span></div>
                </div>
                <div class="glass g3">
                    <svg class="ring" viewBox="0 0 46 46"><circle class="track" cx="23" cy="23" r="19"></circle><circle class="val" cx="23" cy="23" r="19"></circle></svg>
                    <div style="flex:1"><span class="ln hi" style="width:70%;height:10px"></span><span class="ln" style="width:90%;margin-top:8px"></span></div>
                </div>
            </div>
            <div class="hero">
                <h2>热更新 · 口令 · 代理</h2>
                <p>管理员与代理统一登录入口</p>
            </div>
        </div>
        <div class="foot"><svg class="i"><use href="#i-lock"></use></svg>仅限授权人员访问</div>
    </aside></template>

<script setup>
defineProps({ m: Object, c: Object });
</script>
