import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { fetchUserProfile } from '../store/slices/authSlice';
import { Box, CircularProgress } from '@mui/material';

const AuthGuard = ({ children }) => {
    const navigate = useNavigate();
    const location = useLocation();
    const dispatch = useDispatch();
    const { isAuthenticated, loading, user } = useSelector((state) => state.auth);

    useEffect(() => {
        if (!isAuthenticated) {
            navigate('/login', { replace: true, state: { from: location } });
        } else if (!user && !loading) {
            // Token exists but user profile not loaded (e.g. page refresh)
            dispatch(fetchUserProfile());
        }
    }, [isAuthenticated, navigate, location, user, loading, dispatch]);

    if (loading || (isAuthenticated && !user)) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <CircularProgress />
            </Box>
        );
    }

    return children;
};

export default AuthGuard;
