import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';
import store from './store';
import theme from './theme';

import MainLayout from './layouts/MainLayout';
import AuthGuard from './components/AuthGuard';

import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Cards from './pages/Cards';
import Advisor from './pages/Advisor';
import Profile from './pages/Profile';

const App = () => {
    return (
        <Provider store={store}>
            <ThemeProvider theme={theme}>
                <CssBaseline />
                <BrowserRouter>
                    <Routes>
                        <Route path="/login" element={<Login />} />
                        <Route path="/register" element={<Register />} />

                        <Route path="/" element={<AuthGuard><MainLayout /></AuthGuard>}>
                            <Route index element={<Navigate to="/dashboard" replace />} />
                            <Route path="dashboard" element={<Dashboard />} />
                            <Route path="cards" element={<Cards />} />
                            <Route path="advisor" element={<Advisor />} />
                            <Route path="profile" element={<Profile />} />
                        </Route>
                    </Routes>
                </BrowserRouter>
            </ThemeProvider>
        </Provider>
    );
};

export default App;
