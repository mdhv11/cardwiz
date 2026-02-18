import React, { useMemo, useState } from 'react';
import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    TextField,
    Typography
} from '@mui/material';
import axiosClient from '../api/axiosClient';

const currencyOptions = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD'];
const categoryOptions = ['general', 'grocery', 'dining', 'travel', 'fuel', 'online', 'shopping'];

const ValidationDialogForm = ({ open, onClose, onSaved, cards = [] }) => {
    const activeCards = useMemo(() => cards.filter((card) => card?.active), [cards]);
    const [form, setForm] = useState({
        merchant: '',
        amount: '',
        category: 'general',
        currency: 'INR',
        transactionDate: new Date().toISOString().slice(0, 10),
        actualCardId: ''
    });
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState('');

    const handleChange = (event) => {
        const { name, value } = event.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const resetAndClose = () => {
        setError('');
        onClose?.();
    };

    const handleSubmit = async () => {
        if (!form.merchant.trim() || !form.amount) {
            setError('Merchant and amount are required.');
            return;
        }
        setIsSubmitting(true);
        setError('');
        try {
            const payload = {
                merchant: form.merchant.trim(),
                amount: Number(form.amount),
                category: form.category,
                currency: form.currency,
                transactionDate: form.transactionDate,
                actualCardId: form.actualCardId ? Number(form.actualCardId) : null
            };
            const response = await axiosClient.post('/transactions/validate', payload);
            onSaved?.(response.data);
            setForm({
                merchant: '',
                amount: '',
                category: 'general',
                currency: 'INR',
                transactionDate: new Date().toISOString().slice(0, 10),
                actualCardId: ''
            });
            resetAndClose();
        } catch (submitError) {
            setError(
                submitError?.response?.data?.message
                || submitError?.response?.data?.detail
                || 'Could not process validation. Please try again.'
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog open={open} onClose={resetAndClose} fullWidth maxWidth="sm">
            <DialogTitle>Add Validation</DialogTitle>
            <DialogContent>
                <Box sx={{ display: 'grid', gap: 2, mt: 1 }}>
                    <TextField label="Merchant" name="merchant" value={form.merchant} onChange={handleChange} required fullWidth />
                    <TextField label="Amount" name="amount" type="number" value={form.amount} onChange={handleChange} required fullWidth />
                    <TextField select label="Category" name="category" value={form.category} onChange={handleChange} fullWidth>
                        {categoryOptions.map((category) => (
                            <MenuItem key={category} value={category}>{category}</MenuItem>
                        ))}
                    </TextField>
                    <TextField select label="Currency" name="currency" value={form.currency} onChange={handleChange} fullWidth>
                        {currencyOptions.map((currency) => (
                            <MenuItem key={currency} value={currency}>{currency}</MenuItem>
                        ))}
                    </TextField>
                    <TextField
                        select
                        label="Card Used (Optional)"
                        name="actualCardId"
                        value={form.actualCardId}
                        onChange={handleChange}
                        fullWidth
                    >
                        <MenuItem value="">Not selected</MenuItem>
                        {activeCards.map((card) => (
                            <MenuItem key={card.id} value={String(card.id)}>{card.cardName}</MenuItem>
                        ))}
                    </TextField>
                    <TextField
                        label="Date"
                        name="transactionDate"
                        type="date"
                        value={form.transactionDate}
                        onChange={handleChange}
                        InputLabelProps={{ shrink: true }}
                        fullWidth
                    />
                    {error && (
                        <Typography variant="body2" color="error">{error}</Typography>
                    )}
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={resetAndClose} disabled={isSubmitting}>Cancel</Button>
                <Button variant="contained" color="secondary" onClick={handleSubmit} disabled={isSubmitting}>
                    {isSubmitting ? 'Processing...' : 'Save & Process'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default ValidationDialogForm;
