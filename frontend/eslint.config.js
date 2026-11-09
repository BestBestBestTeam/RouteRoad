import js from '@eslint/js';
import globals from 'globals';
import hooks from 'eslint-plugin-react-hooks';
import refresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', '.artifacts', '.npm-cache'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': hooks, 'react-refresh': refresh },
    rules: {
      ...hooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  {
    files: ['src/components/**/*.{ts,tsx}', 'src/pages/**/*.{ts,tsx}', 'src/stores/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/api/mock', '@/api/mock/**'],
              message: 'Use the API facade; demo data belongs behind src/api/workdayApi.ts.',
            },
          ],
        },
      ],
    },
  },
  {
    files: [
      'src/components/ui/**/*.{ts,tsx}',
      'src/components/map/**/*.{ts,tsx}',
      'src/components/dispatcher/DaySummary.tsx',
      'src/components/dispatcher/ReassignDrawer.tsx',
    ],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/stores', '@/stores/**', '**/stores/**', '@/api', '@/api/**', '**/api/**'],
              message: 'Presentation components receive data and actions through props.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/api/**/*.{ts,tsx}', 'src/utils/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: [
                '@/stores/**',
                '**/stores/**',
                '@/components/**',
                '**/components/**',
                '@/pages/**',
                '**/pages/**',
                '@/hooks/**',
                '**/hooks/**',
                'react',
                'react-dom',
                'react-dom/**',
              ],
              message: 'Data adapters and pure utilities must not depend on React or UI state.',
            },
          ],
        },
      ],
    },
  },
  { files: ['*.js', '*.ts'], languageOptions: { globals: globals.node } },
);
