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
        ink: {
          50: "#f6f7f9", 100: "#eceef2", 200: "#d4d9e2", 300: "#aeb7c8",
          400: "#8290a8", 500: "#62718c", 600: "#4d5a73", 700: "#3f495d",
          800: "#373f4f", 900: "#222733", 950: "#14171f",
        },
      },
      fontFamily: { sans: ['"Noto Sans TC"', '"PingFang TC"', '"Microsoft JhengHei"', "system-ui", "sans-serif"] },
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
