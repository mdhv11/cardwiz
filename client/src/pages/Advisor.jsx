import React, { useEffect, useState } from 'react';
import { Alert, Box, Snackbar } from '@mui/material';
import SmartAdvisor from '../components/SmartAdvisor';
import axiosClient, { getApiErrorMessage } from '../api/axiosClient';

const Advisor = () => {
    const [messages, setMessages] = useState([]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [isHistoryLoading, setIsHistoryLoading] = useState(true);
    const [isClearingHistory, setIsClearingHistory] = useState(false);
    const [toast, setToast] = useState({ open: false, severity: 'success', message: '' });
    const [selectedCurrency, setSelectedCurrency] = useState('INR');
    const [statementCards, setStatementCards] = useState([]);
    const [selectedStatementCardId, setSelectedStatementCardId] = useState('');
    const [recentValidations, setRecentValidations] = useState([]);
    const [pendingClarification, setPendingClarification] = useState(null);

    const allowedExtensions = ['pdf', 'jpg', 'jpeg', 'png', 'webp'];
    const maxUploadBytes = 20 * 1024 * 1024;
    const supportedCurrencies = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD'];
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

    const pushBotStructuredMessage = async (type, payload, fallbackText) => {
        setMessages((prev) => [...prev, { sender: 'bot', type, payload, text: fallbackText }]);
        if (!fallbackText) {
            return;
        }
        try {
            await axiosClient.post('/advisor/history', {
                sender: 'bot',
                text: fallbackText,
                type,
                payload
            });
        } catch (_) {
            // Keep chat responsive even if history persistence fails.
        }
    };

    const extractErrorMessage = (error, fallback) => {
        let message = getApiErrorMessage(error, fallback);

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

    const parseRecommendationInput = (text, currency) => {
        const amountMatch = text.match(/(?:rs\.?|inr|₹|\$)?\s*([0-9]+(?:\.[0-9]{1,2})?)/i);
        const lower = text.toLowerCase();
        let category = 'general';
        if (lower.includes('fuel') || lower.includes('gas') || lower.includes('petrol')) category = 'fuel';
        if (lower.includes('grocery') || lower.includes('supermarket')) category = 'grocery';
        if (lower.includes('travel') || lower.includes('flight') || lower.includes('hotel')) category = 'travel';
        if (lower.includes('dining') || lower.includes('restaurant') || lower.includes('food')) category = 'dining';
        if (lower.includes('online') || lower.includes('amazon') || lower.includes('flipkart')) category = 'online';

        const contextNotes = recentValidations
            .slice(0, 5)
            .map((tx) => `${tx.merchant || 'merchant'}:${tx.category || 'general'}:${tx.currency || currency}:${tx.amount ?? 0}`)
            .join(' ; ');

        return {
            merchantName: text,
            category,
            transactionAmount: amountMatch ? Number(amountMatch[1]) : 0,
            currency,
            contextNotes
        };
    };

    const getMissingContext = (text) => {
        const lower = text.trim().toLowerCase();
        const hasMerchantHint =
            /(amazon|uber|starbucks|swiggy|zomato|walmart|target|costco|myntra|flipkart|restaurant|fuel|grocery|hotel|flight|dining|food|shopping)/i.test(text);
        const hasAmountHint = /(?:rs\.?|inr|₹|\$)?\s*[0-9]+(?:\.[0-9]{1,2})?/.test(lower);
        return { hasMerchantHint, hasAmountHint };
    };

    const mergeClarificationText = (initialPrompt, clarificationReply) => {
        const merged = `${initialPrompt} ${clarificationReply}`.trim();
        // If user only enters number, convert into spend context phrase.
        if (/^\s*[0-9]+(?:\.[0-9]{1,2})?\s*$/.test(clarificationReply)) {
            return `${initialPrompt} spend ${clarificationReply}`;
        }
        return merged;
    };

    const buildRecommendationMessage = (payload) => {
        const richBest = payload?.best_card;
        if (richBest?.name && richBest?.rewards) {
            const tx = payload?.transaction_context || {};
            const spendAmount = tx.spend_amount ?? 0;
            const currency = tx.currency || richBest.rewards.value_unit || selectedCurrency;
            const rewardValue = Number(richBest.rewards.estimated_value ?? 0);
            const effectivePct = Number(richBest.rewards.effective_percentage ?? 0);
            const reasoning = Array.isArray(richBest.reasoning) ? richBest.reasoning.filter(Boolean) : [];
            const warning = richBest.warning ? `Warning: ${richBest.warning}` : '';

            const comparisonRows = Array.isArray(payload?.comparison_table) ? payload.comparison_table : [];
            const summaryLines = [
                `Best card: ${richBest.name}`,
                `Estimated rewards: ${currency} ${rewardValue.toFixed(2)} on ${currency} ${Number(spendAmount).toLocaleString()} spend (${effectivePct.toFixed(2)}%)`
            ];
            if (reasoning.length > 0) {
                summaryLines.push(`Why this card: ${reasoning.join(' | ')}`);
            }
            if (warning) {
                summaryLines.push(warning);
            }

            return {
                type: 'recommendation-result',
                payload: {
                    currency,
                    bestCardName: richBest.name,
                    spendAmount: Number(spendAmount),
                    estimatedReward: rewardValue,
                    effectivePercentage: effectivePct,
                    reasoning,
                    warning: richBest.warning || null,
                    comparisonTable: comparisonRows
                },
                fallbackText: summaryLines.join('. ') + '.'
            };
        }

        const recommendation = payload?.bestOption;
        if (!recommendation) {
            return {
                type: null,
                payload: null,
                fallbackText: 'I could not find a recommendation right now. Please try again.'
            };
        }
        const reward = recommendation.estimatedReward || 'No reward details available';
        const reason = recommendation.reasoning || 'No reasoning available';
        return {
            type: null,
            payload: null,
            fallbackText: `Best card: ${recommendation.cardName}. Reward: ${reward}. Why: ${reason}`
        };
    };

    const formatMissedSavingsSummary = (payload) => {
        const summary = payload?.summary || {};
        const currency = summary.currency || selectedCurrency;
        const txCount = Number(summary.transactions_analyzed ?? 0);
        const spend = Number(summary.total_spend ?? 0);
        const actual = Number(summary.total_actual_rewards ?? 0);
        const optimal = Number(summary.total_optimal_rewards ?? 0);
        const missed = Number(summary.total_missed_savings ?? 0);

        return [
            `Statement analysis complete for ${txCount} transactions.`,
            `Total spend: ${currency} ${spend.toFixed(2)}.`,
            `Actual rewards: ${currency} ${actual.toFixed(2)}.`,
            `Optimal rewards: ${currency} ${optimal.toFixed(2)}.`,
            `Missed savings: ${currency} ${missed.toFixed(2)}.`
        ].join(' ');
    };

    const handleSendMessage = async (text, options = {}) => {
        await appendMessage('user', text);
        const lower = text.trim().toLowerCase();
        const looksGeneric =
            lower.length < 12 ||
            lower.includes('which card') ||
            lower.includes('best card') ||
            lower.includes('should i get this card') ||
            lower.includes('what should i use');

        const { hasMerchantHint, hasAmountHint } = getMissingContext(text);

        if (pendingClarification) {
            const mergedText = mergeClarificationText(pendingClarification.initialPrompt, text);
            const mergedContext = getMissingContext(mergedText);
            if (!mergedContext.hasMerchantHint || !mergedContext.hasAmountHint) {
                await pushBotMessage(
                    `Almost there. Please share both where you are spending and approx amount (${options.currency || selectedCurrency}).`
                );
                return;
            }
            setPendingClarification(null);
            setIsAnalyzing(true);
            try {
                const payload = parseRecommendationInput(mergedText, options.currency || selectedCurrency);
                const response = await axiosClient.post('/cards/recommendations', payload);
                const message = buildRecommendationMessage(response.data);
                if (message.type) {
                    await pushBotStructuredMessage(message.type, message.payload, message.fallbackText);
                } else {
                    await pushBotMessage(message.fallbackText);
                }
            } catch (error) {
                await pushBotMessage(extractErrorMessage(error, 'Recommendation failed. Please try again in a moment.'));
            } finally {
                setIsAnalyzing(false);
            }
            return;
        }

        if (looksGeneric && (!hasMerchantHint || !hasAmountHint)) {
            setPendingClarification({ initialPrompt: text });
            await pushBotMessage(
                `I can help, but I need one detail first: where are you spending and roughly how much (${options.currency || selectedCurrency})?`
            );
            return;
        }

        setIsAnalyzing(true);
        try {
            const payload = parseRecommendationInput(text, options.currency || selectedCurrency);
            const response = await axiosClient.post('/cards/recommendations', payload);
            const message = buildRecommendationMessage(response.data);
            if (message.type) {
                await pushBotStructuredMessage(message.type, message.payload, message.fallbackText);
            } else {
                await pushBotMessage(message.fallbackText);
            }
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

        setIsUploading(true);
        try {
            if (extension === 'pdf') {
                if (!selectedStatementCardId) {
                    await pushBotMessage('Select the card you used for this statement before uploading the PDF.');
                    return;
                }

                const contextNotes = recentValidations
                    .slice(0, 5)
                    .map((tx) => `${tx.merchant || 'merchant'}:${tx.category || 'general'}:${tx.currency || selectedCurrency}:${tx.amount ?? 0}`)
                    .join(' ; ');

                const formData = new FormData();
                formData.append('file', file);
                formData.append('actualCardId', String(selectedStatementCardId));
                formData.append('currency', selectedCurrency);
                formData.append('contextNotes', contextNotes);
                formData.append('limitTransactions', '30');

                const response = await axiosClient.post('/cards/statement-missed-savings', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                const summaryText = formatMissedSavingsSummary(response.data);
                await pushBotStructuredMessage('missed-savings-report', response.data, summaryText);
            } else {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('documentType', 'STATEMENT');

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
                    setMessages(history.map((entry) => ({
                        sender: entry.sender,
                        text: entry.text,
                        type: entry.type || undefined,
                        payload: entry.payload || undefined
                    })));
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

    useEffect(() => {
        const loadRecentValidations = async () => {
            try {
                const response = await axiosClient.get('/transactions');
                const items = Array.isArray(response.data) ? response.data : [];
                setRecentValidations(items);
            } catch (_) {
                setRecentValidations([]);
            }
        };
        loadRecentValidations();
    }, []);

    useEffect(() => {
        const loadCards = async () => {
            try {
                const response = await axiosClient.get('/cards');
                const cards = (Array.isArray(response.data) ? response.data : []).filter((card) => card?.active);
                setStatementCards(cards);
                if (cards.length > 0) {
                    setSelectedStatementCardId((prev) => prev || String(cards[0].id));
                }
            } catch (_) {
                setStatementCards([]);
                setSelectedStatementCardId('');
            }
        };
        loadCards();
    }, []);

    return (
        <Box sx={{ height: 'calc(100vh - 100px)' }}> {/* Adjust for layout padding */}
            <SmartAdvisor
                messages={messages}
                onSendMessage={handleSendMessage}
                onUploadDocument={handleUploadDocument}
                onClearHistory={handleClearHistory}
                currencies={supportedCurrencies}
                selectedCurrency={selectedCurrency}
                onCurrencyChange={setSelectedCurrency}
                statementCards={statementCards}
                selectedStatementCardId={selectedStatementCardId}
                onStatementCardChange={setSelectedStatementCardId}
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
