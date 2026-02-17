import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
    Box,
    CssBaseline,
    AppBar,
    Toolbar,
    Typography,
    IconButton,
    Drawer,
    List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Avatar,
    Menu,
    MenuItem,
    useTheme,
    useMediaQuery
} from '@mui/material';
import {
    Menu as MenuIcon,
    Dashboard as DashboardIcon,
    CreditCard as CardIcon,
    SmartToy as BotIcon,
    Person as PersonIcon,
    Logout as LogoutIcon,
    ChevronLeft as ChevronLeftIcon
} from '@mui/icons-material';
import { logout } from '../store/slices/authSlice';
import { toggleSidebar, setSidebarOpen } from '../store/slices/uiSlice';

const drawerWidth = 240;

const MainLayout = () => {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const location = useLocation();

    const { sidebarOpen } = useSelector((state) => state.ui);
    const { user } = useSelector((state) => state.auth);

    const [anchorEl, setAnchorEl] = useState(null);

    const handleMenu = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const handleLogout = () => {
        dispatch(logout());
        navigate('/login');
    };

    const handleDrawerToggle = () => {
        dispatch(toggleSidebar());
    };

    const menuItems = [
        { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
        { text: 'My Cards', icon: <CardIcon />, path: '/cards' },
        { text: 'Smart Advisor', icon: <BotIcon />, path: '/advisor' },
        { text: 'Profile', icon: <PersonIcon />, path: '/profile' },
    ];

    return (
        <Box sx={{ display: 'flex' }}>
            <CssBaseline />

            {/* App Bar */}
            <AppBar
                position="fixed"
                sx={{
                    zIndex: (theme) => theme.zIndex.drawer + 1,
                    backgroundColor: 'rgba(10, 25, 41, 0.8)', // Glassmorphismish
                    backdropFilter: 'blur(8px)',
                    borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
                    boxShadow: 'none'
                }}
            >
                <Toolbar>
                    <IconButton
                        color="inherit"
                        aria-label="open drawer"
                        edge="start"
                        onClick={handleDrawerToggle}
                        sx={{ marginRight: 2 }}
                    >
                        {sidebarOpen ? <ChevronLeftIcon /> : <MenuIcon />}
                    </IconButton>
                    <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1, fontWeight: 700, letterSpacing: 1 }}>
                        CARDWIZ
                    </Typography>

                    {user && (
                        <div>
                            <IconButton
                                size="large"
                                aria-label="account of current user"
                                aria-controls="menu-appbar"
                                aria-haspopup="true"
                                onClick={handleMenu}
                                color="inherit"
                            >
                                {user.profileImageUrl ? (
                                    <Avatar src={user.profileImageUrl} alt={user.firstName} sx={{ width: 32, height: 32 }} />
                                ) : (
                                    <Avatar sx={{ width: 32, height: 32, bgcolor: theme.palette.secondary.main }}>
                                        {user.firstName ? user.firstName[0] : 'U'}
                                    </Avatar>
                                )}
                            </IconButton>
                            <Menu
                                id="menu-appbar"
                                anchorEl={anchorEl}
                                anchorOrigin={{
                                    vertical: 'bottom',
                                    horizontal: 'right',
                                }}
                                keepMounted
                                transformOrigin={{
                                    vertical: 'top',
                                    horizontal: 'right',
                                }}
                                open={Boolean(anchorEl)}
                                onClose={handleClose}
                            >
                                <MenuItem onClick={() => { handleClose(); navigate('/profile'); }}>Profile</MenuItem>
                                <MenuItem onClick={handleLogout}>
                                    <ListItemIcon>
                                        <LogoutIcon fontSize="small" />
                                    </ListItemIcon>
                                    Logout
                                </MenuItem>
                            </Menu>
                        </div>
                    )}
                </Toolbar>
            </AppBar>

            {/* Sidebar Drawer */}
            <Drawer
                variant={isMobile ? "temporary" : "persistent"}
                open={sidebarOpen}
                onClose={handleDrawerToggle}
                sx={{
                    width: drawerWidth,
                    flexShrink: 0,
                    [`& .MuiDrawer-paper`]: {
                        width: drawerWidth,
                        boxSizing: 'border-box',
                        backgroundColor: theme.palette.background.default, // Match background
                        borderRight: '1px solid rgba(255, 255, 255, 0.08)',
                    },
                }}
            >
                <Toolbar /> {/* Spacer for AppBar */}
                <Box sx={{ overflow: 'auto', mt: 2 }}>
                    <List>
                        {menuItems.map((item) => (
                            <ListItem key={item.text} disablePadding sx={{ display: 'block', mb: 1 }}>
                                <ListItemButton
                                    sx={{
                                        minHeight: 48,
                                        justifyContent: sidebarOpen ? 'initial' : 'center',
                                        px: 2.5,
                                        mx: 1.5,
                                        borderRadius: 2,
                                        backgroundColor: location.pathname === item.path ? 'rgba(0, 200, 83, 0.15)' : 'transparent', // Highlight active
                                        '&:hover': {
                                            backgroundColor: location.pathname === item.path ? 'rgba(0, 200, 83, 0.25)' : 'rgba(255, 255, 255, 0.05)',
                                        }
                                    }}
                                    onClick={() => navigate(item.path)}
                                >
                                    <ListItemIcon
                                        sx={{
                                            minWidth: 0,
                                            mr: sidebarOpen ? 2 : 'auto',
                                            justifyContent: 'center',
                                            color: location.pathname === item.path ? theme.palette.secondary.main : 'inherit'
                                        }}
                                    >
                                        {item.icon}
                                    </ListItemIcon>
                                    <ListItemText primary={item.text} sx={{ opacity: sidebarOpen ? 1 : 0 }} />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                </Box>
            </Drawer>

            {/* Main Content */}
            <Box component="main" sx={{ flexGrow: 1, p: 3, transition: 'margin 0.3s', ml: isMobile ? 0 : (sidebarOpen ? 0 : `-${drawerWidth}px`) }}>
                <Toolbar />
                <Outlet />
            </Box>
        </Box>
    );
};

export default MainLayout;
