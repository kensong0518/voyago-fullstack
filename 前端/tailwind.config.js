/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,js}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eefdf6", 100: "#d6f9e8", 200: "#aff0d3", 300: "#79e2b7",
          400: "#3fcd96", 500: "#16b27c", 600: "#0a9065", 700: "#097353",
          800: "#0b5b44", 900: "#0a4a39", 950: "#03291f",
        },
        // 地圖配色：海（底色）、國界線、深藍（文字與路線）
        sea: { DEFAULT: "#dbe5ec", line: "#c3ced8", deep: "#2b4a66" },
        navy: "#18324a",
        ink: {
          50: "#f6f7f9", 100: "#eceef2", 200: "#d4d9e2", 300: "#aeb7c8",
          400: "#8290a8", 500: "#62718c", 600: "#4d5a73", 700: "#3f495d",
          800: "#373f4f", 900: "#222733", 950: "#14171f",
        },
      },
      fontFamily: {
        sans: ['"Noto Sans TC"', '"PingFang TC"', '"Microsoft JhengHei"', "system-ui", "sans-serif"],
        // 標題、行程名稱：明體，像旅遊指南的書名；內文維持黑體
        serif: ['"Noto Serif TC"', '"Songti TC"', '"PMingLiU"', "serif"],
      },
      boxShadow: {
        card: "0 1px 2px rgba(20,23,31,.04), 0 12px 32px -12px rgba(20,23,31,.18)",
        lift: "0 8px 40px -12px rgba(10,144,101,.35)",
      },
      keyframes: { "fade-up": { "0%": { opacity: 0, transform: "translateY(12px)" }, "100%": { opacity: 1, transform: "translateY(0)" } } },
      animation: { "fade-up": "fade-up .5s ease-out both" },
    },
  },
  plugins: [],
};
