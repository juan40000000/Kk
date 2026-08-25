/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Paleta oficial del Ads Manager (tomada de la UI real de Meta)
        meta: {
          blue: '#0866FF',
          blueAlt: '#1877F2',
          blueLink: '#0064E0',
          blueHover: '#EBF5FF',
          green: '#42B72A',
          greenDark: '#31A24C',
          yellow: '#F7B928',
          red: '#E41E3F',
          text: '#1c1e21',
          secondary: '#65676B',
          tertiary: '#8A8D91',
          border: '#DADDE1',
          borderLight: '#E4E6EB',
          header: '#F5F6F7',
          hover: '#F2F2F2',
          bg: '#FFFFFF',
          shell: '#F0F2F5',
          off: '#BCC0C4',
        },
      },
      fontFamily: {
        meta: [
          '-apple-system',
          'BlinkMacSystemFont',
          '"Segoe UI"',
          'Roboto',
          'Helvetica',
          'Arial',
          'sans-serif',
        ],
      },
      fontSize: {
        '2xs': ['11px', '13px'],
        xs: ['12px', '16px'],
        sm: ['13px', '17px'],
      },
    },
  },
  plugins: [],
};
