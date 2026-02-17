import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
    Box,
    Typography,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    MenuItem,
    Grid
} from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import RewardCard from '../components/RewardCard';
import { fetchCards, addCard } from '../store/slices/cardSlice';

const Cards = () => {
    const dispatch = useDispatch();
    const { items: cards, loading } = useSelector((state) => state.cards);

    const [open, setOpen] = useState(false);
    const [newCard, setNewCard] = useState({
        cardName: '',
        issuer: '',
        network: 'VISA',
        lastFourDigits: ''
    });

    useEffect(() => {
        dispatch(fetchCards());
    }, [dispatch]);

    const handleOpen = () => setOpen(true);
    const handleClose = () => setOpen(false);

    const handleChange = (e) => {
        setNewCard({ ...newCard, [e.target.name]: e.target.value });
    };

    const handleSubmit = async () => {
        if (newCard.cardName && newCard.issuer && newCard.lastFourDigits) {
            await dispatch(addCard({ ...newCard, active: true }));
            handleClose();
            setNewCard({ cardName: '', issuer: '', network: 'VISA', lastFourDigits: '' });
        }
    };

    return (
        <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
                <Typography variant="h4" sx={{ fontWeight: 700 }}>My Wallet</Typography>
                <Button
                    variant="contained"
                    color="secondary"
                    startIcon={<AddIcon />}
                    onClick={handleOpen}
                >
                    Add Card
                </Button>
            </Box>

            <Grid container spacing={3}>
                {loading ? (
                    <Typography>Loading...</Typography>
                ) : cards.length > 0 ? (
                    cards.map((card) => (
                        <Grid size={{ xs: 12, sm: 6, md: 4 }} key={card.id}>
                            <RewardCard card={card} />
                        </Grid>
                    ))
                ) : (
                    <Typography width="100%" textAlign="center">No cards found. Add one to get started!</Typography>
                )}
            </Grid>

            {/* Add Card Dialog */}
            <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
                <DialogTitle>Add New Card</DialogTitle>
                <DialogContent>
                    <Box component="form" sx={{ mt: 1 }}>
                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            label="Card Name"
                            name="cardName"
                            placeholder="e.g. HDFC Millennia"
                            value={newCard.cardName}
                            onChange={handleChange}
                        />
                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            label="Issuer"
                            name="issuer"
                            placeholder="e.g. HDFC Bank"
                            value={newCard.issuer}
                            onChange={handleChange}
                        />
                        <Grid container spacing={2}>
                            <Grid size={{ xs: 6 }}>
                                <TextField
                                    margin="normal"
                                    select
                                    fullWidth
                                    label="Network"
                                    name="network"
                                    value={newCard.network}
                                    onChange={handleChange}
                                >
                                    <MenuItem value="VISA">VISA</MenuItem>
                                    <MenuItem value="MASTERCARD">MasterCard</MenuItem>
                                    <MenuItem value="RUPAY">RuPay</MenuItem>
                                    <MenuItem value="AMEX">Amex</MenuItem>
                                </TextField>
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField
                                    margin="normal"
                                    required
                                    fullWidth
                                    label="Last 4 Digits"
                                    name="lastFourDigits"
                                    type="text"
                                    inputProps={{ maxLength: 4 }}
                                    value={newCard.lastFourDigits}
                                    onChange={handleChange}
                                />
                            </Grid>
                        </Grid>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose} color="inherit">Cancel</Button>
                    <Button onClick={handleSubmit} variant="contained" color="secondary">Add Card</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default Cards;
