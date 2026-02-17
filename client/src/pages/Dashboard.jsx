import React, { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, Paper, Button, Grid } from '@mui/material';
import { Add as AddIcon, ArrowForward as ArrowForwardIcon } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import RewardCard from '../components/RewardCard';
import { fetchCards } from '../store/slices/cardSlice';

const Dashboard = () => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { user } = useSelector((state) => state.auth);
    const { items: cards, loading } = useSelector((state) => state.cards);

    useEffect(() => {
        dispatch(fetchCards());
    }, [dispatch]);

    // Mock data for recent activity
    const recentActivity = [
        { id: 1, merchant: 'Starbucks', amount: '₹450', date: 'Today', reward: '5% Cashback' },
        { id: 2, merchant: 'Amazon', amount: '₹1,200', date: 'Yesterday', reward: '5X Points' },
        { id: 3, merchant: 'Uber', amount: '₹350', date: 'Yesterday', reward: '3% Cashback' },
    ];

    return (
        <Box>
            <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 700 }}>
                        Welcome back, {user?.firstName || 'User'}!
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Here's your financial snapshot.
                    </Typography>
                </Box>
                <Button
                    variant="contained"
                    color="secondary"
                    startIcon={<AddIcon />}
                    onClick={() => navigate('/cards')}
                >
                    Add Card
                </Button>
            </Box>

            <Grid container spacing={4}>
                {/* Cards Section */}
                <Grid size={{ xs: 12, md: 8 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                        <Typography variant="h6">Your Cards</Typography>
                        <Button endIcon={<ArrowForwardIcon />} onClick={() => navigate('/cards')}>
                            View All
                        </Button>
                    </Box>

                    <Grid container spacing={2}>
                        {loading ? (
                            <Typography sx={{ p: 2 }}>Loading cards...</Typography>
                        ) : cards.length > 0 ? (
                            cards.slice(0, 2).map((card) => (
                                <Grid size={{ xs: 12, sm: 6 }} key={card.id}>
                                    <RewardCard card={card} />
                                </Grid>
                            ))
                        ) : (
                            <Grid size={{ xs: 12 }}>
                                <Paper sx={{ p: 4, textAlign: 'center', border: '1px dashed grey', bgcolor: 'transparent' }}>
                                    <Typography>No cards added yet.</Typography>
                                    <Button sx={{ mt: 1 }} onClick={() => navigate('/cards')}>Add your first card</Button>
                                </Paper>
                            </Grid>
                        )}
                        {/* Add a prompt card if few cards */}
                        {cards.length > 0 && cards.length < 3 && (
                            <Grid size={{ xs: 12, sm: 6 }}>
                                <Paper
                                    sx={{
                                        height: '100%',
                                        minHeight: 180,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        justifyContent: 'center',
                                        alignItems: 'center',
                                        border: '2px dashed rgba(255,255,255,0.1)',
                                        bgcolor: 'transparent',
                                        cursor: 'pointer',
                                        '&:hover': {
                                            borderColor: 'secondary.main',
                                            bgcolor: 'rgba(255,255,255,0.02)'
                                        }
                                    }}
                                    onClick={() => navigate('/cards')}
                                >
                                    <AddIcon sx={{ fontSize: 40, color: 'text.secondary', mb: 1 }} />
                                    <Typography color="text.secondary">Add New Card</Typography>
                                </Paper>
                            </Grid>
                        )}
                    </Grid>
                </Grid>

                {/* Recent Activity / Quick Actions */}
                <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="h6" sx={{ mb: 2 }}>Recent Validations</Typography>
                    <Paper sx={{ p: 0, overflow: 'hidden' }}>
                        {recentActivity.map((item, index) => (
                            <Box
                                key={item.id}
                                sx={{
                                    p: 2,
                                    borderBottom: index !== recentActivity.length - 1 ? '1px solid rgba(255,255,255,0.1)' : 'none',
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    '&:hover': { bgcolor: 'rgba(255,255,255,0.05)' }
                                }}
                            >
                                <Box>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{item.merchant}</Typography>
                                    <Typography variant="caption" color="text.secondary">{item.date}</Typography>
                                </Box>
                                <Box sx={{ textAlign: 'right' }}>
                                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.amount}</Typography>
                                    <Typography variant="caption" color="secondary.main">{item.reward}</Typography>
                                </Box>
                            </Box>
                        ))}
                        <Box sx={{ p: 2, textAlign: 'center', borderTop: '1px solid rgba(255,255,255,0.1)' }}>
                            <Button size="small" fullWidth onClick={() => navigate('/advisor')}>
                                Ask Smart Advisor
                            </Button>
                        </Box>
                    </Paper>
                </Grid>
            </Grid>
        </Box>
    );
};

export default Dashboard;
