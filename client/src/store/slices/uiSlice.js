import { createSlice } from '@reduxjs/toolkit';

const initialState = {
    sidebarOpen: true,
    loading: false, // Global loading overlay if needed
    notification: {
        open: false,
        message: '',
        severity: 'info', // 'success', 'info', 'warning', 'error'
    },
};

const uiSlice = createSlice({
    name: 'ui',
    initialState,
    reducers: {
        toggleSidebar: (state) => {
            state.sidebarOpen = !state.sidebarOpen;
        },
        setSidebarOpen: (state, action) => {
            state.sidebarOpen = action.payload;
        },
        showNotification: (state, action) => {
            state.notification = {
                open: true,
                message: action.payload.message,
                severity: action.payload.severity || 'info',
            };
        },
        hideNotification: (state) => {
            state.notification.open = false;
        },
        setLoading: (state, action) => {
            state.loading = action.payload;
        }
    },
});

export const { toggleSidebar, setSidebarOpen, showNotification, hideNotification, setLoading } = uiSlice.actions;

export default uiSlice.reducer;
