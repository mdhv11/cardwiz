import { createTheme } from '@mui/material/styles';

const theme = createTheme({
    palette: {
        mode: 'dark', // Default to dark mode for "Deep Blues" aesthetic
        primary: {
            main: '#0A1929', // Deep Blue
            light: '#132F4C',
            dark: '#050B14',
            contrastText: '#ffffff',
        },
        secondary: {
            main: '#00C853', // Success Green
            light: '#69F0AE',
            dark: '#009624',
            contrastText: '#000000',
        },
        background: {
            default: '#0A1929',
            paper: '#132F4C',
        },
        text: {
            primary: '#ffffff',
            secondary: '#B2BAC2',
        },
        action: {
            hover: 'rgba(255, 255, 255, 0.08)',
        },
    },
    typography: {
        fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
        h1: {
            fontWeight: 700,
            fontSize: '2.5rem',
        },
        h2: {
            fontWeight: 600,
            fontSize: '2rem',
        },
        h6: {
            fontWeight: 600,
        },
        button: {
            textTransform: 'none', // Remove uppercase transformation
            fontWeight: 600,
        },
    },
    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    borderRadius: 8,
                    padding: '8px 16px',
                },
                containedPrimary: {
                    background: 'linear-gradient(45deg, #0A1929 30%, #132F4C 90%)',
                    boxShadow: '0 3px 5px 2px rgba(10, 25, 41, .3)',
                },
                containedSecondary: {
                    background: 'linear-gradient(45deg, #00C853 30%, #69F0AE 90%)',
                }
            },
        },
        MuiCard: {
            styleOverrides: {
                root: {
                    borderRadius: 16,
                    backgroundImage: 'none', // Reset default paper gradient if any
                    backgroundColor: '#132F4C', // Ensure background color matches paper
                    boxShadow: '0 8px 16px 0 rgba(0,0,0,0.2)',
                },
            },
        },
        MuiTextField: {
            styleOverrides: {
                root: {
                    '& .MuiOutlinedInput-root': {
                        borderRadius: 8,
                    },
                },
            },
        },
    },
});

export default theme;
