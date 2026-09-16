/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                bgBase: '#0f1115',
                bgSurface: '#191c21',
                bgHover: '#23272f',
                borderC: '#2d333b',
                textMain: '#e6edf3',
                textMuted: '#7d8590',
                accent: '#2f81f7',
            }
        },
    },
    plugins: [],
}