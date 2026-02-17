import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import cardReducer from './slices/cardSlice';
import uiReducer from './slices/uiSlice';

const store = configureStore({
    reducer: {
        auth: authReducer,
        cards: cardReducer,
        ui: uiReducer,
    },
    devTools: process.env.NODE_ENV !== 'production',
});

export default store;
