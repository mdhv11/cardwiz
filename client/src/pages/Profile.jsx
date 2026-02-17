import React, { useState } from 'react';
import { Box, Typography, Paper, Avatar, Button, TextField } from '@mui/material';
import { useSelector, useDispatch } from 'react-redux';
import { updateProfile } from '../store/slices/authSlice';

const Profile = () => {
    const dispatch = useDispatch();
    const { user, loading } = useSelector((state) => state.auth);
    const [isEditing, setIsEditing] = useState(false);
    const [formData, setFormData] = useState({
        firstName: '',
        lastName: '',
        email: ''
    });

    // Initialize form data when user data is available
    React.useEffect(() => {
        if (user) {
            setFormData({
                firstName: user.firstName || '',
                lastName: user.lastName || '',
                email: user.email || ''
            });
        }
    }, [user]);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async () => {
        await dispatch(updateProfile(formData));
        setIsEditing(false);
    };

    return (
        <Box maxWidth="md" sx={{ mx: 'auto' }}>
            <Typography variant="h4" sx={{ mb: 4, fontWeight: 700 }}>Profile</Typography>
            <Paper sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Avatar
                        src={user?.profileImageUrl}
                        sx={{ width: 100, height: 100, bgcolor: 'secondary.main', fontSize: '2.5rem' }}
                    >
                        {user?.firstName?.[0]}
                    </Avatar>
                    <Box>
                        {!isEditing ? (
                            <>
                                <Typography variant="h5">{user?.firstName} {user?.lastName}</Typography>
                                <Typography color="text.secondary">{user?.email}</Typography>
                                <Button
                                    variant="outlined"
                                    sx={{ mt: 2 }}
                                    onClick={() => setIsEditing(true)}
                                >
                                    Edit Profile
                                </Button>
                            </>
                        ) : (
                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 300 }}>
                                <Box sx={{ display: 'flex', gap: 2 }}>
                                    <TextField
                                        label="First Name"
                                        name="firstName"
                                        value={formData.firstName}
                                        onChange={handleChange}
                                        fullWidth
                                    />
                                    <TextField
                                        label="Last Name"
                                        name="lastName"
                                        value={formData.lastName}
                                        onChange={handleChange}
                                        fullWidth
                                    />
                                </Box>
                                <TextField
                                    label="Email"
                                    name="email"
                                    value={formData.email}
                                    disabled // Email usually not editable directly
                                    fullWidth
                                />
                                <Box sx={{ display: 'flex', gap: 2 }}>
                                    <Button
                                        variant="contained"
                                        onClick={handleSubmit}
                                        disabled={loading}
                                    >
                                        Save
                                    </Button>
                                    <Button
                                        variant="outlined"
                                        onClick={() => setIsEditing(false)}
                                    >
                                        Cancel
                                    </Button>
                                </Box>
                            </Box>
                        )}
                    </Box>
                </Box>
            </Paper>
        </Box>
    );
};

export default Profile;
