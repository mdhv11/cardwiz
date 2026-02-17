import React, { useState } from 'react';
import { Box, Typography } from '@mui/material';
import SmartAdvisor from '../components/SmartAdvisor';
// import { useDispatch, useSelector } from 'react-redux';

const Advisor = () => {
    // const dispatch = useDispatch();
    // const { isAnalyzing } = useSelector((state) => state.cards);

    // Local state for demo purposes until fully wired with Redux/API
    const [messages, setMessages] = useState([
        { sender: 'bot', text: 'Hello! I am CardWiz. Ask me where to use your cards or upload a statement.' }
    ]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);

    const handleSendMessage = (text) => {
        setMessages(prev => [...prev, { sender: 'user', text }]);

        // Simulate AI response
        setIsAnalyzing(true);
        setTimeout(() => {
            setIsAnalyzing(false);
            setMessages(prev => [...prev, { sender: 'bot', text: `That's a great question about "${text}". Based on your HDFC Millennia card, you should get 5% cashback on Amazon.` }]);
        }, 2000);
    };

    return (
        <Box sx={{ height: 'calc(100vh - 100px)' }}> {/* Adjust for layout padding */}
            <SmartAdvisor
                messages={messages}
                onSendMessage={handleSendMessage}
                isAnalyzing={isAnalyzing}
            />
        </Box>
    );
};

export default Advisor;
