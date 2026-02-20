import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import axiosClient, { getApiErrorMessage } from '../../api/axiosClient';

// Thunks
export const fetchCards = createAsyncThunk(
    'cards/fetchCards',
    async (_, { rejectWithValue }) => {
        try {
            const response = await axiosClient.get('/cards');
            return response.data;
        } catch (error) {
            return rejectWithValue(getApiErrorMessage(error, 'Failed to load cards.'));
        }
    }
);

export const addCard = createAsyncThunk(
    'cards/addCard',
    async (cardData, { rejectWithValue }) => {
        try {
            const response = await axiosClient.post('/cards', cardData);
            return response.data;
        } catch (error) {
            return rejectWithValue(getApiErrorMessage(error, 'Failed to add card.'));
        }
    }
);

export const getRecommendation = createAsyncThunk(
    'cards/getRecommendation',
    async (transactionContext, { rejectWithValue }) => {
        try {
            // transactionContext: { merchantName, category, transactionAmount }
            const response = await axiosClient.post('/cards/recommendations', transactionContext);
            return response.data; // { bestOption: {}, alternatives: [], semanticContext: "" }
        } catch (error) {
            return rejectWithValue(getApiErrorMessage(error, 'Recommendation failed.'));
        }
    }
);

const initialState = {
    items: [],
    recommendation: null, // Stores the latest recommendation result
    loading: false,
    error: null,
    analyzingDocument: false,
};

const cardSlice = createSlice({
    name: 'cards',
    initialState,
    reducers: {
        clearRecommendation: (state) => {
            state.recommendation = null;
        }
    },
    extraReducers: (builder) => {
        builder
            // Fetch Cards
            .addCase(fetchCards.pending, (state) => {
                state.loading = true;
            })
            .addCase(fetchCards.fulfilled, (state, action) => {
                state.loading = false;
                state.items = action.payload;
            })
            .addCase(fetchCards.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload;
            })
            // Add Card
            .addCase(addCard.fulfilled, (state, action) => {
                state.items.push(action.payload);
            })
            // Get Recommendation
            .addCase(getRecommendation.pending, (state) => {
                state.analyzingDocument = true; // Using this flag for "Consulting financial models..." state
                state.recommendation = null;
            })
            .addCase(getRecommendation.fulfilled, (state, action) => {
                state.analyzingDocument = false;
                state.recommendation = action.payload;
            })
            .addCase(getRecommendation.rejected, (state, action) => {
                state.analyzingDocument = false;
                state.error = action.payload;
            });
    },
});

export const { clearRecommendation } = cardSlice.actions;

export default cardSlice.reducer;
