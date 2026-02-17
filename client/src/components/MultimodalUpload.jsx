import React, { useCallback } from 'react';
import { Box, Typography, Button, Paper } from '@mui/material';
import { CloudUpload as CloudUploadIcon, InsertDriveFile as FileIcon } from '@mui/icons-material';
import { useDropzone } from 'react-dropzone';

const MultimodalUpload = ({ onUpload, isUploading }) => {
    const onDrop = useCallback((acceptedFiles) => {
        if (acceptedFiles?.length > 0) {
            onUpload(acceptedFiles[0]);
        }
    }, [onUpload]);

    const { getRootProps, getInputProps, isDragActive, acceptedFiles } = useDropzone({
        onDrop,
        accept: {
            'image/*': ['.jpeg', '.png', '.jpg'],
            'application/pdf': ['.pdf']
        },
        maxFiles: 1
    });

    return (
        <Paper
            {...getRootProps()}
            sx={{
                p: 4,
                border: '2px dashed',
                borderColor: isDragActive ? 'secondary.main' : 'rgba(255, 255, 255, 0.2)',
                borderRadius: 4,
                bgcolor: isDragActive ? 'rgba(0, 200, 83, 0.05)' : 'rgba(255, 255, 255, 0.02)',
                cursor: 'pointer',
                textAlign: 'center',
                transition: 'all 0.2s ease-in-out',
                '&:hover': {
                    borderColor: 'secondary.main',
                    bgcolor: 'rgba(255, 255, 255, 0.05)'
                },
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                gap: 2
            }}
        >
            <input {...getInputProps()} />

            <Box
                sx={{
                    p: 2,
                    borderRadius: '50%',
                    bgcolor: 'rgba(255, 255, 255, 0.05)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                }}
            >
                <CloudUploadIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
            </Box>

            <Box>
                <Typography variant="h6" gutterBottom>
                    {isDragActive ? "Drop it here!" : "Upload Statement or Brochure"}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    Drag & drop or click to select
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    Supports PDF, JPG, PNG
                </Typography>
            </Box>

            {acceptedFiles.length > 0 && (
                <Box sx={{ mt: 2, display: 'flex', alignItems: 'center', gap: 1, color: 'secondary.main' }}>
                    <FileIcon fontSize="small" />
                    <Typography variant="body2">{acceptedFiles[0].name}</Typography>
                </Box>
            )}

            <Button variant="outlined" color="secondary" size="small" sx={{ mt: 1 }}>
                Select File
            </Button>
        </Paper>
    );
};

export default MultimodalUpload;
