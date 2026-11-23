import { defineConfig } from 'vite';
import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { codeInspectorPlugin } from 'code-inspector-plugin';

export default defineConfig(({ command, isPreview }) => ({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  plugins: [
    ...(command === 'serve' && !isPreview
      ? [
          codeInspectorPlugin({
            bundler: 'vite',
            hotKeys: ['ctrlKey', 'altKey'],
            showSwitch: false,
          }),
        ]
      : []),
    react(),
    tailwindcss(),
  ],
  server: {
    port: 5173,
    strictPort: true,
    watch: { ignored: ['**/.artifacts/**'] },
  },
}));
