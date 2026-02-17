import React, { useState, useRef, useEffect } from 'react';
import {
    Box,
    Paper,
    Typography,
    TextField,
    IconButton,
    List,
    ListItem,
    Avatar,
    CircularProgress,
    Tooltip
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
    messages,
    isAnalyzing,
    isUploading = false,
    isClearingHistory = false
}) => {
    const [input, setInput] = useState('');
    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);

    const handleSend = () => {
        if (input.trim()) {
            onSendMessage(input);
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
                                <Typography variant="body2">{msg.text}</Typography>
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
