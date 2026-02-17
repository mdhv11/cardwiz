import React from 'react';
import { Card, CardContent, Typography, Box, Chip } from '@mui/material';
import { motion } from 'framer-motion';

const RewardCard = ({ card, isRecommendation = false, reasoning, estimatedReward, onClick }) => {
    // card: { cardName, issuer, network, lastFourDigits, ... }

    return (
        <motion.div
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
        >
            <Card
                onClick={onClick}
                sx={{
                    cursor: onClick ? 'pointer' : 'default',
                    background: 'linear-gradient(135deg, #132F4C 0%, #0A1929 100%)',
                    border: isRecommendation ? '2px solid #00C853' : '1px solid rgba(255, 255, 255, 0.1)',
                    position: 'relative',
                    overflow: 'hidden',
                    minHeight: 180,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                }}
            >
                {/* Abstract Background Shapes */}
                <Box
                    sx={{
                        position: 'absolute',
                        top: -50,
                        right: -50,
                        width: 150,
                        height: 150,
                        borderRadius: '50%',
                        background: 'rgba(255, 255, 255, 0.03)',
                        zIndex: 0,
                    }}
                />

                <CardContent sx={{ zIndex: 1, height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                        <Typography variant="h6" component="div" sx={{ fontWeight: 700 }}>
                            {card.cardName}
                        </Typography>
                        <Chip
                            label={card.network}
                            size="small"
                            sx={{
                                backgroundColor: 'rgba(255,255,255,0.1)',
                                color: 'text.secondary',
                                fontSize: '0.7rem'
                            }}
                        />
                    </Box>

                    <Typography color="text.secondary" sx={{ mb: 1, fontSize: '0.9rem' }}>
                        {card.issuer}
                    </Typography>

                    <Box sx={{ flexGrow: 1 }} />

                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', letterSpacing: 2 }}>
                            **** **** **** {card.lastFourDigits}
                        </Typography>
                    </Box>

                    {isRecommendation && (
                        <Box sx={{ mt: 2, pt: 2, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
                            <Typography variant="subtitle2" color="secondary.main" sx={{ fontWeight: 700 }}>
                                {estimatedReward}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                                {reasoning}
                            </Typography>
                        </Box>
                    )}
                </CardContent>
            </Card>
        </motion.div>
    );
};

export default RewardCard;
