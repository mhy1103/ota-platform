// 后台前端打包：输出到 src/main/resources/static/vue。登录页、安装页、错误页用的在 assets/（公开），
// 后台和代理端页面、后台样式在 app/（登录后才能取）。vite build --ssr 打一个服务端渲染包给测试用，不进 jar
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { whitespace } from './tools/whitespace.mjs';

const APP = ['/src/views/app.js', '/src/views/pages/admin/', '/src/views/pages/agent/', '/src/views/frags/admin-layout--',
    '/src/behaviors.js', '/src/behaviors/ui.js', '/src/behaviors/pages/'];

/** 登录页、安装页在登录之前就要跑，入口不能静态引用 app/ 下的文件（那里要登录才能取，安装前也取不到） */
function publicEntry() {
    return {
        name: 'ota-public-entry',
        generateBundle(options, bundle) {
            for (const chunk of Object.values(bundle)) {
                if (chunk.type !== 'chunk' || !chunk.isEntry) continue;
                const bad = chunk.imports.filter((f) => f.startsWith('app/'));
                if (bad.length) this.error('入口 ' + chunk.fileName + ' 静态引用了要登录才能取的 ' + bad.join('、'));
            }
        }
    };
}

export default defineConfig(({ isSsrBuild }) => ({
    base: '/vue/',
    plugins: [whitespace(), vue({ template: { compilerOptions: { whitespace: 'preserve', comments: false } } }), publicEntry()],
    define: {
        __VUE_OPTIONS_API__: 'false',
        __VUE_PROD_DEVTOOLS__: 'false',
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false'
    },
    build: isSsrBuild ? {
        outDir: 'dist-ssr',
        emptyOutDir: true,
        reportCompressedSize: false
    } : {
        outDir: '../src/main/resources/static/vue',
        emptyOutDir: true,
        manifest: true,
        modulePreload: { polyfill: false },
        target: ['chrome105', 'safari15.4', 'firefox110'],
        reportCompressedSize: false,
        chunkSizeWarningLimit: 1500,
        rollupOptions: {
            input: { main: 'src/main.js' },
            output: {
                manualChunks(id) {
                    if (APP.some((p) => id.includes(p))) return 'app';
                    if (id.includes('/node_modules/')) return 'vendor';
                    return undefined;
                },
                entryFileNames: 'assets/[name]-[hash].js',
                chunkFileNames: (c) => (c.name === 'app' ? 'app/' : 'assets/') + '[name]-[hash].js',
                assetFileNames: (a) => (/^ui\./.test((a.names && a.names[0]) || a.name || '') ? 'app/' : 'assets/') + '[name]-[hash][extname]'
            }
        }
    }
}));
