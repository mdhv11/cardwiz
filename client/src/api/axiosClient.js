import axios from 'axios';

// Create an Axios instance with default configuration
const axiosClient = axios.create({
    // Base URL for API Gateway (user-service) at localhost:8080
    // Requests starting with /api/v1 are proxied by Vite to http://localhost:8080/api/v1
    baseURL: '/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
});

const parseRetryAfterSeconds = (value) => {
    if (!value) return null;
    const asNumber = Number(value);
    if (!Number.isNaN(asNumber) && Number.isFinite(asNumber) && asNumber >= 0) {
        return Math.round(asNumber);
    }
    const asDate = new Date(value);
    if (Number.isNaN(asDate.getTime())) {
        return null;
    }
    const deltaSeconds = Math.ceil((asDate.getTime() - Date.now()) / 1000);
    return deltaSeconds > 0 ? deltaSeconds : 0;
};

export const getApiErrorMessage = (error, fallback = 'Something went wrong. Please try again.') => {
    const status = error?.response?.status;
    if (status === 429) {
        const retryAfterHeader = error?.response?.headers?.['retry-after'];
        const retryAfterSeconds = parseRetryAfterSeconds(retryAfterHeader);
        if (retryAfterSeconds !== null) {
            return `Too many requests. Please try again in ${retryAfterSeconds} seconds.`;
        }
        return 'Too many requests. Please try again shortly.';
    }
    return (
        error?.response?.data?.message
        || error?.response?.data?.detail
        || error?.message
        || fallback
    );
};

// Request Interceptor: Attach JWT token to every request
axiosClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response Interceptor: Handle global errors (e.g., 401 Unauthorized)
axiosClient.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        const { response } = error;
        if (response && response.status === 401) {
            // Token expired or invalid
            localStorage.removeItem('token');
            // Ideally redirect to login or dispatch a logout action
            // window.location.href = '/login'; // Simple redirect, or use a custom event
        }
        if (response && response.status === 429) {
            error.userMessage = getApiErrorMessage(error, 'Too many requests. Please try again shortly.');
        }
        return Promise.reject(error);
    }
);

export default axiosClient;
