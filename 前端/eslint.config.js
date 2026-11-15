import js from "@eslint/js";
import pluginVue from "eslint-plugin-vue";
import globals from "globals";

// 只抓「會出錯」的問題（未使用變數、未定義、Vue 範本錯誤），不管排版風格：
// 專案現有程式碼的排版不統一，硬套風格規則會讓一個 PR 動到每一個檔案、掩蓋真正的變更。
export default [
  { ignores: ["dist/**", "node_modules/**", "src/data/europeMap.js"] },
  js.configs.recommended,
  ...pluginVue.configs["flat/essential"],
  {
    languageOptions: { globals: { ...globals.browser } },
    rules: {
      "no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrors: "none" }],
      "vue/multi-word-component-names": "off",
    },
  },
  {
    files: ["scripts/**", "*.config.js", "tests/**"],
    languageOptions: { globals: { ...globals.node } },
  },
];
