import React, { useState, useRef, useEffect } from 'react';
import {
    Box,
    Collapse,
    Paper,
    Typography,
    TextField,
    FormControl,
    Select,
    MenuItem,
    IconButton,
    List,
    ListItem,
    Avatar,
    CircularProgress,
    Tooltip,
    Button
} from '@mui/material';
import {
    Send as SendIcon,
    SmartToy as BotIcon,
    Person as PersonIcon,
    AttachFile as AttachFileIcon,
    DeleteOutline as DeleteOutlineIcon
} from '@mui/icons-material';

const SmartAdvisor = ({
    onSendMessage,
    onUploadDocument,
    onClearHistory,
    currencies = [],
    selectedCurrency = 'INR',
    onCurrencyChange,
    statementCards = [],
    selectedStatementCardId = '',
    onStatementCardChange,
    messages,
    isAnalyzing,
    isUploading = false,
    isClearingHistory = false
}) => {
    const [input, setInput] = useState('');
    const [expandedReports, setExpandedReports] = useState({});
    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);

    const toggleReport = (key) => {
        setExpandedReports((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const renderMissedSavingsCard = (payload, expanded, onToggle) => {
        const summary = payload?.summary || {};
        const currency = summary.currency || selectedCurrency;
        const rows = Array.isArray(payload?.transactions) ? payload.transactions : [];
        const topRows = [...rows]
            .sort((a, b) => Number(b?.missed_value ?? 0) - Number(a?.missed_value ?? 0))
            .slice(0, 5);

        return (
            <Box sx={{ minWidth: 320 }}>
                <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
                    Missed Savings Report
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    Transactions: {Number(summary.transactions_analyzed ?? 0)} | Spend: {currency} {Number(summary.total_spend ?? 0).toFixed(2)}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                    Actual: {currency} {Number(summary.total_actual_rewards ?? 0).toFixed(2)} | Optimal: {currency} {Number(summary.total_optimal_rewards ?? 0).toFixed(2)}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.75 }}>
                    Total missed: {currency} {Number(summary.total_missed_savings ?? 0).toFixed(2)}
                </Typography>
                {topRows.length > 0 && (
                    <>
                        <Button size="small" onClick={onToggle} sx={{ px: 0, minWidth: 0, mb: expanded ? 0.5 : 0 }}>
                            {expanded ? 'Hide details' : 'Show top opportunities'}
                        </Button>
                        <Collapse in={expanded} timeout="auto" unmountOnExit>
                            <Box sx={{ borderTop: '1px solid rgba(255, 255, 255, 0.12)', pt: 0.75 }}>
                                {topRows.map((row, idx) => (
                                    <Typography key={`${row?.merchant || 'row'}-${idx}`} variant="caption" sx={{ display: 'block', mb: 0.35 }}>
                                        {row?.merchant || 'Merchant'}: +{currency} {Number(row?.missed_value ?? 0).toFixed(2)} ({row?.actual_card_name || 'Current'} {'->'} {row?.optimal_card_name || 'Optimal'})
                                    </Typography>
                                ))}
                            </Box>
                        </Collapse>
                    </>
                )}
            </Box>
        );
    };

    const renderRecommendationCard = (payload) => {
        const currency = payload?.currency || selectedCurrency;
        const rows = Array.isArray(payload?.comparisonTable) ? payload.comparisonTable : [];
        const topRows = rows.slice(0, 4);
        const reasoning = Array.isArray(payload?.reasoning) ? payload.reasoning.filter(Boolean) : [];

        return (
            <Box sx={{ minWidth: 320, maxWidth: 640 }}>
                <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
                    Recommended Card
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {payload?.bestCardName || 'Best option found'}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    Spend: {currency} {Number(payload?.spendAmount ?? 0).toLocaleString()}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                    Estimated rewards: {currency} {Number(payload?.estimatedReward ?? 0).toFixed(2)} ({Number(payload?.effectivePercentage ?? 0).toFixed(2)}%)
                </Typography>
                {reasoning.length > 0 && (
                    <Typography variant="caption" sx={{ display: 'block', mb: 1 }}>
                        Why this card: {reasoning.join(' | ')}
                    </Typography>
                )}
                {payload?.warning && (
                    <Typography variant="caption" color="warning.main" sx={{ display: 'block', mb: 1 }}>
                        {payload.warning}
                    </Typography>
                )}
                {topRows.length > 0 && (
                    <Box sx={{ borderTop: '1px solid rgba(255, 255, 255, 0.12)', pt: 0.75 }}>
                        <Typography variant="caption" sx={{ fontWeight: 700, display: 'block', mb: 0.5 }}>
                            Card Comparison
                        </Typography>
                        {topRows.map((row, idx) => (
                            <Box
                                key={`${row?.card_name || row?.cardName || 'card'}-${idx}`}
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: '1.6fr 0.8fr 0.8fr',
                                    gap: 1,
                                    py: 0.35,
                                    borderBottom: idx !== topRows.length - 1 ? '1px solid rgba(255,255,255,0.06)' : 'none'
                                }}
                            >
                                <Typography variant="caption">{row?.card_name || row?.cardName || 'Card'}</Typography>
                                <Typography variant="caption" sx={{ textAlign: 'right' }}>
                                    {Number(row?.effective_percentage ?? row?.effectivePercentage ?? 0).toFixed(2)}%
                                </Typography>
                                <Typography variant="caption" sx={{ textAlign: 'right' }}>
                                    {currency} {Number(row?.estimated_value ?? row?.estimatedValue ?? 0).toFixed(2)}
                                </Typography>
                            </Box>
                        ))}
                    </Box>
                )}
            </Box>
        );
    };

    const handleSend = () => {
        if (input.trim()) {
            onSendMessage(input, { currency: selectedCurrency });
            setInput('');
        }
    };

    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleUploadClick = () => {
        if (!isAnalyzing && !isUploading && !isClearingHistory) {
            fileInputRef.current?.click();
        }
    };

    const handleFileChange = async (event) => {
        const selectedFile = event.target.files?.[0];
        if (!selectedFile || !onUploadDocument) {
            return;
        }
        await onUploadDocument(selectedFile);
        event.target.value = '';
    };

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isAnalyzing, isUploading]);

    return (
        <Paper
            elevation={3}
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                borderRadius: 4,
                overflow: 'hidden',
                border: '1px solid rgba(255, 255, 255, 0.1)',
                bgcolor: 'background.paper'
            }}
        >
            <Box sx={{ p: 2, borderBottom: '1px solid rgba(255, 255, 255, 0.1)', bgcolor: 'rgba(0, 0, 0, 0.2)' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                    <Box>
                        <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <BotIcon color="secondary" /> Smart Advisor
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Powered by Amazon Nova 2 Pro
                        </Typography>
                    </Box>
                    <Tooltip title="Clear chat history">
                        <span>
                            <IconButton
                                color="secondary"
                                size="small"
                                onClick={onClearHistory}
                                disabled={isAnalyzing || isUploading || isClearingHistory}
                                sx={{
                                    bgcolor: 'rgba(0, 200, 83, 0.1)',
                                    '&:hover': { bgcolor: 'rgba(0, 200, 83, 0.2)' }
                                }}
                            >
                                <DeleteOutlineIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                </Box>
            </Box>

            <List sx={{ flexGrow: 1, overflow: 'auto', p: 2 }}>
                {messages.map((msg, index) => (
                    <ListItem
                        key={index}
                        sx={{
                            flexDirection: 'column',
                            alignItems: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                            mb: 1
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                flexDirection: msg.sender === 'user' ? 'row-reverse' : 'row',
                                alignItems: 'flex-start',
                                gap: 1,
                                maxWidth: '80%'
                            }}
                        >
                            <Avatar
                                sx={{
                                    width: 32,
                                    height: 32,
                                    bgcolor: msg.sender === 'user' ? 'primary.main' : 'secondary.main'
                                }}
                            >
                                {msg.sender === 'user' ? <PersonIcon fontSize="small" /> : <BotIcon fontSize="small" />}
                            </Avatar>
                            <Paper
                                sx={{
                                    p: 1.5,
                                    borderRadius: 2,
                                    bgcolor: msg.sender === 'user' ? 'primary.light' : 'rgba(255, 255, 255, 0.05)',
                                    color: msg.sender === 'user' ? 'white' : 'text.primary'
                                }}
                            >
                                {msg?.type === 'missed-savings-report'
                                    ? renderMissedSavingsCard(
                                        msg.payload,
                                        !!expandedReports[index],
                                        () => toggleReport(index)
                                    )
                                    : msg?.type === 'recommendation-result'
                                        ? renderRecommendationCard(msg.payload)
                                        : <Typography variant="body2">{msg.text}</Typography>}
                            </Paper>
                        </Box>
                    </ListItem>
                ))}
                {isAnalyzing && (
                    <ListItem sx={{ flexDirection: 'column', alignItems: 'flex-start', mb: 1 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <CircularProgress size={16} color="secondary" />
                            <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                                Consulting financial models...
                            </Typography>
                        </Box>
                    </ListItem>
                )}
                {isUploading && (
                    <ListItem sx={{ flexDirection: 'column', alignItems: 'flex-start', mb: 1 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <CircularProgress size={16} color="secondary" />
                            <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                                Uploading and analyzing your document...
                            </Typography>
                        </Box>
                    </ListItem>
                )}
                <div ref={messagesEndRef} />
            </List>

            <Box sx={{ p: 2, borderTop: '1px solid rgba(255, 255, 255, 0.1)', bgcolor: 'rgba(0, 0, 0, 0.2)' }}>
                <Box sx={{ display: 'flex', gap: 1 }}>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".pdf,.png,.jpg,.jpeg,.webp"
                        onChange={handleFileChange}
                        style={{ display: 'none' }}
                    />
                    <Tooltip title="Upload statement or brochure">
                        <span>
                            <IconButton
                                color="secondary"
                                onClick={handleUploadClick}
                                disabled={isAnalyzing || isUploading || isClearingHistory}
                                sx={{
                                    bgcolor: 'rgba(0, 200, 83, 0.1)',
                                    '&:hover': { bgcolor: 'rgba(0, 200, 83, 0.2)' }
                                }}
                            >
                                <AttachFileIcon />
                            </IconButton>
                        </span>
                    </Tooltip>
                    <TextField
                        fullWidth
                        placeholder="Where should I use my card?"
                        variant="outlined"
                        size="small"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyPress={handleKeyPress}
                        disabled={isAnalyzing || isUploading || isClearingHistory}
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                borderRadius: 4,
                                bgcolor: 'rgba(255, 255, 255, 0.05)'
                            }
                        }}
                    />
                    <FormControl size="small" sx={{ minWidth: 110 }}>
                        <Select
                            value={selectedCurrency}
                            onChange={(e) => onCurrencyChange?.(e.target.value)}
                            disabled={isAnalyzing || isUploading || isClearingHistory}
                            sx={{
                                borderRadius: 4,
                                bgcolor: 'rgba(255, 255, 255, 0.05)'
                            }}
                        >
                            {currencies.map((currency) => (
                                <MenuItem key={currency} value={currency}>{currency}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 170 }}>
                        <Select
                            value={selectedStatementCardId}
                            onChange={(e) => onStatementCardChange?.(e.target.value)}
                            displayEmpty
                            disabled={isAnalyzing || isUploading || isClearingHistory || statementCards.length === 0}
                            sx={{
                                borderRadius: 4,
                                bgcolor: 'rgba(255, 255, 255, 0.05)'
                            }}
                        >
                            {statementCards.length === 0 ? (
                                <MenuItem value="" disabled>No active card</MenuItem>
                            ) : (
                                statementCards.map((card) => (
                                    <MenuItem key={card.id} value={String(card.id)}>
                                        {card.cardName}
                                    </MenuItem>
                                ))
                            )}
                        </Select>
                    </FormControl>
                    <IconButton
                        color="secondary"
                        onClick={handleSend}
                        disabled={!input.trim() || isAnalyzing || isUploading || isClearingHistory}
                        sx={{
                            bgcolor: 'rgba(0, 200, 83, 0.1)',
                            '&:hover': { bgcolor: 'rgba(0, 200, 83, 0.2)' }
                        }}
                    >
                        <SendIcon />
                    </IconButton>
                </Box>
            </Box>
        </Paper>
    );
};

export default SmartAdvisor;
