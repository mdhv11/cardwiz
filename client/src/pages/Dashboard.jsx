import React, { useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
    Box,
    Typography,
    Paper,
    Button,
    Grid,
    Chip
} from '@mui/material';
import { Add as AddIcon, ArrowForward as ArrowForwardIcon } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import RewardCard from '../components/RewardCard';
import ValidationDialogForm from '../components/ValidationDialogForm';
import axiosClient from '../api/axiosClient';
import { fetchCards } from '../store/slices/cardSlice';

const Dashboard = () => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { user } = useSelector((state) => state.auth);
    const { items: cards, loading } = useSelector((state) => state.cards);
    const [recentValidations, setRecentValidations] = useState([]);
    const [lastValidationResult, setLastValidationResult] = useState(null);
    const [dialogOpen, setDialogOpen] = useState(false);

    const loadRecentValidations = async () => {
        try {
            const response = await axiosClient.get('/transactions');
            setRecentValidations(Array.isArray(response.data) ? response.data : []);
        } catch (_) {
            setRecentValidations([]);
        }
    };

    useEffect(() => {
        dispatch(fetchCards());
        loadRecentValidations();
    }, [dispatch]);

    const sortedValidations = useMemo(() => {
        return [...recentValidations]
            .sort((a, b) => {
                const dateA = a.transactionDate ? new Date(a.transactionDate).getTime() : 0;
                const dateB = b.transactionDate ? new Date(b.transactionDate).getTime() : 0;
                return dateB - dateA;
            })
            .slice(0, 5);
    }, [recentValidations]);

    const formatAmount = (amount, currency) => {
        if (amount === null || amount === undefined || amount === '') {
            return `${currency || 'INR'} 0`;
        }
        return `${currency || 'INR'} ${amount}`;
    };

    const resolveValidationStatus = (item) => {
        const status = item?.validationStatus || item?.validation_status;
        if (status) {
            return status;
        }
        if (!item?.actualCardId || !item?.suggestedCardId) {
            return 'NOT_SET';
        }
        return item.actualCardId === item.suggestedCardId ? 'MATCHED' : 'MISSED';
    };

    const statusChipColor = (status) => {
        if (status === 'MATCHED') return 'success';
        if (status === 'MISSED') return 'warning';
        return 'default';
    };

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
            {lastValidationResult?.recommendation && (
                <Paper sx={{ p: 2, mb: 3, border: '1px solid rgba(255,255,255,0.08)' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Latest Validation Processed
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Suggested card: {lastValidationResult.recommendation?.best_card?.name || lastValidationResult.recommendation?.bestOption?.cardName || 'Unknown'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                        Routing: {lastValidationResult.recommendation?.routing_mode || 'N/A'} ({lastValidationResult.recommendation?.routing_reason || 'N/A'})
                    </Typography>
                    <Box sx={{ mt: 1 }}>
                        <Chip
                            size="small"
                            label={`Validation: ${resolveValidationStatus(lastValidationResult.transaction || {})}`}
                            color={statusChipColor(resolveValidationStatus(lastValidationResult.transaction || {}))}
                        />
                    </Box>
                </Paper>
            )}

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
                    <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="h6">Recent Validations</Typography>
                        <Button size="small" onClick={() => setDialogOpen(true)}>Add</Button>
                    </Box>
                    <Paper sx={{ p: 0, overflow: 'hidden' }}>
                        {sortedValidations.length === 0 && (
                            <Box sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary">
                                    No validations yet. Add one to personalize recommendations.
                                </Typography>
                            </Box>
                        )}
                        {sortedValidations.map((item, index) => (
                            <Box
                                key={item.id}
                                sx={{
                                    p: 2,
                                    borderBottom: index !== sortedValidations.length - 1 ? '1px solid rgba(255,255,255,0.1)' : 'none',
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    '&:hover': { bgcolor: 'rgba(255,255,255,0.05)' }
                                }}
                            >
                                <Box>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{item.merchant}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {item.transactionDate || 'Unknown date'}
                                    </Typography>
                                </Box>
                                <Box sx={{ textAlign: 'right' }}>
                                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                        {formatAmount(item.amount, item.currency)}
                                    </Typography>
                                    <Typography variant="caption" color="secondary.main">
                                        {(item.category || 'general').toUpperCase()}
                                    </Typography>
                                    <Box sx={{ mt: 0.5 }}>
                                        <Chip
                                            size="small"
                                            label={resolveValidationStatus(item)}
                                            color={statusChipColor(resolveValidationStatus(item))}
                                            sx={{ fontSize: 10, height: 20 }}
                                        />
                                    </Box>
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
            <ValidationDialogForm
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                cards={cards}
                onSaved={(payload) => {
                    setLastValidationResult(payload);
                    loadRecentValidations();
                }}
            />
        </Box>
    );
};

export default Dashboard;
