import React, { useState, useRef, useEffect } from 'react';
import {
    Box,
    Paper,
    Typography,
    TextField,
    IconButton,
    List,
    ListItem,
    ListItemText,
    Avatar,
    CircularProgress
} from '@mui/material';
import { Send as SendIcon, SmartToy as BotIcon, Person as PersonIcon } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

const SmartAdvisor = ({ onSendMessage, messages, isAnalyzing }) => {
    const [input, setInput] = useState('');
    const messagesEndRef = useRef(null);
    const theme = useTheme();

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

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isAnalyzing]);

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
                <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <BotIcon color="secondary" /> Smart Advisor
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    Powered by Amazon Nova 2 Pro
                </Typography>
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
                <div ref={messagesEndRef} />
            </List>

            <Box sx={{ p: 2, borderTop: '1px solid rgba(255, 255, 255, 0.1)', bgcolor: 'rgba(0, 0, 0, 0.2)' }}>
                <Box sx={{ display: 'flex', gap: 1 }}>
                    <TextField
                        fullWidth
                        placeholder="Where should I use my card?"
                        variant="outlined"
                        size="small"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyPress={handleKeyPress}
                        disabled={isAnalyzing}
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
                        disabled={!input.trim() || isAnalyzing}
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
