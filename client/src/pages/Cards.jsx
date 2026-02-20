import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
    Alert,
    Box,
    Button,
    Checkbox,
    CircularProgress,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControlLabel,
    FormGroup,
    Grid,
    MenuItem,
    Snackbar,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography
} from '@mui/material';
import {
    Add as AddIcon,
    CompareArrows as CompareArrowsIcon,
    UploadFile as UploadFileIcon
} from '@mui/icons-material';
import RewardCard from '../components/RewardCard';
import { fetchCards, addCard } from '../store/slices/cardSlice';
import axiosClient, { getApiErrorMessage } from '../api/axiosClient';

const SUPPORTED_CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD'];

const Cards = () => {
    const dispatch = useDispatch();
    const { items: cards, loading } = useSelector((state) => state.cards);

    const [open, setOpen] = useState(false);
    const [compareOpen, setCompareOpen] = useState(false);
    const [compareLoading, setCompareLoading] = useState(false);
    const [compareError, setCompareError] = useState('');
    const [compareResult, setCompareResult] = useState(null);

    const [knowledgeCoverage, setKnowledgeCoverage] = useState({});
    const [coverageLoading, setCoverageLoading] = useState(false);
    const [uploadingCardId, setUploadingCardId] = useState(null);
    const [pendingUploadCardId, setPendingUploadCardId] = useState(null);
    const [pollingJobCardId, setPollingJobCardId] = useState(null);
    const [toast, setToast] = useState({ open: false, severity: 'success', message: '' });

    const [newCard, setNewCard] = useState({
        cardName: '',
        issuer: '',
        network: 'VISA',
        lastFourDigits: ''
    });

    const [compareForm, setCompareForm] = useState({
        merchantName: '',
        category: 'general',
        transactionAmount: '10000',
        currency: 'INR'
    });
    const [selectedCardIds, setSelectedCardIds] = useState([]);

    const fileInputRef = useRef(null);

    const activeCards = useMemo(() => cards.filter((card) => card.active), [cards]);

    const loadCoverage = async () => {
        if (activeCards.length === 0) {
            setKnowledgeCoverage({});
            return;
        }
        setCoverageLoading(true);
        try {
            const response = await axiosClient.get('/cards/knowledge-coverage');
            setKnowledgeCoverage(response.data || {});
        } catch (_) {
            setKnowledgeCoverage({});
        } finally {
            setCoverageLoading(false);
        }
    };

    useEffect(() => {
        dispatch(fetchCards());
    }, [dispatch]);

    useEffect(() => {
        if (cards.length > 0) {
            loadCoverage();
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [cards.length]);

    useEffect(() => {
        if (!compareOpen) {
            return;
        }
        setSelectedCardIds((prev) => prev.filter((id) => activeCards.some((card) => card.id === id)));
    }, [activeCards, compareOpen]);

    const handleOpen = () => setOpen(true);
    const handleClose = () => setOpen(false);

    const handleOpenCompare = () => {
        setCompareOpen(true);
        setCompareError('');
        setCompareResult(null);
        setSelectedCardIds([]);
    };

    const handleCloseCompare = () => {
        if (compareLoading) {
            return;
        }
        setCompareOpen(false);
    };

    const handleChange = (e) => {
        setNewCard({ ...newCard, [e.target.name]: e.target.value });
    };

    const handleCompareInput = (e) => {
        setCompareForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
    };

    const handleToggleCard = (cardId) => {
        setSelectedCardIds((prev) => (
            prev.includes(cardId)
                ? prev.filter((id) => id !== cardId)
                : [...prev, cardId]
        ));
    };

    const handleSubmit = async () => {
        if (newCard.cardName && newCard.issuer && newCard.lastFourDigits) {
            await dispatch(addCard({ ...newCard, active: true }));
            handleClose();
            setNewCard({ cardName: '', issuer: '', network: 'VISA', lastFourDigits: '' });
        }
    };

    const handleRunCompare = async () => {
        setCompareError('');
        setCompareResult(null);

        if (selectedCardIds.length < 2) {
            setCompareError('Select at least 2 cards to compare.');
            return;
        }

        if (!compareForm.merchantName.trim()) {
            setCompareError('Enter a merchant or spending context for accurate comparison.');
            return;
        }

        setCompareLoading(true);
        try {
            const response = await axiosClient.post('/cards/recommendations', {
                merchantName: compareForm.merchantName,
                category: compareForm.category,
                transactionAmount: Number(compareForm.transactionAmount || 0),
                currency: compareForm.currency,
                contextNotes: 'cards_page_compare_flow',
                availableCardIds: selectedCardIds
            });
            setCompareResult(response.data || null);
        } catch (error) {
            setCompareError(getApiErrorMessage(error, 'Comparison failed. Please try again.'));
        } finally {
            setCompareLoading(false);
        }
    };

    const handleOpenCardUpload = (cardId) => {
        setPendingUploadCardId(cardId);
        fileInputRef.current?.click();
    };

    const pollDocumentJob = async (documentId, cardId) => {
        const startedAt = Date.now();
        const timeoutMs = 180000;
        const pollEveryMs = 2500;
        setPollingJobCardId(cardId);

        while (Date.now() - startedAt < timeoutMs) {
            await new Promise((resolve) => setTimeout(resolve, pollEveryMs));
            try {
                const response = await axiosClient.get(`/cards/documents/${documentId}/status`);
                const status = response.data?.status;
                if (status === 'COMPLETED') {
                    await dispatch(fetchCards());
                    await loadCoverage();
                    setToast({
                        open: true,
                        severity: 'success',
                        message: 'AI analysis completed. Smart features are now active for this card.'
                    });
                    setPollingJobCardId(null);
                    return;
                }
                if (status === 'FAILED') {
                    await dispatch(fetchCards());
                    setToast({
                        open: true,
                        severity: 'error',
                        message: response.data?.aiSummary || 'AI analysis failed for this document.'
                    });
                    setPollingJobCardId(null);
                    return;
                }
            } catch (_) {
                // Continue polling through transient errors.
            }
        }

        await dispatch(fetchCards());
        setToast({
            open: true,
            severity: 'warning',
            message: 'Still processing in background. Status will refresh on next visit.'
        });
        setPollingJobCardId(null);
    };

    const handleCardFileSelected = async (event) => {
        const file = event.target.files?.[0];
        const targetCardId = pendingUploadCardId;
        event.target.value = '';

        if (!file || !targetCardId) {
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('documentType', 'CARD_TNC');

        setUploadingCardId(targetCardId);
        try {
            const response = await axiosClient.post(`/cards/${targetCardId}/documents/analyze`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            await dispatch(fetchCards());
            await loadCoverage();
            setToast({
                open: true,
                severity: 'success',
                message: 'Document uploaded. AI analysis started in background.'
            });
            const documentId = response?.data?.documentId;
            if (documentId) {
                void pollDocumentJob(documentId, targetCardId);
            }
        } catch (error) {
            setToast({
                open: true,
                severity: 'error',
                message: getApiErrorMessage(error, 'Failed to upload card document.')
            });
        } finally {
            setUploadingCardId(null);
            setPendingUploadCardId(null);
        }
    };

    const selectedCardNameMap = useMemo(() => {
        return new Map(activeCards.map((card) => [card.id, card.cardName]));
    }, [activeCards]);

    const missingCardIds = Array.isArray(compareResult?.missingCardIds)
        ? compareResult.missingCardIds
        : (Array.isArray(compareResult?.missing_card_ids) ? compareResult.missing_card_ids : []);
    const hasSufficientData = compareResult?.hasSufficientData ?? compareResult?.has_sufficient_data ?? false;
    const bestCardPayload = compareResult?.bestCard || compareResult?.best_card;
    const comparisonTablePayload = Array.isArray(compareResult?.comparisonTable)
        ? compareResult.comparisonTable
        : (Array.isArray(compareResult?.comparison_table) ? compareResult.comparison_table : []);
    const transactionContextPayload = compareResult?.transactionContext || compareResult?.transaction_context;

    return (
        <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
                <Typography variant="h4" sx={{ fontWeight: 700 }}>My Wallet</Typography>
                <Box sx={{ display: 'flex', gap: 1.5 }}>
                    <Button
                        variant="outlined"
                        color="secondary"
                        startIcon={<CompareArrowsIcon />}
                        onClick={handleOpenCompare}
                        disabled={activeCards.length < 2}
                    >
                        Compare Cards
                    </Button>
                    <Button
                        variant="contained"
                        color="secondary"
                        startIcon={<AddIcon />}
                        onClick={handleOpen}
                    >
                        Add Card
                    </Button>
                </Box>
            </Box>

            <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.webp"
                onChange={handleCardFileSelected}
                style={{ display: 'none' }}
            />

            <Grid container spacing={3}>
                {loading ? (
                    <Typography>Loading...</Typography>
                ) : cards.length > 0 ? (
                    cards.map((card) => {
                        const isReadyByCoverage = Boolean(knowledgeCoverage?.[String(card.id)] || knowledgeCoverage?.[card.id]);
                        const docStatus = card.docStatus || 'NOT_UPLOADED';
                        const isReady = docStatus === 'COMPLETED' || isReadyByCoverage;
                        const isProcessing = docStatus === 'PROCESSING';
                        const isFailed = docStatus === 'FAILED';
                        const isUploadingThis = uploadingCardId === card.id;
                        const isPollingThis = pollingJobCardId === card.id;
                        return (
                            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={card.id}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.2 }}>
                                    <RewardCard card={card} />
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <Chip
                                            size="small"
                                            color={isReady ? 'success' : (isProcessing ? 'warning' : (isFailed ? 'error' : 'default'))}
                                            label={
                                                isReady
                                                    ? 'Knowledge Ready'
                                                    : isProcessing
                                                        ? 'AI Analyzing...'
                                                        : isFailed
                                                            ? 'Analysis Failed'
                                                            : (coverageLoading ? 'Checking knowledge...' : 'No Docs Indexed')
                                            }
                                        />
                                        <Button
                                            size="small"
                                            variant="outlined"
                                            color="secondary"
                                            startIcon={(isUploadingThis || isPollingThis) ? <CircularProgress size={14} color="inherit" /> : <UploadFileIcon />}
                                            disabled={isUploadingThis || isPollingThis}
                                            onClick={() => handleOpenCardUpload(card.id)}
                                        >
                                            {isUploadingThis ? 'Uploading...' : (isPollingThis ? 'Processing...' : 'Upload Docs')}
                                        </Button>
                                    </Box>
                                </Box>
                            </Grid>
                        );
                    })
                ) : (
                    <Typography width="100%" textAlign="center">No cards found. Add one to get started!</Typography>
                )}
            </Grid>

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

            <Dialog open={compareOpen} onClose={handleCloseCompare} fullWidth maxWidth="md">
                <DialogTitle>Compare Cards</DialogTitle>
                <DialogContent>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                        Select cards and provide spend context. If reward-rule documents are missing for selected cards,
                        we will ask you to upload statements/brochures first.
                    </Typography>
                    <Grid container spacing={2} sx={{ mb: 2 }}>
                        <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField
                                fullWidth
                                label="Merchant / Context"
                                name="merchantName"
                                placeholder="e.g. Shoppers Stop"
                                value={compareForm.merchantName}
                                onChange={handleCompareInput}
                                required
                            />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 3 }}>
                            <TextField
                                select
                                fullWidth
                                label="Category"
                                name="category"
                                value={compareForm.category}
                                onChange={handleCompareInput}
                            >
                                <MenuItem value="general">General</MenuItem>
                                <MenuItem value="grocery">Grocery</MenuItem>
                                <MenuItem value="dining">Dining</MenuItem>
                                <MenuItem value="travel">Travel</MenuItem>
                                <MenuItem value="fuel">Fuel</MenuItem>
                                <MenuItem value="online">Online</MenuItem>
                                <MenuItem value="apparel">Apparel</MenuItem>
                            </TextField>
                        </Grid>
                        <Grid size={{ xs: 12, sm: 3 }}>
                            <TextField
                                fullWidth
                                label="Spend Amount"
                                name="transactionAmount"
                                type="number"
                                value={compareForm.transactionAmount}
                                onChange={handleCompareInput}
                            />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 3 }}>
                            <TextField
                                select
                                fullWidth
                                label="Currency"
                                name="currency"
                                value={compareForm.currency}
                                onChange={handleCompareInput}
                            >
                                {SUPPORTED_CURRENCIES.map((currency) => (
                                    <MenuItem key={currency} value={currency}>{currency}</MenuItem>
                                ))}
                            </TextField>
                        </Grid>
                    </Grid>

                    <Typography variant="subtitle2" sx={{ mb: 1 }}>Select cards to compare</Typography>
                    <FormGroup>
                        {activeCards.map((card) => (
                            <FormControlLabel
                                key={card.id}
                                control={(
                                    <Checkbox
                                        checked={selectedCardIds.includes(card.id)}
                                        onChange={() => handleToggleCard(card.id)}
                                    />
                                )}
                                label={`${card.cardName} • ${card.issuer} • **** ${card.lastFourDigits}`}
                            />
                        ))}
                    </FormGroup>

                    {compareError && <Alert severity="error" sx={{ mt: 2 }}>{compareError}</Alert>}

                    {compareResult && (
                        <Box sx={{ mt: 3 }}>
                            <Divider sx={{ mb: 2 }} />

                            {!hasSufficientData && (
                                <Alert severity="warning" sx={{ mb: 2 }}>
                                    Not enough reward-rule data for selected cards.
                                    {missingCardIds.length > 0
                                        ? ` Missing: ${missingCardIds.map((id) => selectedCardNameMap.get(id) || `Card ${id}`).join(', ')}.`
                                        : ''}
                                    Upload statement/T&C documents in Smart Advisor or use Upload Docs in this page.
                                </Alert>
                            )}

                            {bestCardPayload && (
                                <Alert severity="success" sx={{ mb: 2 }}>
                                    Winner: {bestCardPayload.name} ({bestCardPayload?.rewards?.effectivePercentage?.toFixed?.(2) || bestCardPayload?.rewards?.effective_percentage?.toFixed?.(2) || bestCardPayload?.rewards?.effectivePercentage || bestCardPayload?.rewards?.effective_percentage}%).
                                    Estimated value: {bestCardPayload?.rewards?.valueUnit || bestCardPayload?.rewards?.value_unit} {bestCardPayload?.rewards?.estimatedValue || bestCardPayload?.rewards?.estimated_value}.
                                </Alert>
                            )}

                            {comparisonTablePayload.length > 0 && (
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>Card</TableCell>
                                                <TableCell align="right">Effective %</TableCell>
                                                <TableCell align="right">Estimated Value</TableCell>
                                                <TableCell>Verdict</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {comparisonTablePayload.map((row, index) => (
                                                <TableRow key={`${row.cardName || row.card_name}-${index}`}>
                                                    <TableCell>{row.cardName || row.card_name}</TableCell>
                                                    <TableCell align="right">{Number(row.effectivePercentage ?? row.effective_percentage ?? 0).toFixed(2)}%</TableCell>
                                                    <TableCell align="right">
                                                        {(transactionContextPayload?.currency || compareForm.currency)} {Number(row.estimatedValue ?? row.estimated_value ?? 0).toFixed(2)}
                                                    </TableCell>
                                                    <TableCell>{row.verdict}</TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            )}
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseCompare} color="inherit" disabled={compareLoading}>Close</Button>
                    <Button
                        onClick={handleRunCompare}
                        variant="contained"
                        color="secondary"
                        disabled={compareLoading}
                        startIcon={compareLoading ? <CircularProgress size={16} color="inherit" /> : <CompareArrowsIcon />}
                    >
                        {compareLoading ? 'Comparing...' : 'Run Comparison'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={toast.open}
                autoHideDuration={2500}
                onClose={() => setToast((prev) => ({ ...prev, open: false }))}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                <Alert
                    onClose={() => setToast((prev) => ({ ...prev, open: false }))}
                    severity={toast.severity}
                    variant="filled"
                    sx={{ width: '100%' }}
                >
                    {toast.message}
                </Alert>
            </Snackbar>
        </Box>
    );
};

export default Cards;
