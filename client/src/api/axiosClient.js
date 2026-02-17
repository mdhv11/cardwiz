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
        return Promise.reject(error);
    }
);

export default axiosClient;
