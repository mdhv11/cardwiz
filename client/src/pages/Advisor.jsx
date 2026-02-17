import React, { useEffect, useState } from 'react';
import { Alert, Box, Snackbar } from '@mui/material';
import SmartAdvisor from '../components/SmartAdvisor';
import axiosClient from '../api/axiosClient';

const Advisor = () => {
    const [messages, setMessages] = useState([]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [isHistoryLoading, setIsHistoryLoading] = useState(true);
    const [isClearingHistory, setIsClearingHistory] = useState(false);
    const [toast, setToast] = useState({ open: false, severity: 'success', message: '' });

    const allowedExtensions = ['pdf', 'jpg', 'jpeg', 'png', 'webp'];
    const maxUploadBytes = 20 * 1024 * 1024;
    const defaultWelcomeMessage = 'Hello! I am CardWiz. Ask me where to use your cards or upload a statement.';

    const appendMessage = async (sender, text, persist = true) => {
        setMessages((prev) => [...prev, { sender, text }]);
        if (!persist) {
            return;
        }
        try {
            await axiosClient.post('/advisor/history', { sender, text });
        } catch (_) {
            // Keep chat responsive even if history persistence fails.
        }
    };

    const pushBotMessage = async (text, persist = true) => {
        await appendMessage('bot', text, persist);
    };

    const extractErrorMessage = (error, fallback) => {
        let message =
            error?.response?.data?.message ||
            error?.response?.data?.detail ||
            error?.message ||
            '';

        // Unwrap cases like: "{\"detail\":\"...\"}" or "{\"message\":\"...\"}"
        for (let i = 0; i < 3 && typeof message === 'string'; i += 1) {
            const trimmed = message.trim();
            if (!trimmed) {
                break;
            }
            try {
                const parsed = JSON.parse(trimmed);
                if (typeof parsed === 'string') {
                    message = parsed;
                    continue;
                }
                if (parsed && typeof parsed === 'object') {
                    message = parsed.detail || parsed.message || message;
                    continue;
                }
            } catch (_) {
                break;
            }
        }

        if (typeof message === 'string' && message.trim()) {
            const normalized = message.toLowerCase();
            if (normalized.includes('aspect ratio') && normalized.includes('20:1')) {
                return 'This image is too narrow/wide for AI parsing. Please upload a clearer PDF or a normal screenshot/photo.';
            }
            if (normalized.includes('required key [messages] not found')) {
                return 'Recommendation service is temporarily misconfigured. Please retry in a moment.';
            }
            return message;
        }
        return fallback;
    };

    const parseRecommendationInput = (text) => {
        const amountMatch = text.match(/(?:rs\.?|inr|₹|\$)\s*([0-9]+(?:\.[0-9]{1,2})?)/i);
        return {
            merchantName: text,
            category: 'general',
            transactionAmount: amountMatch ? Number(amountMatch[1]) : 0
        };
    };

    const handleSendMessage = async (text) => {
        await appendMessage('user', text);
        setIsAnalyzing(true);
        try {
            const payload = parseRecommendationInput(text);
            const response = await axiosClient.post('/cards/recommendations', payload);
            const recommendation = response.data?.bestOption;

            if (!recommendation) {
                await pushBotMessage('I could not find a recommendation right now. Please try again.');
                return;
            }

            const reward = recommendation.estimatedReward || 'No reward details available';
            const reason = recommendation.reasoning || 'No reasoning available';
            await pushBotMessage(`Best card: ${recommendation.cardName}. Reward: ${reward}. Why: ${reason}`);
        } catch (error) {
            await pushBotMessage(extractErrorMessage(error, 'Recommendation failed. Please try again in a moment.'));
        } finally {
            setIsAnalyzing(false);
        }
    };

    const handleUploadDocument = async (file) => {
        const fileName = file?.name || 'document';
        await appendMessage('user', `Uploaded: ${fileName}`);

        if (!file) {
            await pushBotMessage('No file selected.');
            return;
        }

        const extension = fileName.split('.').pop()?.toLowerCase();
        if (!extension || !allowedExtensions.includes(extension)) {
            await pushBotMessage('Unsupported file format. Use PDF, JPG, JPEG, PNG, or WEBP.');
            return;
        }

        if (file.size > maxUploadBytes) {
            await pushBotMessage('File is too large. Max allowed size is 20 MB.');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('documentType', 'STATEMENT');

        setIsUploading(true);
        try {
            const response = await axiosClient.post('/cards/documents/analyze', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            const analysis = response.data?.analysis;
            const summary = analysis?.aiSummary || response.data?.aiSummary;
            const extractedRules = Array.isArray(analysis?.extractedRules) ? analysis.extractedRules : [];

            if (summary) {
                await pushBotMessage(`Document analyzed. ${summary}`);
            } else {
                await pushBotMessage('Document uploaded and analyzed successfully.');
            }

            if (extractedRules.length > 0) {
                const preview = extractedRules
                    .slice(0, 3)
                    .map((rule) => `${rule.cardName}: ${rule.rewardRate} ${rule.rewardType} on ${rule.category}`)
                    .join(' | ');
                await pushBotMessage(`Top extracted rules: ${preview}`);
            }
        } catch (error) {
            await pushBotMessage(extractErrorMessage(error, 'Document upload or analysis failed. Please try again.'));
        } finally {
            setIsUploading(false);
        }
    };

    const handleClearHistory = async () => {
        if (isAnalyzing || isUploading || isHistoryLoading || isClearingHistory) {
            return;
        }
        const confirmed = window.confirm('Clear advisor chat history?');
        if (!confirmed) {
            return;
        }

        setIsClearingHistory(true);
        try {
            await axiosClient.delete('/advisor/history');
            setMessages([{ sender: 'bot', text: defaultWelcomeMessage }]);
            setToast({ open: true, severity: 'success', message: 'Chat history cleared.' });
            try {
                await axiosClient.post('/advisor/history', { sender: 'bot', text: defaultWelcomeMessage });
            } catch (_) {
                // Keep UI responsive even if seeding the welcome message fails.
            }
        } catch (_) {
            await pushBotMessage('Could not clear chat history right now. Please try again.');
            setToast({ open: true, severity: 'error', message: 'Failed to clear chat history.' });
        } finally {
            setIsClearingHistory(false);
        }
    };

    useEffect(() => {
        const loadHistory = async () => {
            setIsHistoryLoading(true);
            try {
                const response = await axiosClient.get('/advisor/history');
                const history = Array.isArray(response.data) ? response.data : [];
                if (history.length > 0) {
                    setMessages(history.map((entry) => ({ sender: entry.sender, text: entry.text })));
                } else {
                    setMessages([{ sender: 'bot', text: defaultWelcomeMessage }]);
                    try {
                        await axiosClient.post('/advisor/history', { sender: 'bot', text: defaultWelcomeMessage });
                    } catch (_) {
                        // Keep chat usable even if welcome-message persistence fails.
                    }
                }
            } catch (_) {
                setMessages([{ sender: 'bot', text: defaultWelcomeMessage }]);
            } finally {
                setIsHistoryLoading(false);
            }
        };

        loadHistory();
    }, []);

    return (
        <Box sx={{ height: 'calc(100vh - 100px)' }}> {/* Adjust for layout padding */}
            <SmartAdvisor
                messages={messages}
                onSendMessage={handleSendMessage}
                onUploadDocument={handleUploadDocument}
                onClearHistory={handleClearHistory}
                isAnalyzing={isAnalyzing || isHistoryLoading}
                isUploading={isUploading}
                isClearingHistory={isClearingHistory}
            />
            <Snackbar
                open={toast.open}
                autoHideDuration={2200}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
                onClose={() => setToast((prev) => ({ ...prev, open: false }))}
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

export default Advisor;
