import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  base: mode === 'production' ? '/peachwuhu/' : '/',
  server: {
    port: 8081,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:18000', changeOrigin: true },
      '/media': { target: 'http://127.0.0.1:18000', changeOrigin: true }
    }
  }
}))
