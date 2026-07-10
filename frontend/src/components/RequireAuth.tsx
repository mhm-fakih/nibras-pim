import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Permission, Role } from '../api/client';
import { useAuth } from '../hooks/useAuth';

/**
 * Gate for authenticated routes. Redirects to /login when signed out; when {@code roles} and/or
 * {@code permission} are given, shows a "not authorised" notice if the user lacks them.
 */
export function RequireAuth({
  children,
  roles,
  permission,
}: {
  children: ReactNode;
  roles?: Role[];
  permission?: Permission;
}) {
  const { user, loading, can } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <Box sx={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if ((roles && !roles.includes(user.role)) || (permission && !can(permission))) {
    return (
      <Box sx={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <Typography color="text.secondary">
          You don’t have permission to view this page.
        </Typography>
      </Box>
    );
  }

  return <>{children}</>;
}
